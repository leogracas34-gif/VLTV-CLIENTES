package com.vltv.clientes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.data.ClienteEntity
import com.vltv.clientes.databinding.ActivityMainBinding
import com.vltv.clientes.network.AppConfig
import com.vltv.clientes.ui.ClienteAdapter
import com.vltv.clientes.worker.VerificacaoVencimentoWorker

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private lateinit var adapter: ClienteAdapter

    private var listaCompleta: List<ClienteEntity> = emptyList()
    private var filtroAtual: Filtro = Filtro.TODOS
    private var sincronizando = false

    private enum class Filtro { TODOS, VENCENDO, VENCIDOS, PENDENTES }

    private val pedirPermissaoNotificacao = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* segue independente do resultado */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        solicitarPermissaoNotificacaoSeNecessario()

        adapter = ClienteAdapter(
            onClick = { cliente -> abrirEdicao(cliente) },
            onMenuClick = { cliente, _ -> abrirEdicao(cliente) },
            onWhatsappClick = { cliente -> abrirWhatsapp(cliente) }
        )
        binding.rvClientes.layoutManager = LinearLayoutManager(this)
        binding.rvClientes.adapter = adapter

        database.clienteDao().observarTodos().observe(this) { lista ->
            listaCompleta = lista
            renderizar()
        }

        binding.fabAdicionar.setOnClickListener {
            startActivity(Intent(this, CadastroClienteActivity::class.java))
        }

        binding.btnConfigMensagens.setOnClickListener {
            startActivity(Intent(this, ConfiguracaoMensagensActivity::class.java))
        }

        binding.btnSincronizar.setOnClickListener { sincronizarAgora() }
        binding.layoutBannerPendente.setOnClickListener { sincronizarAgora() }

        binding.chipTodos.setOnClickListener { selecionarFiltro(Filtro.TODOS) }
        binding.chipVencendo.setOnClickListener { selecionarFiltro(Filtro.VENCENDO) }
        binding.chipVencidos.setOnClickListener { selecionarFiltro(Filtro.VENCIDOS) }
        binding.chipPendentes.setOnClickListener { selecionarFiltro(Filtro.PENDENTES) }
        atualizarEstiloChips()

        binding.swipeRefresh.setOnRefreshListener {
            sincronizarAgora()
            // A lista é observada via LiveData, então ela se atualiza sozinha
            // assim que o worker terminar - aqui só desligamos o spinner
            // depois de um tempo razoável pra não ficar girando pra sempre.
            binding.rvClientes.postDelayed({ binding.swipeRefresh.isRefreshing = false }, 4000)
        }

        // Observa o trabalho de sincronização manual pra mostrar o spinner
        // no botão e avisar quando terminar (ou falhar).
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(VerificacaoVencimentoWorker.WORK_NAME_MANUAL)
            .observe(this) { infos -> observarSincronizacao(infos) }

        // Checagem em segundo plano agendada pra rodar 1x/dia...
        VerificacaoVencimentoWorker.agendar(this)
        // ...e uma checagem extra agora, assim que o app é aberto.
        VerificacaoVencimentoWorker.executarAgora(this)
    }

    private fun abrirEdicao(cliente: ClienteEntity) {
        val intent = Intent(this, CadastroClienteActivity::class.java)
        intent.putExtra(CadastroClienteActivity.EXTRA_CLIENTE_ID, cliente.id)
        startActivity(intent)
    }

    // Abre o WhatsApp já com uma mensagem sugerida (usa o mesmo template
    // configurado em "Configurar Mensagens" pro número de dias restantes).
    private fun abrirWhatsapp(cliente: ClienteEntity) {
        val mensagem = cliente.diasRestantes?.let {
            AppConfig.montarMensagemPara(this, it, cliente.nome)
        } ?: "Olá, ${cliente.nome}! Tudo certo com seu plano?"

        val texto = Uri.encode(mensagem)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${cliente.whatsapp}?text=$texto")))
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir o WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sincronizarAgora() {
        VerificacaoVencimentoWorker.executarManual(this)
    }

    private fun observarSincronizacao(infos: List<WorkInfo>) {
        val emAndamento = infos.any {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
        }

        if (emAndamento && !sincronizando) {
            sincronizando = true
            binding.btnSincronizar.visibility = android.view.View.GONE
            binding.progressSincronizar.visibility = android.view.View.VISIBLE
        } else if (!emAndamento && sincronizando) {
            sincronizando = false
            binding.btnSincronizar.visibility = android.view.View.VISIBLE
            binding.progressSincronizar.visibility = android.view.View.GONE

            val falhou = infos.any { it.state == WorkInfo.State.FAILED }
            Toast.makeText(
                this,
                if (falhou) "Não foi possível sincronizar agora. Tente de novo mais tarde."
                else "Sincronização concluída.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun selecionarFiltro(filtro: Filtro) {
        filtroAtual = filtro
        atualizarEstiloChips()
        renderizar()
    }

    private fun atualizarEstiloChips() {
        val chips = listOf(
            binding.chipTodos to Filtro.TODOS,
            binding.chipVencendo to Filtro.VENCENDO,
            binding.chipVencidos to Filtro.VENCIDOS,
            binding.chipPendentes to Filtro.PENDENTES
        )
        for ((chip, filtro) in chips) {
            val selecionado = filtro == filtroAtual
            chip.setBackgroundResource(if (selecionado) R.drawable.bg_chip_selecionado else R.drawable.bg_chip_normal)
            chip.setTextColor(ContextCompat.getColor(this, if (selecionado) R.color.branco else R.color.cinza_texto))
        }
    }

    private fun renderizar() {
        val filtrada = when (filtroAtual) {
            Filtro.TODOS -> listaCompleta
            Filtro.VENCENDO -> listaCompleta.filter { (it.diasRestantes ?: 99) in 0..3 }
            Filtro.VENCIDOS -> listaCompleta.filter { (it.diasRestantes ?: 0) < 0 }
            Filtro.PENDENTES -> listaCompleta.filter { it.dns.isBlank() }
        }
        adapter.submitList(filtrada)
        binding.layoutVazio.visibility = if (listaCompleta.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        atualizarResumo(listaCompleta)
    }

    private fun atualizarResumo(lista: List<ClienteEntity>) {
        val vencendo = lista.count { (it.diasRestantes ?: 99) in 0..3 }
        val vencidos = lista.count { (it.diasRestantes ?: 0) < 0 }
        val pendentes = lista.count { it.dns.isBlank() }

        binding.tvStatTotal.text = lista.size.toString()
        binding.tvStatAVencer.text = vencendo.toString()
        binding.tvStatVencidos.text = vencidos.toString()
        binding.tvStatPendentes.text = pendentes.toString()

        if (pendentes > 0) {
            binding.layoutBannerPendente.visibility = android.view.View.VISIBLE
            binding.tvBannerPendente.text = if (pendentes == 1)
                "1 cliente aguardando sincronização — toque para sincronizar agora"
            else
                "$pendentes clientes aguardando sincronização — toque para sincronizar agora"
        } else {
            binding.layoutBannerPendente.visibility = android.view.View.GONE
        }
    }

    private fun solicitarPermissaoNotificacaoSeNecessario() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val temPermissao = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!temPermissao) {
                pedirPermissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
