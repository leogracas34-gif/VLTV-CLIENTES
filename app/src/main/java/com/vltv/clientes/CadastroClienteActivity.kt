package com.vltv.clientes

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.data.ClienteEntity
import com.vltv.clientes.data.PlanoCliente
import com.vltv.clientes.databinding.ActivityCadastroClienteBinding
import com.vltv.clientes.worker.SincronizacaoClienteWorker
import kotlinx.coroutines.launch
import java.util.Locale

class CadastroClienteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCadastroClienteBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private var clienteExistente: ClienteEntity? = null
    private var planoSelecionado: PlanoCliente = PlanoCliente.MENSAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCadastroClienteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVoltar.setOnClickListener { finish() }

        binding.chipMensal.setOnClickListener { selecionarPlano(PlanoCliente.MENSAL, preencherValorPadrao = true) }
        binding.chipTrimestral.setOnClickListener { selecionarPlano(PlanoCliente.TRIMESTRAL, preencherValorPadrao = true) }
        binding.chipSemestral.setOnClickListener { selecionarPlano(PlanoCliente.SEMESTRAL, preencherValorPadrao = true) }
        binding.chipAnual.setOnClickListener { selecionarPlano(PlanoCliente.ANUAL, preencherValorPadrao = true) }
        selecionarPlano(PlanoCliente.MENSAL, preencherValorPadrao = true)

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

    // Só atualiza o visual dos chips (+ preenche o valor com o padrão do
    // plano, quando vier de um toque do usuário). Ao carregar um cliente já
    // existente, chamamos sem preencherValorPadrao pra não sobrescrever um
    // valor combinado manualmente com o cliente.
    private fun selecionarPlano(plano: PlanoCliente, preencherValorPadrao: Boolean) {
        planoSelecionado = plano

        val chips = listOf(
            binding.chipMensal to PlanoCliente.MENSAL,
            binding.chipTrimestral to PlanoCliente.TRIMESTRAL,
            binding.chipSemestral to PlanoCliente.SEMESTRAL,
            binding.chipAnual to PlanoCliente.ANUAL
        )
        for ((chip, valor) in chips) {
            aplicarEstiloChip(chip, selecionado = valor == plano)
        }

        if (preencherValorPadrao) {
            binding.etValorPlano.setText(formatarValor(plano.valorPadrao))
        }
    }

    private fun aplicarEstiloChip(chip: TextView, selecionado: Boolean) {
        chip.setBackgroundResource(if (selecionado) R.drawable.bg_chip_selecionado else R.drawable.bg_chip_normal)
        chip.setTextColor(ContextCompat.getColor(this, if (selecionado) R.color.branco else R.color.cinza_texto))
    }

    private fun formatarValor(valor: Double): String =
        String.format(Locale.US, "%.2f", valor)

    private fun carregarCliente(id: Long) {
        lifecycleScope.launch {
            val cliente = database.clienteDao().buscarPorId(id) ?: return@launch
            clienteExistente = cliente
            binding.etNome.setText(cliente.nome)
            binding.etWhatsapp.setText(cliente.whatsapp)
            binding.etUsuario.setText(cliente.usuario)
            binding.etSenha.setText(cliente.senha)
            binding.etObservacao.setText(cliente.observacao ?: "")

            val plano = try {
                PlanoCliente.valueOf(cliente.plano)
            } catch (e: IllegalArgumentException) {
                PlanoCliente.MENSAL
            }
            selecionarPlano(plano, preencherValorPadrao = false)
            binding.etValorPlano.setText(formatarValor(cliente.valorPlano))
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
        val observacao = binding.etObservacao.text.toString().trim().ifBlank { null }
        val valorPlano = binding.etValorPlano.text.toString().trim()
            .replace(",", ".")
            .toDoubleOrNull() ?: planoSelecionado.valorPadrao

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
                    plano = planoSelecionado.name,
                    valorPlano = valorPlano,
                    observacao = observacao,
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
                        nome = nome,
                        whatsapp = whatsapp,
                        usuario = usuario,
                        senha = senha,
                        plano = planoSelecionado.name,
                        valorPlano = valorPlano,
                        observacao = observacao
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
