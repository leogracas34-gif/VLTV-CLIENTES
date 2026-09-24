package com.vltv.clientes

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.data.ClienteEntity
import com.vltv.clientes.databinding.ActivityCadastroClienteBinding
import com.vltv.clientes.network.BuscaClienteResultado
import com.vltv.clientes.network.XtreamCheck
import kotlinx.coroutines.launch

class CadastroClienteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroClienteBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private var clienteExistente: ClienteEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroClienteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVoltar.setOnClickListener { finish() }

        val clienteId = intent.getLongExtra(EXTRA_CLIENTE_ID, -1L)
        if (clienteId != -1L) {
            binding.tvTituloTela.text = "Editar Cliente"
            binding.btnSalvar.text = "Salvar Alterações"
            binding.btnExcluir.visibility = android.view.View.VISIBLE
            carregarCliente(clienteId)
        }

        binding.btnSalvar.setOnClickListener { validarEBuscar() }
        binding.btnExcluir.setOnClickListener { confirmarExclusao() }
    }

    private fun carregarCliente(id: Long) {
        lifecycleScope.launch {
            val cliente = database.clienteDao().buscarPorId(id) ?: return@launch
            clienteExistente = cliente
            binding.etNome.setText(cliente.nome)
            binding.etWhatsapp.setText(cliente.whatsapp)
            binding.etUsuario.setText(cliente.usuario)
            binding.etSenha.setText(cliente.senha)
        }
    }

    private fun validarEBuscar() {
        val nome = binding.etNome.text.toString().trim()
        val whatsappBruto = binding.etWhatsapp.text.toString().trim()
        val usuario = binding.etUsuario.text.toString().trim()
        val senha = binding.etSenha.text.toString().trim()

        if (nome.isEmpty()) { binding.etNome.error = "Informe o nome"; return }
        if (whatsappBruto.isEmpty()) { binding.etWhatsapp.error = "Informe o WhatsApp"; return }
        if (usuario.isEmpty()) { binding.etUsuario.error = "Informe o usuário"; return }
        if (senha.isEmpty()) { binding.etSenha.error = "Informe a senha"; return }

        val whatsapp = normalizarWhatsapp(whatsappBruto)

        mostrarBusca(true, "Procurando servidor entre os cadastrados na VPS...")

        lifecycleScope.launch {
            val resultado = XtreamCheck.buscarCliente(this@CadastroClienteActivity, usuario, senha)

            when (resultado) {
                is BuscaClienteResultado.Sucesso -> {
                    val r = resultado.resultado
                    salvarCliente(nome, whatsapp, r.dns, usuario, senha, r.expDateUnix, r.diasRestantes)
                }
                is BuscaClienteResultado.CredenciaisInvalidas -> {
                    mostrarErroBusca("Não encontramos esse usuário/senha em nenhum servidor cadastrado. Confira os dados e tente de novo.")
                }
                is BuscaClienteResultado.Erro -> {
                    mostrarErroBusca(resultado.mensagem)
                }
            }
        }
    }

    private fun salvarCliente(
        nome: String, whatsapp: String, dns: String, usuario: String, senha: String,
        expDateUnix: Long?, diasRestantes: Int?
    ) {
        lifecycleScope.launch {
            val existente = clienteExistente
            if (existente != null) {
                database.clienteDao().atualizar(
                    existente.copy(
                        nome = nome, whatsapp = whatsapp, dns = dns, usuario = usuario, senha = senha,
                        expDateUnix = expDateUnix, diasRestantes = diasRestantes,
                        ultimaChecagemEm = System.currentTimeMillis(), ultimoErro = null
                    )
                )
            } else {
                database.clienteDao().inserir(
                    ClienteEntity(
                        nome = nome, whatsapp = whatsapp, dns = dns, usuario = usuario, senha = senha,
                        expDateUnix = expDateUnix, diasRestantes = diasRestantes,
                        ultimaChecagemEm = System.currentTimeMillis()
                    )
                )
            }
            finish()
        }
    }

    private fun confirmarExclusao() {
        // Confirmação simples e discreta, dentro da própria tela — sem
        // dialog nativo: troca o texto do botão pra "Toque de novo pra
        // confirmar" por alguns segundos.
        if (binding.btnExcluir.text == "Toque novamente para confirmar") {
            excluirCliente()
            return
        }
        binding.btnExcluir.text = "Toque novamente para confirmar"
        binding.btnExcluir.postDelayed({
            binding.btnExcluir.text = "Excluir Cliente"
        }, 3000)
    }

    private fun excluirCliente() {
        val cliente = clienteExistente ?: return
        lifecycleScope.launch {
            database.clienteDao().excluir(cliente)
            finish()
        }
    }

    private fun mostrarBusca(mostrar: Boolean, texto: String) {
        binding.layoutStatusBusca.visibility = if (mostrar) android.view.View.VISIBLE else android.view.View.GONE
        binding.progressBusca.visibility = if (mostrar) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvStatusBusca.text = texto
        binding.btnSalvar.isEnabled = !mostrar
        binding.btnSalvar.alpha = if (mostrar) 0.6f else 1f
    }

    private fun mostrarErroBusca(mensagem: String) {
        mostrarBusca(true, mensagem)
        binding.progressBusca.visibility = android.view.View.GONE
        binding.btnSalvar.isEnabled = true
        binding.btnSalvar.alpha = 1f
    }

    // Aceita número já com ou sem DDI - se vier só com DDD+número (10-11
    // dígitos), assume Brasil (55) automaticamente.
    private fun normalizarWhatsapp(bruto: String): String {
        val digitos = bruto.filter { it.isDigit() }
        return if (digitos.length in 10..11) "55$digitos" else digitos
    }

    companion object {
        const val EXTRA_CLIENTE_ID = "cliente_id"
    }
}
