package com.vltv.clientes

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.vltv.clientes.databinding.ActivityGeradorBannerBinding
import com.vltv.clientes.network.BackendApi
import com.vltv.clientes.network.BuscaTmdbResultado
import com.vltv.clientes.network.TmdbResultado
import com.vltv.clientes.ui.BannerComposer
import com.vltv.clientes.ui.CategoriaBanner
import com.vltv.clientes.ui.EstiloFilme
import com.vltv.clientes.ui.FormatoBanner
import com.vltv.clientes.ui.TmdbResultadoAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Tela do Gerador de Banner de filme/série: busca o pôster no TMDB, escolhe
// o selo, o formato (Story ou Feed) e o modelo (Cinema, Cartaz, Neon ou
// Dourado) olhando as miniaturas ao vivo, e gera o banner. Tudo é desenhado
// direto no Canvas, sem depender de internet nem de app externo.
class GeradorBannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGeradorBannerBinding
    private lateinit var adapterResultados: TmdbResultadoAdapter

    private var categoriaSelecionada = CategoriaBanner.DESTAQUE_SEMANA
    private var estiloSel = EstiloFilme.CINEMA
    private var formatoSel = FormatoBanner.STORY

    private var itemAtual: TmdbResultado? = null
    private var posterAtual: Bitmap? = null
    private var bannerGerado: Bitmap? = null

    private var jobMiniaturas: Job? = null
    private val miniaturas = mutableListOf<Pair<View, EstiloFilme>>()
    private val chipsFormato = mutableListOf<Pair<TextView, FormatoBanner>>()

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
        montarChipsFormato()

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

        // Mexeu em qualquer texto -> atualiza as miniaturas dos modelos
        binding.etTitulo.doAfterTextChanged { agendarMiniaturas() }
        binding.etSinopse.doAfterTextChanged { agendarMiniaturas() }
        binding.etFrase.doAfterTextChanged { agendarMiniaturas() }

        binding.btnGerarBanner.setOnClickListener { gerarBanner() }
        binding.btnSalvar.setOnClickListener { pedirPermissaoESalvar() }
        binding.btnCompartilhar.setOnClickListener { compartilhar() }
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    // ───────────────────────── Selo e formato ────────────────────────────

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
                agendarMiniaturas()
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

    private fun montarChipsFormato() {
        binding.linhaFormatosFilme.removeAllViews()
        chipsFormato.clear()

        FormatoBanner.values().forEach { formato ->
            val chip = TextView(this).apply {
                text = formato.rotulo
                setTextColor(Color.WHITE)
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(10), dp(20), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener {
                    formatoSel = formato
                    atualizarVisualFormatos()
                    agendarMiniaturas()
                }
            }
            binding.linhaFormatosFilme.addView(chip)
            chipsFormato.add(Pair(chip, formato))
        }
        atualizarVisualFormatos()
    }

    private fun atualizarVisualFormatos() {
        chipsFormato.forEach { (chip, formato) ->
            chip.setBackgroundResource(if (formato == formatoSel) R.drawable.bg_chip_selecionado else R.drawable.bg_chip_normal)
        }
    }

    // ───────────────────────── Busca no TMDB ─────────────────────────────

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

            itemAtual = item
            posterAtual = poster
            binding.etTitulo.setText(item.titulo)
            binding.etSinopse.setText(item.sinopse)

            binding.tvLabelTitulo.visibility = View.VISIBLE
            binding.etTitulo.visibility = View.VISIBLE
            binding.tvLabelSinopse.visibility = View.VISIBLE
            binding.etSinopse.visibility = View.VISIBLE
            binding.tvLabelFrase.visibility = View.VISIBLE
            binding.etFrase.visibility = View.VISIBLE
            binding.tvLabelFormato.visibility = View.VISIBLE
            binding.linhaFormatosFilme.visibility = View.VISIBLE
            binding.tvLabelModeloFilme.visibility = View.VISIBLE
            binding.scrollModelosFilme.visibility = View.VISIBLE
            binding.btnGerarBanner.visibility = View.VISIBLE

            agendarMiniaturas()
            Toast.makeText(this@GeradorBannerActivity, "Escolha o modelo e toque em Gerar Banner.", Toast.LENGTH_SHORT).show()
        }
    }

    // ───────────────────────── Modelos (miniaturas ao vivo) ──────────────

    private class Params(
        val poster: Bitmap,
        val categoria: CategoriaBanner,
        val titulo: String,
        val tipo: String,
        val ano: String,
        val sinopse: String,
        val frase: String,
        val formato: FormatoBanner
    )

    private fun lerParams(): Params? {
        val poster = posterAtual ?: return null
        val item = itemAtual ?: return null
        return Params(
            poster,
            categoriaSelecionada,
            binding.etTitulo.text.toString(),
            item.tipo,
            item.ano,
            binding.etSinopse.text.toString(),
            binding.etFrase.text.toString(),
            formatoSel
        )
    }

    private fun desenhar(params: Params, estilo: EstiloFilme, escala: Float): Bitmap =
        BannerComposer.compor(
            this, params.poster, params.categoria, params.titulo, params.tipo, params.ano,
            params.sinopse, params.frase, estilo, params.formato, escala
        )

    private fun agendarMiniaturas() {
        val params = lerParams() ?: return
        jobMiniaturas?.cancel()

        jobMiniaturas = lifecycleScope.launch {
            delay(250)
            val estilos = EstiloFilme.values().toList()
            val bitmaps = try {
                withContext(Dispatchers.Default) { estilos.map { desenhar(params, it, 0.2f) } }
            } catch (e: Throwable) {
                null
            } ?: return@launch

            binding.galeriaModelosFilme.removeAllViews()
            miniaturas.clear()

            estilos.forEachIndexed { i, estilo ->
                val img = ImageView(this@GeradorBannerActivity).apply {
                    setImageBitmap(bitmaps[i])
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                    layoutParams = when (params.formato) {
                        FormatoBanner.STORY -> LinearLayout.LayoutParams(dp(104), dp(184))
                        FormatoBanner.FEED -> LinearLayout.LayoutParams(dp(132), dp(165))
                    }.apply { marginEnd = dp(10) }
                    setOnClickListener {
                        estiloSel = estilo
                        atualizarBordasMiniaturas()
                    }
                }
                binding.galeriaModelosFilme.addView(img)
                miniaturas.add(Pair(img, estilo))
            }
            atualizarBordasMiniaturas()
        }
    }

    private fun atualizarBordasMiniaturas() {
        miniaturas.forEach { (view, estilo) ->
            view.setBackgroundResource(if (estilo == estiloSel) R.drawable.bg_card_selecionado else R.drawable.bg_card)
        }
    }

    // ───────────────────────── Gerar, salvar e compartilhar ──────────────

    private fun gerarBanner() {
        val params = lerParams()
        if (params == null) {
            Toast.makeText(this, "Escolha um filme ou série primeiro.", Toast.LENGTH_SHORT).show()
            return
        }
        val estilo = estiloSel

        binding.btnGerarBanner.text = "Gerando..."
        binding.btnGerarBanner.isEnabled = false

        lifecycleScope.launch {
            val banner = try {
                withContext(Dispatchers.Default) { desenhar(params, estilo, 1f) }
            } catch (e: Throwable) {
                null
            }
            binding.btnGerarBanner.text = "Gerar Banner"
            binding.btnGerarBanner.isEnabled = true

            if (banner == null) {
                Toast.makeText(this@GeradorBannerActivity, "Não foi possível gerar o banner. Tenta de novo.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            mostrarBanner(banner)
        }
    }

    // Mostra o banner pronto e rola a tela até ele.
    private fun mostrarBanner(banner: Bitmap) {
        bannerGerado = banner
        binding.ivPreviewBanner.setImageBitmap(banner)
        binding.ivPreviewBanner.visibility = View.VISIBLE
        binding.layoutBotoesFinais.visibility = View.VISIBLE
        Toast.makeText(this, "Banner gerado! Toque em Salvar ou Compartilhar.", Toast.LENGTH_SHORT).show()

        binding.ivPreviewBanner.post {
            binding.scrollPrincipal.smoothScrollTo(0, binding.ivPreviewBanner.top - dp(8))
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
                        banner.compress(Bitmap.CompressFormat.JPEG, 95, out)
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
                        banner.compress(Bitmap.CompressFormat.JPEG, 95, out)
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
