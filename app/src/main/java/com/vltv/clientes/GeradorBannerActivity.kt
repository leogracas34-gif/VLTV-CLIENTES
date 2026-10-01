package com.vltv.clientes

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vltv.clientes.databinding.ActivityGeradorBannerBinding
import com.vltv.clientes.network.BackendApi
import com.vltv.clientes.network.BuscaTmdbResultado
import com.vltv.clientes.network.TmdbResultado
import com.vltv.clientes.ui.BannerComposer
import com.vltv.clientes.ui.CategoriaBanner
import com.vltv.clientes.ui.TmdbResultadoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Tela do Gerador de Banner: busca o pôster oficial no TMDB (via backend,
// que guarda a chave protegida), monta um banner com selo de destaque +
// título + logo por cima, e deixa salvar na galeria ou compartilhar direto
// (ex: mandar pro status do WhatsApp, ou reaproveitar na Transmissão).
class GeradorBannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGeradorBannerBinding
    private lateinit var adapterResultados: TmdbResultadoAdapter

    private var categoriaSelecionada = CategoriaBanner.DESTAQUE_SEMANA
    private var posterAtual: Bitmap? = null
    private var bannerGerado: Bitmap? = null

    private val solicitarPermissaoStorage = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        if (concedida) salvarNaGaleria() else Toast.makeText(this, "Permissão negada - não foi possível salvar.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGeradorBannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnVoltar.setOnClickListener { finish() }

        configurarChipsDeCategoria()

        adapterResultados = TmdbResultadoAdapter(lifecycleScope) { resultado -> escolherResultado(resultado) }
        binding.rvResultados.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvResultados.adapter = adapterResultados

        binding.btnBuscar.setOnClickListener { buscar() }
        binding.etBusca.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                buscar()
                true
            } else {
                false
            }
        }

        binding.btnGerarBanner.setOnClickListener { gerarBanner() }
        binding.btnGerarBannerPrecos.setOnClickListener { gerarBannerDePrecos() }
        binding.btnSalvar.setOnClickListener { pedirPermissaoESalvar() }
        binding.btnCompartilhar.setOnClickListener { compartilhar() }

        binding.chipModoFilme.setOnClickListener { selecionarModo(modoFilme = true) }
        binding.chipModoPrecos.setOnClickListener { selecionarModo(modoFilme = false) }
    }

    // Alterna entre o modo "Filme/Série" (busca no TMDB) e "Preços dos Planos"
    // (banner fixo com os 4 planos cadastrados) - são fluxos independentes,
    // só compartilham a área de preview e os botões de salvar/compartilhar.
    private fun selecionarModo(modoFilme: Boolean) {
        binding.containerModoFilme.visibility = if (modoFilme) View.VISIBLE else View.GONE
        binding.containerModoPrecos.visibility = if (modoFilme) View.GONE else View.VISIBLE

        binding.chipModoFilme.setBackgroundResource(if (modoFilme) R.drawable.bg_chip_selecionado else R.drawable.bg_chip_normal)
        binding.chipModoPrecos.setBackgroundResource(if (modoFilme) R.drawable.bg_chip_normal else R.drawable.bg_chip_selecionado)

        bannerGerado = null
        binding.ivPreviewBanner.visibility = View.GONE
        binding.layoutBotoesFinais.visibility = View.GONE
    }

    private fun gerarBannerDePrecos() {
        lifecycleScope.launch {
            val banner = withContext(Dispatchers.Default) {
                BannerComposer.comporPrecos(this@GeradorBannerActivity)
            }
            bannerGerado = banner
            binding.ivPreviewBanner.setImageBitmap(banner)
            binding.ivPreviewBanner.visibility = View.VISIBLE
            binding.layoutBotoesFinais.visibility = View.VISIBLE
        }
    }

    private fun configurarChipsDeCategoria() {
        val chips = mapOf(
            binding.chipDestaque to CategoriaBanner.DESTAQUE_SEMANA,
            binding.chipTop10Brasil to CategoriaBanner.TOP10_BRASIL,
            binding.chipTop10Mundo to CategoriaBanner.TOP10_MUNDO,
            binding.chipLancamento to CategoriaBanner.LANCAMENTO
        )
        chips.forEach { (chip, categoria) ->
            chip.setOnClickListener {
                categoriaSelecionada = categoria
                atualizarVisualChips(chips)
            }
        }
        atualizarVisualChips(chips)
    }

    private fun atualizarVisualChips(chips: Map<TextView, CategoriaBanner>) {
        chips.forEach { (chip, categoria) ->
            if (categoria == categoriaSelecionada) {
                val fundo = GradientDrawable().apply {
                    setColor(categoria.cor)
                    cornerRadius = 20f * resources.displayMetrics.density
                }
                chip.background = fundo
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_normal)
            }
        }
    }

    private fun buscar() {
        val termo = binding.etBusca.text.toString().trim()
        if (termo.length < 2) {
            Toast.makeText(this, "Digite pelo menos 2 letras.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBusca.visibility = View.VISIBLE
        binding.tvErroBusca.visibility = View.GONE
        binding.rvResultados.visibility = View.GONE

        lifecycleScope.launch {
            when (val resultado = BackendApi.buscarTmdb(this@GeradorBannerActivity, termo)) {
                is BuscaTmdbResultado.Ok -> {
                    binding.progressBusca.visibility = View.GONE
                    if (resultado.resultados.isEmpty()) {
                        binding.tvErroBusca.text = "Nada encontrado pra \"$termo\"."
                        binding.tvErroBusca.visibility = View.VISIBLE
                    } else {
                        adapterResultados.submitList(resultado.resultados)
                        binding.rvResultados.visibility = View.VISIBLE
                    }
                }
                is BuscaTmdbResultado.Falha -> {
                    binding.progressBusca.visibility = View.GONE
                    binding.tvErroBusca.text = resultado.motivo
                    binding.tvErroBusca.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun escolherResultado(item: TmdbResultado) {
        binding.progressBusca.visibility = View.VISIBLE

        lifecycleScope.launch {
            val poster = BackendApi.baixarBitmap(item.posterUrl)
            binding.progressBusca.visibility = View.GONE

            if (poster == null) {
                Toast.makeText(this@GeradorBannerActivity, "Não consegui baixar esse pôster. Tenta outro.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            posterAtual = poster
            binding.etSinopse.setText(item.sinopse)
            binding.tvLabelSinopse.visibility = View.VISIBLE
            binding.etSinopse.visibility = View.VISIBLE
            binding.tvLabelFrase.visibility = View.VISIBLE
            binding.etFrase.visibility = View.VISIBLE
            binding.btnGerarBanner.visibility = View.VISIBLE

            gerarBanner()
        }
    }

    private fun gerarBanner() {
        val poster = posterAtual

        if (poster == null) {
            Toast.makeText(this, "Escolha um filme ou série primeiro.", Toast.LENGTH_SHORT).show()
            return
        }

        val sinopse = binding.etSinopse.text.toString()
        val frase = binding.etFrase.text.toString()

        lifecycleScope.launch {
            // A composição é trabalho de CPU (desenhar no Canvas), não de IO -
            // Dispatchers.Default é o certo aqui, pra não travar a tela.
            val banner = withContext(Dispatchers.Default) {
                BannerComposer.compor(this@GeradorBannerActivity, poster, categoriaSelecionada, sinopse, frase)
            }
            bannerGerado = banner
            binding.ivPreviewBanner.setImageBitmap(banner)
            binding.ivPreviewBanner.visibility = View.VISIBLE
            binding.layoutBotoesFinais.visibility = View.VISIBLE
        }
    }

    private fun pedirPermissaoESalvar() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            solicitarPermissaoStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            salvarNaGaleria()
        }
    }

    private fun salvarNaGaleria() {
        val banner = bannerGerado ?: return
        val nomeArquivo = "vltv_banner_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"

        lifecycleScope.launch {
            val sucesso = withContext(Dispatchers.IO) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, nomeArquivo)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VLTV Clientes")
                        }
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
                    contentResolver.openOutputStream(uri)?.use { out ->
                        banner.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            Toast.makeText(
                this@GeradorBannerActivity,
                if (sucesso) "Banner salvo na galeria." else "Não foi possível salvar o banner.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun compartilhar() {
        val banner = bannerGerado ?: return

        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                try {
                    val pastaCache = File(cacheDir, "banners").apply { mkdirs() }
                    val arquivo = File(pastaCache, "banner_compartilhar.jpg")
                    FileOutputStream(arquivo).use { out ->
                        banner.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    FileProvider.getUriForFile(this@GeradorBannerActivity, "$packageName.fileprovider", arquivo)
                } catch (e: Exception) {
                    null
                }
            }

            if (uri == null) {
                Toast.makeText(this@GeradorBannerActivity, "Não foi possível preparar o banner pra compartilhar.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Compartilhar banner"))
        }
    }
}
