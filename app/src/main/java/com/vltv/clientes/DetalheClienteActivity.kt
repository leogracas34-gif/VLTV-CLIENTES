package com.vltv.clientes

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.data.ClienteEntity
import com.vltv.clientes.data.PlanoCliente
import com.vltv.clientes.databinding.ActivityDetalheClienteBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Tela só de visualização - mostra os dados do cliente (WhatsApp, status de
// vencimento, plano, valor e observação). Pra editar ou excluir, continua
// sendo pelo ícone de engrenagem na tela principal.
class DetalheClienteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalheClienteBinding
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalheClienteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVoltar.setOnClickListener { finish() }

        val clienteId = intent.getLongExtra(EXTRA_CLIENTE_ID, -1L)
        if (clienteId == -1L) {
            finish()
            return
        }
        carregarCliente(clienteId)
    }

    private fun carregarCliente(id: Long) {
        lifecycleScope.launch {
            val cliente = database.clienteDao().buscarPorId(id)
            if (cliente == null) {
                finish()
                return@launch
            }
            preencherTela(cliente)
        }
    }

    private fun preencherTela(cliente: ClienteEntity) {
        binding.tvAvatar.text = cliente.nome.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        binding.tvNome.text = cliente.nome
        binding.tvWhatsapp.text = formatarTelefone(cliente.whatsapp)
        binding.tvStatus.text = descreverStatus(cliente)

        val plano = try {
            PlanoCliente.valueOf(cliente.plano)
        } catch (e: IllegalArgumentException) {
            PlanoCliente.MENSAL
        }
        binding.tvPlano.text = plano.label
        binding.tvValor.text = formatarMoeda(cliente.valorPlano)

        val observacao = cliente.observacao
        if (observacao.isNullOrBlank()) {
            binding.layoutObservacao.visibility = android.view.View.GONE
        } else {
            binding.layoutObservacao.visibility = android.view.View.VISIBLE
            binding.tvObservacao.text = observacao
        }
    }

    private fun descreverStatus(cliente: ClienteEntity): String {
        val dias = cliente.diasRestantes
        return when {
            cliente.dns.isBlank() -> "Aguardando sincronização"
            cliente.ultimoErro != null && dias == null -> "Erro na última checagem: ${cliente.ultimoErro}"
            dias == null -> "Aguardando primeira checagem"
            dias < 0 -> "Venceu em ${formatarData(cliente.expDateUnix)} (há ${-dias} dias)"
            dias == 0 -> "Vence hoje (${formatarData(cliente.expDateUnix)})"
            else -> "Vence em ${formatarData(cliente.expDateUnix)} ($dias dias restantes)"
        }
    }

    private fun formatarData(expUnix: Long?): String {
        if (expUnix == null) return "--"
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(Date(expUnix * 1000L))
    }

    private fun formatarMoeda(valor: Double): String {
        val nf = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        return nf.format(valor)
    }

    // Formatação simples "+55 31 99999-8888" a partir de dígitos puros -
    // igual à usada no card da tela principal.
    private fun formatarTelefone(numero: String): String {
        val d = numero.filter { it.isDigit() }
        return if (d.length >= 12) {
            val ddi = d.substring(0, d.length - 10)
            val ddd = d.substring(d.length - 10, d.length - 8)
            val parte1 = d.substring(d.length - 8, d.length - 4)
            val parte2 = d.substring(d.length - 4)
            "+$ddi $ddd $parte1-$parte2"
        } else numero
    }

    companion object {
        const val EXTRA_CLIENTE_ID = "cliente_id"
    }
}
