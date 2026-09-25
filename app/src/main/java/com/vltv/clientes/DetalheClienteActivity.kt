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

    // ✅ CORRIGIDO: mesma prioridade da tela principal (ClienteAdapter) -
    // se já sabemos os dias restantes, isso manda, mesmo que uma
    // resincronização mais recente tenha falhado e zerado o dns/gerado erro.
    // "Aguardando sincronização"/"Erro" só aparecem quando o vencimento
    // desse cliente nunca foi descoberto.
    private fun descreverStatus(cliente: ClienteEntity): String {
        val dias = cliente.diasRestantes
        return when {
            dias != null && dias < 0 -> {
                if (cliente.expDateUnix != null)
                    "Venceu em ${formatarData(cliente.expDateUnix)} (há ${-dias} dias)"
                else
                    "Vencido — conta indisponível em todos os servidores testados (data exata desconhecida)"
            }
            dias != null && dias == 0 -> "Vence hoje (${formatarData(cliente.expDateUnix)})"
            dias != null -> "Vence em ${formatarData(cliente.expDateUnix)} ($dias dias restantes)"
            cliente.dns.isBlank() && cliente.ultimoErro == null -> "Aguardando sincronização"
            cliente.ultimoErro != null -> "Erro na última checagem: ${cliente.ultimoErro}"
            else -> "Aguardando primeira checagem"
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

    // ✅ CORRIGIDO: essa função ainda usava a lógica antiga (assumia sempre
    // 8 dígitos de número local, cortando a partir do fim), que quebra pro
    // celular brasileiro padrão de 9 dígitos - um número de 13 dígitos
    // (55+DDD+9 dígitos) ficava com DDI "553" e DDD "19" em vez de "55"/"31"
    // (ex: "+553 19 9304-7590"). Agora trata explicitamente os tamanhos
    // possíveis, igual à versão já corrigida usada no card da tela principal
    // (ClienteAdapter).
    private fun formatarTelefone(numero: String): String {
        val d = numero.filter { it.isDigit() }
        return when (d.length) {
            13 -> { // 55 + DDD(2) + 9 dígitos locais (celular padrão atual)
                val ddi = d.substring(0, 2)
                val ddd = d.substring(2, 4)
                val parte1 = d.substring(4, 9)
                val parte2 = d.substring(9)
                "+$ddi $ddd $parte1-$parte2"
            }
            12 -> { // 55 + DDD(2) + 8 dígitos locais (fixo, ou celular sem o 9)
                val ddi = d.substring(0, 2)
                val ddd = d.substring(2, 4)
                val parte1 = d.substring(4, 8)
                val parte2 = d.substring(8)
                "+$ddi $ddd $parte1-$parte2"
            }
            11 -> { // sem DDI: DDD(2) + 9 dígitos locais
                val ddd = d.substring(0, 2)
                val parte1 = d.substring(2, 7)
                val parte2 = d.substring(7)
                "$ddd $parte1-$parte2"
            }
            10 -> { // sem DDI: DDD(2) + 8 dígitos locais
                val ddd = d.substring(0, 2)
                val parte1 = d.substring(2, 6)
                val parte2 = d.substring(6)
                "$ddd $parte1-$parte2"
            }
            else -> numero
        }
    }

    companion object {
        const val EXTRA_CLIENTE_ID = "cliente_id"
    }
}
