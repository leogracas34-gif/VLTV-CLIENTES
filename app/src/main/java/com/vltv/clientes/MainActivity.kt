package com.vltv.clientes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.data.ClienteEntity
import com.vltv.clientes.databinding.ActivityMainBinding
import com.vltv.clientes.network.AppConfig
import com.vltv.clientes.ui.ClienteAdapter
import com.vltv.clientes.worker.VerificacaoVencimentoWorker
import kotlinx.coroutines.launch

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
            onClick = { cliente -> abrirDetalhe(cliente) },
            onMenuClick = { cliente, view -> mostrarMenuCliente(cliente, view) },
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
        binding.btnEnviarAvisosAgora.setOnClickListener { enviarAvisosAgora() }

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

        // Observa o trabalho do botão "Enviar avisos agora" separadamente,
        // só pra avisar quando terminar - não mexe no spinner de sincronizar.
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(VerificacaoVencimentoWorker.WORK_NAME_ENVIO_MANUAL)
            .observe(this) { infos -> observarEnvioManual(infos) }

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

    private fun abrirDetalhe(cliente: ClienteEntity) {
        val intent = Intent(this, DetalheClienteActivity::class.java)
        intent.putExtra(DetalheClienteActivity.EXTRA_CLIENTE_ID, cliente.id)
        startActivity(intent)
    }

    // Menu que abre ao tocar na engrenagem do card: opção rápida de
    // observação/alerta (sem precisar entrar na tela de edição inteira),
    // além de Editar e Excluir.
    private fun mostrarMenuCliente(cliente: ClienteEntity, anchor: android.view.View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_cliente, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.itemObservacao -> { abrirObservacao(cliente); true }
                R.id.itemEditar -> { abrirEdicao(cliente); true }
                R.id.itemExcluir -> { confirmarExclusao(cliente); true }
                else -> false
            }
        }
        popup.show()
    }

    // Caixa rápida pra ver/editar só a observação (ou um alerta) do cliente,
    // sem passar pela tela de edição completa.
    private fun abrirObservacao(cliente: ClienteEntity) {
        val input = EditText(this).apply {
            setText(cliente.observacao ?: "")
            hint = "Ex: prefere pagar via Pix no início do mês"
            setTextColor(ContextCompat.getColor(context, R.color.branco))
            setHintTextColor(ContextCompat.getColor(context, R.color.cinza_hint))
            setPadding(48, 32, 48, 32)
            minLines = 3
            gravity = android.view.Gravity.TOP
        }

        AlertDialog.Builder(this)
            .setTitle("Observação — ${cliente.nome}")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val texto = input.text.toString().trim().ifBlank { null }
                lifecycleScope.launch {
                    database.clienteDao().atualizar(cliente.copy(observacao = texto))
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmarExclusao(cliente: ClienteEntity) {
        AlertDialog.Builder(this)
            .setTitle("Excluir cliente")
            .setMessage("Tem certeza que deseja excluir ${cliente.nome}? Essa ação não pode ser desfeita.")
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch {
                    database.clienteDao().excluir(cliente)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Abre o WhatsApp já com uma mensagem sugerida (usa o mesmo template
    // configurado em "Configurar Mensagens" pro número de dias restantes).
    //
    // ✅ CORRIGIDO: antes usava um link genérico (wa.me), e como o celular
    // tem tanto o WhatsApp normal quanto o WhatsApp Business instalados,
    // o Android decidia sozinho qual abrir (geralmente o normal, por ser
    // o "padrão"). Como o número configurado pro bot e usado pra falar com
    // os clientes é o do Business, agora o intent força especificamente
    // o pacote do WhatsApp Business (com.whatsapp.w4b) primeiro - só cai
    // pro WhatsApp normal se o Business não estiver instalado no aparelho.
    private fun abrirWhatsapp(cliente: ClienteEntity) {
        val mensagem = cliente.diasRestantes?.let {
            AppConfig.montarMensagemPara(this, it, cliente.nome)
        } ?: "Olá, ${cliente.nome}! Tudo certo com seu plano?"

        val texto = Uri.encode(mensagem)
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=${cliente.whatsapp}&text=$texto")

        try {
            // 1ª tentativa: forçar o WhatsApp Business especificamente.
            val intentBusiness = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp.w4b")
            }
            startActivity(intentBusiness)
        } catch (e: Exception) {
            try {
                // 2ª tentativa: WhatsApp Business não está instalado -
                // cai pro WhatsApp comum.
                val intentNormal = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                }
                startActivity(intentNormal)
            } catch (e2: Exception) {
                Toast.makeText(this, "Não foi possível abrir o WhatsApp", Toast.LENGTH_SHORT).show()
            }
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

    private var enviandoAvisos = false

    // Botão "Enviar avisos agora": força o mesmo fluxo do ciclo automático
    // (3/2/1/0 dias ou vencido), pulando a janela 9h-11h. Clientes que já
    // receberam aviso hoje continuam sendo pulados normalmente - não
    // duplica quem o envio automático já mandou com sucesso.
    private fun enviarAvisosAgora() {
        enviandoAvisos = true
        Toast.makeText(this, "Enviando avisos de vencimento...", Toast.LENGTH_SHORT).show()
        VerificacaoVencimentoWorker.executarEnvioAgora(this)
    }

    private fun observarEnvioManual(infos: List<WorkInfo>) {
        if (!enviandoAvisos) return
        val terminou = infos.any {
            it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED
        }
        if (!terminou) return

        enviandoAvisos = false
        val falhou = infos.any { it.state == WorkInfo.State.FAILED }
        Toast.makeText(
            this,
            if (falhou) "Não foi possível concluir o envio. Veja a observação de cada cliente para detalhes."
            else "Avisos enviados (quem já tinha vencimento próximo/vencido e ainda não recebeu aviso hoje).",
            Toast.LENGTH_LONG
        ).show()
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
            // ✅ CORRIGIDO: "Pendente" agora só conta cliente que nunca teve
            // vencimento descoberto (diasRestantes null) - se já sabemos os
            // dias (mesmo com dns zerado por uma resincronização que falhou),
            // ele aparece em "Vencendo"/"Vencidos" normalmente, não aqui.
            Filtro.PENDENTES -> listaCompleta.filter { it.dns.isBlank() && it.diasRestantes == null }
        }
        adapter.submitList(filtrada)
        binding.layoutVazio.visibility = if (listaCompleta.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        atualizarResumo(listaCompleta)
    }

    private fun atualizarResumo(lista: List<ClienteEntity>) {
        val vencendo = lista.count { (it.diasRestantes ?: 99) in 0..3 }
        val vencidos = lista.count { (it.diasRestantes ?: 0) < 0 }
        val pendentes = lista.count { it.dns.isBlank() && it.diasRestantes == null }

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
