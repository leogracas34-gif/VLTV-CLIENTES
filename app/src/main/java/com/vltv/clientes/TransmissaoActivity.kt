package com.vltv.clientes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.data.ClienteEntity
import com.vltv.clientes.databinding.ActivityTransmissaoBinding
import com.vltv.clientes.network.AppConfig
import com.vltv.clientes.network.BackendApi
import com.vltv.clientes.network.BuscaClienteResultado
import com.vltv.clientes.network.EnvioResultado
import com.vltv.clientes.network.XtreamCheck
import com.vltv.clientes.ui.TransmissaoClienteAdapter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Tela de "lista de transmissão" própria do app: escreve uma mensagem
// (com {nome}, {login} e {vencimento} substituídos automaticamente pra cada
// cliente), opcionalmente anexa uma imagem, marca quais clientes vão receber
// e envia pra todos de uma vez - reaproveitando o backend/fila que já existe
// (que já cuida do espaçamento entre envios pra não levar o número a ser
// marcado como spam).
//
// Mensagem de renovação: o botão "Usar modelo de renovação" carrega o modelo
// salvo no campo de mensagem, e "Salvar como modelo" guarda o texto atual
// como novo modelo. Quando a mensagem tem {vencimento}, o app consulta o
// servidor de cada cliente selecionado ANTES de enviar, pra garantir que a
// data seja a nova (depois da renovação no painel) e não a antiga.
class TransmissaoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransmissaoBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private lateinit var adapter: TransmissaoClienteAdapter

    private var clientesAtivos: List<ClienteEntity> = emptyList()
    private var imagemBase64: String? = null
    private var enviando = false

    // Tamanho máximo (lado maior) da imagem depois de redimensionada, e
    // qualidade do JPEG - mantém o payload pequeno o suficiente pra não
    // pesar no envio pelo 4G do cliente nem estourar o limite do backend.
    private val LADO_MAXIMO_IMAGEM = 1280
    private val QUALIDADE_JPEG = 75

    // Cliente já consultado no servidor + se a consulta deu certo.
    private data class ClienteConsultado(val cliente: ClienteEntity, val atualizado: Boolean)

    private val escolherImagemLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) processarImagemEscolhida(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransmissaoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = TransmissaoClienteAdapter(
            onToggle = { atualizarContador() }
        )
        binding.rvClientesTransmissao.layoutManager = LinearLayoutManager(this)
        binding.rvClientesTransmissao.adapter = adapter

        // Só clientes ativos entram na lista - não faz sentido mandar
        // novidade pra quem já foi desativado/cancelou.
        database.clienteDao().observarTodos().observe(this) { lista ->
            clientesAtivos = lista.filter { it.ativo }
            adapter.submitList(clientesAtivos)
            atualizarContador()
        }

        binding.btnVoltar.setOnClickListener { finish() }
        binding.btnGeradorBanner.setOnClickListener { startActivity(Intent(this, GeradorBannerActivity::class.java)) }

        binding.btnEscolherImagem.setOnClickListener { escolherImagemLauncher.launch("image/*") }
        binding.btnRemoverImagem.setOnClickListener { removerImagem() }

        binding.btnModeloRenovacao.setOnClickListener { usarModeloRenovacao() }
        binding.btnSalvarModelo.setOnClickListener { salvarComoModelo() }

        binding.btnSelecionarTodos.setOnClickListener {
            if (adapter.quantidadeSelecionada() < clientesAtivos.size) {
                adapter.selecionarTodos()
            } else {
                adapter.desmarcarTodos()
            }
            atualizarContador()
        }

        binding.btnEnviarTransmissao.setOnClickListener { confirmarEnvio() }

        atualizarContador()
    }

    // Carrega o modelo de renovação salvo no campo de mensagem. Se já tem
    // outro texto escrito, pergunta antes de substituir.
    private fun usarModeloRenovacao() {
        val modelo = AppConfig.getMensagemRenovacao(this)
        val atual = binding.etMensagem.text.toString().trim()

        if (atual.isBlank() || atual == modelo.trim()) {
            aplicarTextoNoCampo(modelo)
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Substituir mensagem?")
            .setMessage("O texto que você escreveu será trocado pelo modelo de renovação.")
            .setPositiveButton("Substituir") { _, _ -> aplicarTextoNoCampo(modelo) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun aplicarTextoNoCampo(texto: String) {
        binding.etMensagem.setText(texto)
        binding.etMensagem.setSelection(binding.etMensagem.text.length)
    }

    // Guarda o texto atual como novo modelo de renovação (pra quando quiser
    // mudar a mensagem padrão).
    private fun salvarComoModelo() {
        val texto = binding.etMensagem.text.toString().trim()
        if (texto.isBlank()) {
            Toast.makeText(this, "Escreva a mensagem antes de salvar como modelo.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Salvar como modelo?")
            .setMessage("Esse texto vira o novo modelo de renovação.")
            .setPositiveButton("Salvar") { _, _ ->
                AppConfig.salvarMensagemRenovacao(this, texto)
                Toast.makeText(this, "Modelo de renovação salvo.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun processarImagemEscolhida(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            val redimensionada = redimensionar(original, LADO_MAXIMO_IMAGEM)
            val outputStream = ByteArrayOutputStream()
            redimensionada.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, outputStream)
            imagemBase64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            binding.ivPreviewImagem.setImageBitmap(redimensionada)
            binding.layoutSemImagem.visibility = View.GONE
            binding.layoutComImagem.visibility = View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível carregar essa imagem.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun redimensionar(bitmap: Bitmap, ladoMaximo: Int): Bitmap {
        val largura = bitmap.width
        val altura = bitmap.height
        if (largura <= ladoMaximo && altura <= ladoMaximo) return bitmap

        val proporcao = if (largura > altura) {
            ladoMaximo.toFloat() / largura
        } else {
            ladoMaximo.toFloat() / altura
        }
        val novaLargura = (largura * proporcao).toInt()
        val novaAltura = (altura * proporcao).toInt()
        return Bitmap.createScaledBitmap(bitmap, novaLargura, novaAltura, true)
    }

    private fun removerImagem() {
        imagemBase64 = null
        binding.ivPreviewImagem.setImageBitmap(null)
        binding.layoutComImagem.visibility = View.GONE
        binding.layoutSemImagem.visibility = View.VISIBLE
    }

    private fun atualizarContador() {
        val selecionados = adapter.quantidadeSelecionada()
        val total = clientesAtivos.size
        binding.tvContadorSelecionados.text = "$selecionados de $total selecionados"
        binding.btnSelecionarTodos.text = if (selecionados < total) "Selecionar todos" else "Desmarcar todos"
    }

    private fun confirmarEnvio() {
        if (enviando) return

        val mensagem = binding.etMensagem.text.toString().trim()
        if (mensagem.isBlank()) {
            Toast.makeText(this, "Escreva uma mensagem antes de enviar.", Toast.LENGTH_SHORT).show()
            return
        }

        val selecionados = adapter.getClientesSelecionados()
        if (selecionados.isEmpty()) {
            Toast.makeText(this, "Selecione pelo menos um cliente.", Toast.LENGTH_SHORT).show()
            return
        }

        val aviso = if (mensagem.contains("{vencimento}")) {
            "\n\nAntes de enviar, o app confere o vencimento de cada cliente no servidor."
        } else {
            ""
        }

        AlertDialog.Builder(this)
            .setTitle("Confirmar envio")
            .setMessage("Enviar essa mensagem para ${selecionados.size} cliente(s)?$aviso")
            .setPositiveButton("Enviar") { _, _ -> enviarParaTodos(mensagem, selecionados) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Consulta o servidor de cada cliente (em paralelo) e grava no banco o
    // vencimento atualizado. Se a consulta de um cliente falhar, ele volta
    // marcado como "não atualizado" - quem chama decide o que fazer.
    private suspend fun consultarVencimentos(clientes: List<ClienteEntity>): List<ClienteConsultado> =
        coroutineScope {
            clientes.map { cliente ->
                async {
                    try {
                        val resultado = if (cliente.dns.isBlank()) {
                            XtreamCheck.buscarCliente(this@TransmissaoActivity, cliente.usuario, cliente.senha)
                        } else {
                            XtreamCheck.reconsultar(this@TransmissaoActivity, cliente.dns, cliente.usuario, cliente.senha)
                        }

                        if (resultado is BuscaClienteResultado.Sucesso) {
                            val r = resultado.resultado
                            val novo = cliente.copy(
                                dns = r.dns,
                                expDateUnix = r.expDateUnix,
                                diasRestantes = r.diasRestantes,
                                ultimaChecagemEm = System.currentTimeMillis(),
                                ultimoErro = null
                            )
                            database.clienteDao().atualizar(novo)
                            ClienteConsultado(novo, true)
                        } else {
                            ClienteConsultado(cliente, false)
                        }
                    } catch (e: Exception) {
                        ClienteConsultado(cliente, false)
                    }
                }
            }.awaitAll()
        }

    private fun inicioDeHoje(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun formatarVencimento(expUnix: Long?): String {
        if (expUnix == null) return "--"
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        return sdf.format(Date(expUnix * 1000L))
    }

    // Troca os campos automáticos pelos dados reais do cliente.
    private fun personalizar(template: String, cliente: ClienteEntity): String =
        template
            .replace("{nome}", cliente.nome)
            .replace("{login}", cliente.usuario)
            .replace("{vencimento}", formatarVencimento(cliente.expDateUnix))

    private fun enviarParaTodos(mensagemTemplate: String, clientes: List<ClienteEntity>) {
        enviando = true
        binding.btnEnviarTransmissao.visibility = View.GONE
        binding.progressEnvio.visibility = View.VISIBLE

        lifecycleScope.launch {
            var sucesso = 0
            var falhas = 0
            val pulados = mutableListOf<String>()

            val precisaVencimento = mensagemTemplate.contains("{vencimento}")
            val preparados: List<ClienteConsultado> = if (precisaVencimento) {
                consultarVencimentos(clientes)
            } else {
                clientes.map { ClienteConsultado(it, true) }
            }

            val hoje = inicioDeHoje()

            for (item in preparados) {
                val cliente = item.cliente

                // Segurança: nunca manda "renovado até X" com data velha ou
                // desconhecida. Quem cair aqui não recebe nada e aparece na
                // lista de pulados pra você conferir.
                if (precisaVencimento) {
                    if (!item.atualizado) {
                        pulados.add("${cliente.nome}: não consegui conferir o vencimento no servidor")
                        continue
                    }
                    val exp = cliente.expDateUnix
                    if (exp == null || exp * 1000L < hoje) {
                        pulados.add("${cliente.nome}: ainda aparece vencido no painel (renovou lá?)")
                        continue
                    }
                }

                val mensagemPersonalizada = personalizar(mensagemTemplate, cliente)
                val resultado = BackendApi.enviarMensagem(
                    this@TransmissaoActivity,
                    cliente.whatsapp,
                    mensagemPersonalizada,
                    imagemBase64
                )
                when (resultado) {
                    is EnvioResultado.Ok -> sucesso++
                    is EnvioResultado.Falha -> falhas++
                }
            }

            enviando = false
            binding.btnEnviarTransmissao.visibility = View.VISIBLE
            binding.progressEnvio.visibility = View.GONE

            val mensagemFinal = if (falhas == 0) {
                "Enfileirado com sucesso para $sucesso cliente(s). O envio de verdade acontece aos poucos, respeitando o intervalo entre mensagens."
            } else {
                "Enfileirado para $sucesso cliente(s). $falhas falharam ao enfileirar - confira a conexão com o servidor."
            }
            Toast.makeText(this@TransmissaoActivity, mensagemFinal, Toast.LENGTH_LONG).show()

            if (pulados.isNotEmpty()) {
                AlertDialog.Builder(this@TransmissaoActivity)
                    .setTitle("${pulados.size} cliente(s) não receberam")
                    .setMessage(pulados.joinToString("\n\n"))
                    .setPositiveButton("OK", null)
                    .show()
            } else if (falhas == 0) {
                finish()
            }
        }
    }
}
