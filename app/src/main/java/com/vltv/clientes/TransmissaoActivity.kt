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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vltv.clientes.data.AppDatabase
import com.vltv.clientes.data.ClienteEntity
import com.vltv.clientes.databinding.ActivityTransmissaoBinding
import com.vltv.clientes.network.BackendApi
import com.vltv.clientes.network.EnvioResultado
import com.vltv.clientes.ui.TransmissaoClienteAdapter
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

// Tela de "lista de transmissão" própria do app: escreve uma mensagem
// (com {nome} substituído automaticamente pra cada cliente), opcionalmente
// anexa uma imagem, marca quais clientes vão receber e envia pra todos de
// uma vez - reaproveitando o backend/fila que já existe (que já cuida do
// espaçamento entre envios pra não levar o número a ser marcado como spam).
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

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Confirmar envio")
            .setMessage("Enviar essa mensagem para ${selecionados.size} cliente(s)?")
            .setPositiveButton("Enviar") { _, _ -> enviarParaTodos(mensagem, selecionados) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun enviarParaTodos(mensagemTemplate: String, clientes: List<ClienteEntity>) {
        enviando = true
        binding.btnEnviarTransmissao.visibility = View.GONE
        binding.progressEnvio.visibility = View.VISIBLE

        lifecycleScope.launch {
            var sucesso = 0
            var falhas = 0

            for (cliente in clientes) {
                val mensagemPersonalizada = mensagemTemplate.replace("{nome}", cliente.nome)
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

            if (falhas == 0) finish()
        }
    }
}
