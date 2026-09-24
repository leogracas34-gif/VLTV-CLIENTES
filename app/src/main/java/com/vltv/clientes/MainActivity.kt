package com.vltv.clientes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.data.ClienteEntity
import com.vltv.clientes.databinding.ActivityMainBinding
import com.vltv.clientes.ui.ClienteAdapter
import com.vltv.clientes.worker.VerificacaoVencimentoWorker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private lateinit var adapter: ClienteAdapter

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
            onMenuClick = { cliente, _ -> abrirEdicao(cliente) }
        )
        binding.rvClientes.layoutManager = LinearLayoutManager(this)
        binding.rvClientes.adapter = adapter

        database.clienteDao().observarTodos().observe(this) { lista ->
            adapter.submitList(lista)
            binding.layoutVazio.visibility = if (lista.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            atualizarResumo(lista)
        }

        binding.fabAdicionar.setOnClickListener {
            startActivity(Intent(this, CadastroClienteActivity::class.java))
        }

        binding.btnConfigMensagens.setOnClickListener {
            startActivity(Intent(this, ConfiguracaoMensagensActivity::class.java))
        }

        binding.swipeRefresh.setOnRefreshListener {
            VerificacaoVencimentoWorker.executarAgora(this)
            // A lista é observada via LiveData, então ela se atualiza sozinha
            // assim que o worker terminar - aqui só desligamos o spinner
            // depois de um tempo razoável pra não ficar girando pra sempre.
            binding.rvClientes.postDelayed({ binding.swipeRefresh.isRefreshing = false }, 4000)
        }

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

    private fun atualizarResumo(lista: List<ClienteEntity>) {
        val vencendoEm3 = lista.count { (it.diasRestantes ?: 99) in 0..3 }
        val vencidos = lista.count { (it.diasRestantes ?: 0) < 0 }
        binding.tvResumo.text = "${lista.size} clientes  •  $vencendoEm3 vencendo em breve  •  $vencidos vencidos"
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
