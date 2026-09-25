package com.vltv.clientes

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.data.ClienteEntity
import com.vltv.clientes.databinding.ActivityCadastroClienteBinding
import com.vltv.clientes.worker.SincronizacaoClienteWorker
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

        binding.btnSalvar.setOnClickListener { validarESalvar() }
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

    // Salva o cliente IMEDIATAMENTE no banco local, sem depender de internet
    // ou da VPS estar no ar. A busca do DNS/vencimento acontece depois, em
    // segundo plano (SincronizacaoClienteWorker) - se não conseguir agora,
    // o WorkManager tenta de novo sozinho assim que a rede voltar, e o botão
    // "Sincronizar" da tela principal também pega esse cliente pendente.
    private fun validarESalvar() {
        val nome = binding.etNome.text.toString().trim()
        val whatsappBruto = binding.etWhatsapp.text.toString().trim()
        val usuario = binding.etUsuario.text.toString().trim()
        val senha = binding.etSenha.text.toString().trim()

        if (nome.isEmpty()) { binding.etNome.error = "Informe o nome"; return }
        if (whatsappBruto.isEmpty()) { binding.etWhatsapp.error = "Informe o WhatsApp"; return }
        if (usuario.isEmpty()) { binding.etUsuario.error = "Informe o usuário"; return }
        if (senha.isEmpty()) { binding.etSenha.error = "Informe a senha"; return }

        val whatsapp = normalizarWhatsapp(whatsappBruto)
        val existente = clienteExistente
        val credenciaisMudaram = existente?.let { it.usuario != usuario || it.senha != senha } ?: true

        binding.btnSalvar.isEnabled = false

        lifecycleScope.launch {
            val idSalvo: Long

            if (existente != null) {
                val atualizado = existente.copy(
                    nome = nome,
                    whatsapp = whatsapp,
                    usuario = usuario,
                    senha = senha,
                    // Se o login/senha mudou, esquece o status antigo - vai
                    // reconsultar do zero. Se não mudou, mantém o DNS/dias
                    // já conhecidos até a próxima sincronização confirmar.
                    dns = if (credenciaisMudaram) "" else existente.dns,
                    diasRestantes = if (credenciaisMudaram) null else existente.diasRestantes,
                    expDateUnix = if (credenciaisMudaram) null else existente.expDateUnix,
                    ultimoErro = null
                )
                database.clienteDao().atualizar(atualizado)
                idSalvo = atualizado.id
            } else {
                idSalvo = database.clienteDao().inserir(
                    ClienteEntity(
                        nome = nome, whatsapp = whatsapp, usuario = usuario, senha = senha
                    )
                )
            }

            SincronizacaoClienteWorker.sincronizar(this@CadastroClienteActivity, idSalvo)

            Toast.makeText(
                this@CadastroClienteActivity,
                "Cliente salvo. Sincronizando em segundo plano...",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private fun confirmarExclusao() {
        // Confirmação simples e discreta, dentro da própria tela - sem
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
