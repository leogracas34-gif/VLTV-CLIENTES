package com.vltv.clientes

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import com.vltv.clientes.data.PlanoCliente
import com.vltv.clientes.databinding.ActivityGeradorBannerBinding
import com.vltv.clientes.network.BackendApi
import com.vltv.clientes.network.BuscaTmdbResultado
import com.vltv.clientes.network.TmdbResultado
import com.vltv.clientes.ui.BannerComposer
import com.vltv.clientes.ui.CategoriaBanner
import com.vltv.clientes.ui.EstiloFilme
import com.vltv.clientes.ui.EstiloPreco
import com.vltv.clientes.ui.FormatoBanner
import com.vltv.clientes.ui.PaletaBanner
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

// Tela do Gerador de Banner. Dois fluxos:
//  1) Filme ou Série: busca o pôster no TMDB, escolhe o selo e o modelo
//     (Cinema / Cartaz / Neon) e gera o banner.
//  2) Preços dos Planos: escolhe o plano (Mensal, Trimestral, Semestral,
//     Anual ou Indique e Ganhe), o valor, o modelo e a cor, e gera o banner.
// Em ambos, os modelos aparecem como miniaturas ao vivo, e o banner final
// é desenhado direto no Canvas (sem depender de internet nem do Canva).
class GeradorBannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGeradorBannerBinding
    private lateinit var adapterResultados: TmdbResultadoAdapter

    // Filme / série
    private var categoriaSelecionada = CategoriaBanner.DESTAQUE_SEMANA
    private var estiloFilmeSel = EstiloFilme.CINEMA
    private var posterAtual: Bitmap? = null
    private var jobMiniaturasFilme: Job? = null
    private val miniaturasFilme = mutableListOf<View>()

    // Preços / indicação (planoSel == null significa "Indique e Ganhe")
    private var planoSel: PlanoCliente? = PlanoCliente.MENSAL
    private var estiloPrecoSel = EstiloPreco.PREMIUM
    private var paletaSel = PaletaBanner.DOURADO
    private var formatoSel = FormatoBanner.PAISAGEM
    private var fotoIndicacao: Bitmap? = null
    private val chipsFormato = mutableListOf<Pair<TextView, FormatoBanner>>()
    private var precosInicializado = false
    private var jobMiniaturasPreco: Job? = null
    private val chipsPlano = mutableListOf<Pair<TextView, PlanoCliente?>>()
    private val bolasCor = mutableListOf<Pair<View, PaletaBanner>>()
    private val miniaturasPreco = mutableListOf<Pair<View, EstiloPreco>>()

    private var bannerGerado: Bitmap? = null

    private val escolherFoto = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) carregarFoto(uri) }

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

        // Mudou sinopse ou frase -> atualiza as miniaturas dos modelos
        binding.etSinopse.doAfterTextChanged { agendarMiniaturasFilme() }
        binding.etFrase.doAfterTextChanged { agendarMiniaturasFilme() }

        // Mudou valor ou frase do plano -> atualiza as miniaturas
        binding.etValorPreco.doAfterTextChanged { agendarMiniaturasPreco() }
        binding.etTextoExtra.doAfterTextChanged { agendarMiniaturasPreco() }
        binding.etQtdAmigos.doAfterTextChanged { agendarMiniaturasPreco() }

        binding.btnEscolherFoto.setOnClickListener { escolherFoto.launch("image/*") }
        binding.btnRemoverFoto.setOnClickListener {
            fotoIndicacao = null
            binding.btnRemoverFoto.visibility = View.GONE
            binding.btnEscolherFoto.text = "Escolher foto da galeria"
            agendarMiniaturasPreco()
        }

        binding.btnGerarBanner.setOnClickListener { gerarBanner() }
        binding.btnGerarBannerPrecos.setOnClickListener { gerarBannerDePrecos() }
        binding.btnSalvar.setOnClickListener { pedirPermissaoESalvar() }
        binding.btnCompartilhar.setOnClickListener { compartilhar() }

        binding.chipModoFilme.setOnClickListener { selecionarModo(modoFilme = true) }
        binding.chipModoPrecos.setOnClickListener { selecionarModo(modoFilme = false) }
    }

    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    // ───────────────────────── Modo (Filme x Preços) ─────────────────────

    private fun selecionarModo(modoFilme: Boolean) {
        binding.containerModoFilme.visibility = if (modoFilme) View.VISIBLE else View.GONE
        binding.containerModoPrecos.visibility = if (modoFilme) View.GONE else View.VISIBLE

        binding.chipModoFilme.setBackgroundResource(if (modoFilme) R.drawable.bg_chip_selecionado else R.drawable.bg_chip_normal)
        binding.chipModoPrecos.setBackgroundResource(if (modoFilme) R.drawable.bg_chip_normal else R.drawable.bg_chip_selecionado)

        bannerGerado = null
        binding.ivPreviewBanner.visibility = View.GONE
        binding.layoutBotoesFinais.visibility = View.GONE

        if (!modoFilme && !precosInicializado) {
            precosInicializado = true
            montarChipsPlanos()
            montarBolasDeCor()
            montarChipsFormato()
            selecionarPlano(PlanoCliente.MENSAL)
        }
    }

    // ───────────────────────── Preços dos planos / Indique e Ganhe ───────

    private fun nomeCurto(plano: PlanoCliente): String = when (plano) {
        PlanoCliente.MENSAL -> "Mensal"
        PlanoCliente.TRIMESTRAL -> "Trimestral"
        PlanoCliente.SEMESTRAL -> "Semestral"
        PlanoCliente.ANUAL -> "Anual"
    }

    private fun formatarValor(valor: Double): String =
        String.format(Locale("pt", "BR"), "%.2f", valor)

    private fun montarChipsPlanos() {
        binding.linhaPlanos.removeAllViews()
        chipsPlano.clear()

        val itens: List<PlanoCliente?> = PlanoCliente.values().toList() + listOf<PlanoCliente?>(null)
        itens.forEach { plano ->
            val texto = if (plano != null) {
                "${nomeCurto(plano)}\nR$ ${formatarValor(plano.valorPadrao)}"
            } else {
                "Indique\ne Ganhe"
            }
            val chip = TextView(this).apply {
                text = texto
                setTextColor(Color.WHITE)
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(10), dp(18), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener { selecionarPlano(plano) }
            }
            binding.linhaPlanos.addView(chip)
            chipsPlano.add(Pair(chip, plano))
        }
    }

    private fun montarBolasDeCor() {
        binding.linhaCores.removeAllViews()
        bolasCor.clear()

        PaletaBanner.values().forEach { paleta ->
            val bola = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) }
                setOnClickListener {
                    paletaSel = paleta
                    atualizarVisualCores()
                    agendarMiniaturasPreco()
                }
            }
            binding.linhaCores.addView(bola)
            bolasCor.add(Pair(bola, paleta))
        }
        atualizarVisualCores()
    }

    private fun montarChipsFormato() {
        binding.linhaFormatos.removeAllViews()
        chipsFormato.clear()

        FormatoBanner.values().forEach { formato ->
            val chip = TextView(this).apply {
                text = formato.rotulo
                setTextColor(Color.WHITE)
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(10), dp(18), dp(10))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener {
                    formatoSel = formato
                    atualizarVisualFormatos()
                    agendarMiniaturasPreco()
                }
            }
            binding.linhaFormatos.addView(chip)
            chipsFormato.add(Pair(chip, formato))
        }
        atualizarVisualFormatos()
    }

    private fun atualizarVisualFormatos() {
        chipsFormato.forEach { (chip, formato) ->
            chip.setBackgroundResource(if (formato == formatoSel) R.drawable.bg_chip_selecionado else R.drawable.bg_chip_normal)
        }
    }

    private fun carregarFoto(uri: Uri) {
        lifecycleScope.launch {
            val foto = withContext(Dispatchers.IO) {
                try {
                    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }
                    var amostra = 1
                    while (limites.outWidth / amostra > 1600 || limites.outHeight / amostra > 1600) amostra *= 2
                    val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opcoes) }
                } catch (e: Throwable) {
                    null
                }
            }
            if (foto == null) {
                Toast.makeText(this@GeradorBannerActivity, "Não consegui abrir essa foto.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            fotoIndicacao = foto
            binding.btnEscolherFoto.text = "Trocar foto"
            binding.btnRemoverFoto.visibility = View.VISIBLE
            // A foto só aparece no modelo Festa - já seleciona ele pra você ver
            estiloPrecoSel = EstiloPreco.FESTA
            agendarMiniaturasPreco()
        }
    }

    private fun atualizarVisualCores() {
        bolasCor.forEach { (view, paleta) ->
            val fundo = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(paleta.base)
                if (paleta == paletaSel) setStroke(dp(3), Color.WHITE) else setStroke(dp(1), 0x55FFFFFF)
            }
            view.background = fundo
        }
    }

    private fun atualizarVisualPlanos() {
        chipsPlano.forEach { (chip, plano) ->
            chip.setBackgroundResource(if (plano == planoSel) R.drawable.bg_chip_selecionado else R.drawable.bg_chip_normal)
        }
    }

    private fun selecionarPlano(plano: PlanoCliente?) {
        planoSel = plano
        atualizarVisualPlanos()

        binding.blocoQtd.visibility = if (plano == null) View.VISIBLE else View.GONE
        binding.blocoFoto.visibility = if (plano == null) View.VISIBLE else View.GONE

        if (plano != null) {
            if (estiloPrecoSel == EstiloPreco.FESTA) estiloPrecoSel = EstiloPreco.PREMIUM
            binding.blocoValor.visibility = View.VISIBLE
            binding.tvLabelExtra.text = "Frase personalizada (opcional)"
            binding.etTextoExtra.hint = "Ex: Aproveite 3 meses de conteúdo incrível!"
            binding.etValorPreco.setText(formatarValor(plano.valorPadrao))
        } else {
            binding.blocoValor.visibility = View.GONE
            binding.tvLabelExtra.text = "Observação (opcional)"
            binding.etTextoExtra.hint = BannerComposer.NOTA_INDICACAO_PADRAO
            // Na indicação não existe o modelo "Todos os planos"
            if (estiloPrecoSel == EstiloPreco.TABELA) estiloPrecoSel = EstiloPreco.FESTA
        }
        binding.etTextoExtra.setText("")
        agendarMiniaturasPreco()
    }

    private fun estilosDisponiveis(): List<EstiloPreco> =
        if (planoSel == null) {
            listOf(EstiloPreco.FESTA, EstiloPreco.PREMIUM, EstiloPreco.VIDRO, EstiloPreco.CIRCULO)
        } else {
            EstiloPreco.values().toList().filter { it != EstiloPreco.FESTA }
        }

    private fun valorDigitado(plano: PlanoCliente): Double =
        binding.etValorPreco.text.toString().trim().replace(',', '.').toDoubleOrNull() ?: plano.valorPadrao

    // Foto do que está escolhido na tela agora (lida na thread principal,
    // pra poder desenhar o banner em segundo plano sem mexer em View).
    private class ParamsPreco(
        val plano: PlanoCliente?,
        val valor: Double,
        val estilo: EstiloPreco,
        val paleta: PaletaBanner,
        val texto: String,
        val qtdAmigos: Int,
        val foto: Bitmap?,
        val formato: FormatoBanner
    )

    private fun lerParamsPreco(): ParamsPreco {
        val plano = planoSel
        val valor = if (plano != null) valorDigitado(plano) else 0.0
        val qtd = binding.etQtdAmigos.text.toString().trim().toIntOrNull() ?: 1
        return ParamsPreco(
            plano, valor, estiloPrecoSel, paletaSel,
            binding.etTextoExtra.text.toString(), qtd, fotoIndicacao, formatoSel
        )
    }

    private fun desenharPreco(params: ParamsPreco, estilo: EstiloPreco, escala: Float): Bitmap =
        if (params.plano == null) {
            BannerComposer.comporIndicacao(this, estilo, params.paleta, params.qtdAmigos, params.texto, params.foto, params.formato, escala)
        } else {
            BannerComposer.comporPlano(this, params.plano, params.valor, estilo, params.paleta, params.texto, params.formato, escala)
        }

    private fun agendarMiniaturasPreco() {
        if (!precosInicializado) return
        jobMiniaturasPreco?.cancel()

        val params = lerParamsPreco()
        val estilos = estilosDisponiveis()

        jobMiniaturasPreco = lifecycleScope.launch {
            delay(250)
            val bitmaps = try {
                withContext(Dispatchers.Default) { estilos.map { desenharPreco(params, it, 0.25f) } }
            } catch (e: Throwable) {
                null
            } ?: return@launch

            binding.galeriaModelosPreco.removeAllViews()
            miniaturasPreco.clear()

            estilos.forEachIndexed { i, estilo ->
                val img = ImageView(this@GeradorBannerActivity).apply {
                    setImageBitmap(bitmaps[i])
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                    layoutParams = when (params.formato) {
                        FormatoBanner.PAISAGEM -> LinearLayout.LayoutParams(dp(230), dp(134))
                        FormatoBanner.QUADRADO -> LinearLayout.LayoutParams(dp(150), dp(150))
                        FormatoBanner.STORY -> LinearLayout.LayoutParams(dp(95), dp(168))
                    }.apply { marginEnd = dp(10) }
                    setOnClickListener {
                        estiloPrecoSel = estilo
                        atualizarBordasMiniaturasPreco()
                    }
                }
                binding.galeriaModelosPreco.addView(img)
                miniaturasPreco.add(Pair(img, estilo))
            }
            atualizarBordasMiniaturasPreco()
        }
    }

    private fun atualizarBordasMiniaturasPreco() {
        miniaturasPreco.forEach { (view, estilo) ->
            view.setBackgroundResource(if (estilo == estiloPrecoSel) R.drawable.bg_card_selecionado else R.drawable.bg_card)
        }
    }

    private fun gerarBannerDePrecos() {
        val params = lerParamsPreco()
        if (params.plano != null && params.valor <= 0.0) {
            Toast.makeText(this, "Digite um valor válido.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnGerarBannerPrecos.text = "Gerando..."
        binding.btnGerarBannerPrecos.isEnabled = false

        lifecycleScope.launch {
            val banner = try {
                withContext(Dispatchers.Default) { desenharPreco(params, params.estilo, 1f) }
            } catch (e: Throwable) {
                null
            }
            binding.btnGerarBannerPrecos.text = "Gerar banner"
            binding.btnGerarBannerPrecos.isEnabled = true

            if (banner == null) {
                Toast.makeText(this@GeradorBannerActivity, "Não foi possível gerar o banner. Tenta de novo.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            mostrarBanner(banner)
        }
    }

    // ───────────────────────── Filme / série ─────────────────────────────

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
                agendarMiniaturasFilme()
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
            binding.tvLabelModeloFilme.visibility = View.VISIBLE
            binding.scrollModelosFilme.visibility = View.VISIBLE
            binding.btnGerarBanner.visibility = View.VISIBLE

            agendarMiniaturasFilme()
            Toast.makeText(this@GeradorBannerActivity, "Escolha o modelo e toque em Gerar Banner.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun agendarMiniaturasFilme() {
        val poster = posterAtual ?: return
        jobMiniaturasFilme?.cancel()

        val categoria = categoriaSelecionada
        val sinopse = binding.etSinopse.text.toString()
        val frase = binding.etFrase.text.toString()

        jobMiniaturasFilme = lifecycleScope.launch {
            delay(250)
            val estilos = EstiloFilme.values().toList()
            val bitmaps = try {
                withContext(Dispatchers.Default) {
                    estilos.map { BannerComposer.compor(this@GeradorBannerActivity, poster, categoria, sinopse, frase, it, 0.25f) }
                }
            } catch (e: Throwable) {
                null
            } ?: return@launch

            binding.galeriaModelosFilme.removeAllViews()
            miniaturasFilme.clear()

            estilos.forEachIndexed { i, estilo ->
                val img = ImageView(this@GeradorBannerActivity).apply {
                    setImageBitmap(bitmaps[i])
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(dp(3), dp(3), dp(3), dp(3))
                    tag = estilo
                    layoutParams = LinearLayout.LayoutParams(dp(130), dp(168)).apply { marginEnd = dp(10) }
                    setOnClickListener {
                        estiloFilmeSel = estilo
                        atualizarBordasMiniaturasFilme()
                    }
                }
                binding.galeriaModelosFilme.addView(img)
                miniaturasFilme.add(img)
            }
            atualizarBordasMiniaturasFilme()
        }
    }

    private fun atualizarBordasMiniaturasFilme() {
        miniaturasFilme.forEach { view ->
            view.setBackgroundResource(if (view.tag == estiloFilmeSel) R.drawable.bg_card_selecionado else R.drawable.bg_card)
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
        val categoria = categoriaSelecionada
        val estilo = estiloFilmeSel

        binding.btnGerarBanner.text = "Gerando..."
        binding.btnGerarBanner.isEnabled = false

        lifecycleScope.launch {
            val banner = try {
                withContext(Dispatchers.Default) {
                    BannerComposer.compor(this@GeradorBannerActivity, poster, categoria, sinopse, frase, estilo, 1f)
                }
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

    // ───────────────────────── Preview, salvar e compartilhar ────────────

    // Mostra o banner pronto e rola a tela até ele (antes ele aparecia lá
    // embaixo, fora da tela, e parecia que o botão não tinha feito nada).
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
