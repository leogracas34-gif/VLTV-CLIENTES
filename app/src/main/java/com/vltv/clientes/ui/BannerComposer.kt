package com.vltv.clientes.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.vltv.clientes.R
import java.util.Locale

private fun cor(hex: String): Int = Color.parseColor(hex)

private fun alfa(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

// Selo de destaque do banner - cada um com sua cor.
enum class CategoriaBanner(val rotulo: String, val cor: Int, val corClara: Int) {
    DESTAQUE_SEMANA("DESTAQUE DA SEMANA", cor("#E50914"), cor("#FF4655")),
    TOP10_BRASIL("TOP 10 BRASIL", cor("#00A859"), cor("#2ED37A")),
    TOP10_MUNDO("TOP 10 MUNDO", cor("#1565C0"), cor("#3B8DE8")),
    LANCAMENTO("LANÇAMENTO", cor("#F9A825"), cor("#FFC24D"))
}

// Modelos (layouts) do banner.
enum class EstiloFilme(val rotulo: String) {
    CINEMA("Cinema"),
    CARTAZ("Cartaz"),
    NEON("Neon"),
    DOURADO("Dourado"),
    PREMIERE("Estreia"),
    CLEAN("Claro"),
    VIBRANTE("Vibrante")
}

// Formato final do banner.
enum class FormatoBanner(val rotulo: String, val largura: Int, val altura: Int) {
    STORY("Story 9:16", 1080, 1920),
    FEED("Feed 4:5", 1080, 1350),
    QUADRADO("Quadrado 1:1", 1080, 1080)
}

// Desenha os banners direto no Canvas (sem depender de internet nem de app
// externo). "escala" = 1f gera o banner final; 0.2f gera as miniaturas da
// galeria de modelos.
object BannerComposer {

    private class Fontes(val titulo: Typeface, val corpo: Typeface, val negrito: Typeface)

    private class Dados(
        val titulo: String,
        val meta: String,
        val sinopse: String,
        val frase: String
    )

    // Cores do tema do banner. metal = textos/botões em degradê dourado;
    // glow = título com brilho neon.
    private class Tema(
        val base: Int,
        val clara: Int,
        val escura: Int,
        val metal: Boolean = false,
        val glow: Boolean = false,
        val titulo: Int = Color.WHITE,
        val texto: Int = Color.parseColor("#E8E8E8"),
        val rodape: Int = Color.parseColor("#B8BEC9"),
        val meta: Int = clara,
        val textoCta: Int = if (metal) Color.parseColor("#1A1208") else Color.WHITE,
        val bolinha: Int = if (metal) Color.parseColor("#1A1208") else Color.WHITE,
        val playCor: Int = if (metal) clara else base,
        val sombraTitulo: Boolean = true
    )

    // Onde o bloco de texto fica: centralizado embaixo (Story/Feed) ou numa
    // coluna ao lado do pôster (Quadrado).
    private class ZonaTexto(
        val x: Float,
        val larg: Float,
        val centro: Boolean,
        val ctaTop: Float,
        val ctaLarg: Float,
        val tituloMax: Float,
        val maxLinhas: Int
    )

    private val OURO_CLARO = cor("#F7E3A1")
    private val OURO = cor("#D4AF37")
    private val OURO_ESCURO = cor("#8A6A12")

    private fun carregarFontes(context: Context): Fontes {
        val titulo = ResourcesCompat.getFont(context, R.font.bebas_neue_regular) ?: Typeface.DEFAULT_BOLD
        val corpo = ResourcesCompat.getFont(context, R.font.poppins_regular) ?: Typeface.DEFAULT
        val negrito = ResourcesCompat.getFont(context, R.font.poppins_semibold) ?: Typeface.DEFAULT_BOLD
        return Fontes(titulo, corpo, negrito)
    }

    fun compor(
        context: Context,
        poster: Bitmap,
        categoria: CategoriaBanner,
        titulo: String,
        tipo: String,
        ano: String,
        sinopse: String,
        fraseChamada: String,
        estilo: EstiloFilme,
        formato: FormatoBanner,
        escala: Float = 1f
    ): Bitmap {
        val f = carregarFontes(context)
        val w = formato.largura.toFloat()
        val h = formato.altura.toFloat()

        val bmp = Bitmap.createBitmap(
            (w * escala).toInt().coerceAtLeast(1),
            (h * escala).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val c = Canvas(bmp)
        c.scale(escala, escala)

        val tipoRotulo = if (tipo == "filme") "FILME" else "SÉRIE"
        val meta = listOf(tipoRotulo, ano.trim()).filter { it.isNotEmpty() }.joinToString("  •  ")
        val dados = Dados(
            titulo.trim().uppercase(Locale("pt", "BR")),
            meta,
            sinopse.trim(),
            fraseChamada.trim()
        )

        when (estilo) {
            EstiloFilme.CINEMA -> estiloCinema(c, f, poster, categoria, dados, w, h)
            EstiloFilme.CARTAZ -> estiloCartaz(c, f, poster, categoria, dados, w, h)
            EstiloFilme.NEON -> estiloNeon(c, f, poster, categoria, dados, w, h)
            EstiloFilme.DOURADO -> estiloDourado(c, f, poster, categoria, dados, w, h)
            EstiloFilme.PREMIERE -> estiloPremiere(c, f, poster, categoria, dados, w, h)
            EstiloFilme.CLEAN -> estiloClean(c, f, poster, categoria, dados, w, h)
            EstiloFilme.VIBRANTE -> estiloVibrante(c, f, poster, categoria, dados, w, h)
        }
        return bmp
    }

    private fun lateral(h: Float): Boolean = h < 1200f

    // Retângulo do pôster em destaque (proporção 2:3), conforme o formato.
    // No Quadrado ele fica à esquerda e o texto numa coluna à direita.
    private fun cartaoPoster(w: Float, h: Float, fator: Float = 1f): RectF {
        val base = when {
            h > 1500f -> RectF((w - 640f) / 2f, 190f, (w + 640f) / 2f, 190f + 960f)
            h > 1200f -> RectF((w - 430f) / 2f, 125f, (w + 430f) / 2f, 125f + 645f)
            else -> RectF(70f, 235f, 470f, 835f)
        }
        val cx = base.centerX()
        val cy = base.centerY()
        val pw = base.width() * fator
        val ph = base.height() * fator
        return RectF(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f)
    }

    private fun zonaPadrao(w: Float, h: Float): ZonaTexto =
        ZonaTexto(70f, w - 140f, true, h - 215f, 720f, if (h > 1500f) 150f else 128f, 2)

    private fun zonaDoCartao(w: Float, h: Float, card: RectF, folga: Float): ZonaTexto {
        if (!lateral(h)) return zonaPadrao(w, h)
        val x = card.right + folga
        val larg = w - 60f - x
        return ZonaTexto(x, larg, false, card.bottom - 98f, larg, 112f, 3)
    }

    private fun yDoTexto(h: Float, card: RectF, folgaVertical: Float): Float =
        if (lateral(h)) card.top + 4f else card.bottom + folgaVertical

    // ═════════════════════════ MODELO 1: CINEMA ═════════════════════════

    private fun estiloCinema(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, d: Dados, w: Float, h: Float) {
        fundoDesfocado(c, poster, w, h)
        c.drawColor(alfa(Color.BLACK, 150))
        raiosDeLuz(c, w, h)

        val vinheta = Paint().apply {
            shader = RadialGradient(w / 2f, h * 0.42f, h * 0.8f, Color.TRANSPARENT, alfa(Color.BLACK, 215), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, vinheta)

        val card = cartaoPoster(w, h)
        val brilho = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = alfa(cat.cor, 170)
            maskFilter = BlurMaskFilter(46f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(card.left - 16f, card.top - 16f, card.right + 16f, card.bottom + 16f), 40f, 40f, brilho)
        posterArredondado(c, poster, card, 28f)
        val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = alfa(Color.WHITE, 90)
        }
        c.drawRoundRect(card, 28f, 28f, borda)

        seloCategoria(c, cat.rotulo, cat.corClara, cat.cor, f.titulo, 44f, 50f, false)
        logoRedonda(c, f.negrito, w - 74f, 78f)

        val tema = Tema(base = cat.cor, clara = cat.corClara, escura = misturar(cat.cor, Color.BLACK, 0.4f))
        blocoTexto(c, f, w, h, yDoTexto(h, card, if (h > 1500f) 60f else 40f), d, tema, zonaDoCartao(w, h, card, 56f))
    }

    private fun raiosDeLuz(c: Canvas, w: Float, h: Float) {
        val luz = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(w / 2f, 0f, w / 2f, h * 0.75f, alfa(Color.WHITE, 46), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        val raios = listOf(Pair(-760f, -380f), Pair(-130f, 130f), Pair(380f, 760f))
        raios.forEach { (a, b) ->
            val path = Path().apply {
                moveTo(w / 2f - 24f, -20f)
                lineTo(w / 2f + 24f, -20f)
                lineTo(w / 2f + b, h * 0.8f)
                lineTo(w / 2f + a, h * 0.8f)
                close()
            }
            c.drawPath(path, luz)
        }
    }

    // ═════════════════════════ MODELO 2: CARTAZ ═════════════════════════

    private fun estiloCartaz(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, d: Dados, w: Float, h: Float) {
        fundoDesfocado(c, poster, w, h)
        posterTelaCheia(c, poster, w, h, null)

        val topo = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 340f, alfa(Color.BLACK, 185), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, 340f, topo)

        val base = Paint().apply {
            shader = LinearGradient(
                0f, h * 0.30f, 0f, h,
                intArrayOf(Color.TRANSPARENT, alfa(Color.BLACK, 205), alfa(Color.BLACK, 252)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, h * 0.30f, w, h, base)

        val glow = Paint().apply {
            shader = RadialGradient(w / 2f, h, w * 0.85f, alfa(cat.cor, 110), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, h * 0.5f, w, h, glow)

        seloCategoria(c, cat.rotulo, cat.corClara, cat.cor, f.titulo, 44f, 50f, false)
        logoRedonda(c, f.negrito, w - 74f, 78f)

        val tema = Tema(base = cat.cor, clara = cat.corClara, escura = misturar(cat.cor, Color.BLACK, 0.4f))
        val yTopo = if (lateral(h)) h * 0.46f else h * 0.60f
        blocoTexto(c, f, w, h, yTopo, d, tema, zonaPadrao(w, h))
    }

    // Pôster cobrindo a tela toda, alinhado ao topo (rostos aparecem).
    private fun posterTelaCheia(c: Canvas, poster: Bitmap, w: Float, h: Float, filtro: Paint?) {
        val esc = maxOf(w / poster.width, h / poster.height)
        val lw = poster.width * esc
        val lh = poster.height * esc
        val dest = RectF((w - lw) / 2f, 0f, (w + lw) / 2f, lh)
        val paint = filtro ?: Paint(Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(poster, Rect(0, 0, poster.width, poster.height), dest, paint)
    }

    // ═════════════════════════ MODELO 3: NEON ═══════════════════════════

    private fun estiloNeon(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, d: Dados, w: Float, h: Float) {
        fundoDesfocado(c, poster, w, h)
        c.drawColor(alfa(Color.BLACK, 155))
        c.drawColor(alfa(cat.cor, 85))

        linhaNeon(c, -60f, h * 0.20f, 420f, -40f, cat.corClara)
        linhaNeon(c, -60f, h * 0.26f, 560f, -40f, cat.corClara)
        linhaNeon(c, w + 60f, h * 0.60f, w - 460f, h * 0.90f, cat.corClara)
        linhaNeon(c, w + 60f, h * 0.66f, w - 560f, h * 0.96f, cat.corClara)

        val card = cartaoPoster(w, h, 0.93f)
        val cx = card.centerX()
        val cy = card.centerY()

        // cartão de trás (inclinado e escurecido)
        c.save()
        c.rotate(7f, cx, cy)
        posterArredondado(c, poster, card, 26f)
        val escuro = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alfa(Color.BLACK, 125) }
        c.drawRoundRect(card, 26f, 26f, escuro)
        val bordaTras = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = alfa(cat.corClara, 160)
        }
        c.drawRoundRect(card, 26f, 26f, bordaTras)
        c.restore()

        // cartão da frente com borda neon
        c.save()
        c.rotate(-4f, cx, cy)
        val neonLargo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 18f
            color = cat.corClara
            maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(card, 26f, 26f, neonLargo)
        posterArredondado(c, poster, card, 26f)
        val neonFino = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = Color.WHITE
        }
        c.drawRoundRect(card, 26f, 26f, neonFino)
        c.restore()

        seloCategoria(c, cat.rotulo, cat.corClara, cat.cor, f.titulo, 44f, 50f, false)
        logoRedonda(c, f.negrito, w - 74f, 78f)

        val tema = Tema(base = cat.cor, clara = cat.corClara, escura = misturar(cat.cor, Color.BLACK, 0.4f), glow = true)
        blocoTexto(c, f, w, h, yDoTexto(h, card, if (h > 1500f) 85f else 55f), d, tema, zonaDoCartao(w, h, card, 84f))
    }

    private fun linhaNeon(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, corNeon: Int) {
        val brilho = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = corNeon
            strokeWidth = 16f
            maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawLine(x1, y1, x2, y2, brilho)
        val miolo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 230
            strokeWidth = 3.5f
        }
        c.drawLine(x1, y1, x2, y2, miolo)
    }

    // ═════════════════════════ MODELO 4: DOURADO ════════════════════════

    private fun estiloDourado(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, d: Dados, w: Float, h: Float) {
        fundoDesfocado(c, poster, w, h)
        c.drawColor(alfa(cor("#0A0806"), 222))
        c.drawColor(alfa(OURO, 22))

        val brilho = Paint().apply {
            shader = RadialGradient(w / 2f, h * 0.30f, w * 0.9f, alfa(OURO, 70), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, brilho)

        // moldura dupla dourada com losangos nos cantos
        val ouro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            shader = LinearGradient(0f, 0f, w, h, OURO_CLARO, OURO_ESCURO, Shader.TileMode.CLAMP)
        }
        c.drawRect(RectF(34f, 34f, w - 34f, h - 34f), ouro)
        ouro.strokeWidth = 1.5f
        c.drawRect(RectF(52f, 52f, w - 52f, h - 52f), ouro)
        val losango = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OURO }
        listOf(
            Pair(34f, 34f), Pair(w - 34f, 34f), Pair(34f, h - 34f), Pair(w - 34f, h - 34f)
        ).forEach { (x, y) -> losangoEm(c, x, y, 13f, losango) }

        seloCategoria(c, cat.rotulo, OURO_CLARO, OURO, f.titulo, w / 2f, if (h > 1500f) 88f else 74f, true, cor("#1A1208"))

        val card = cartaoPoster(w, h, if (h > 1500f) 0.97f else 0.93f)
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 200
            maskFilter = BlurMaskFilter(34f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRect(RectF(card.left - 4f, card.top + 14f, card.right + 4f, card.bottom + 22f), sombra)
        val moldura = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(card.left, card.top, card.right, card.bottom, OURO_CLARO, OURO_ESCURO, Shader.TileMode.CLAMP)
        }
        c.drawRect(RectF(card.left - 9f, card.top - 9f, card.right + 9f, card.bottom + 9f), moldura)
        posterArredondado(c, poster, card, 0f)

        val tema = Tema(base = OURO, clara = OURO_CLARO, escura = OURO_ESCURO, metal = true)
        blocoTexto(c, f, w, h, yDoTexto(h, card, if (h > 1500f) 66f else 46f), d, tema, zonaDoCartao(w, h, card, 60f))
    }

    private fun losangoEm(c: Canvas, x: Float, y: Float, r: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(x, y - r)
            lineTo(x + r, y)
            lineTo(x, y + r)
            lineTo(x - r, y)
            close()
        }
        c.drawPath(path, paint)
    }

    // ═════════════════════════ MODELO 5: ESTREIA ════════════════════════
    // Cortinas vermelhas de cinema, holofote e pôster com moldura branca.

    private fun estiloPremiere(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, d: Dados, w: Float, h: Float) {
        val fundo = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, cor("#2E0509"), cor("#090102"), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, fundo)

        val luzTopo = Paint().apply {
            shader = RadialGradient(w / 2f, 0f, w * 0.95f, alfa(cor("#FF4B4B"), 120), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, luzTopo)

        // cortinas nas laterais (faixas verticais)
        val larguraFaixa = w * 0.035f
        for (i in 0 until 5) {
            val faixa = Paint().apply { color = alfa(cor("#C1121F"), if (i % 2 == 0) 95 else 32) }
            c.drawRect(i * larguraFaixa, 0f, (i + 1) * larguraFaixa, h, faixa)
            c.drawRect(w - (i + 1) * larguraFaixa, 0f, w - i * larguraFaixa, h, faixa)
        }

        // holofote
        val cone = Path().apply {
            moveTo(w / 2f - 30f, -20f)
            lineTo(w / 2f + 30f, -20f)
            lineTo(w / 2f + w * 0.55f, h * 0.85f)
            lineTo(w / 2f - w * 0.55f, h * 0.85f)
            close()
        }
        val luz = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(w / 2f, 0f, w / 2f, h * 0.85f, alfa(Color.WHITE, 70), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawPath(cone, luz)

        val card = cartaoPoster(w, h)
        val brilho = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = alfa(cor("#FF2D3B"), 150)
            maskFilter = BlurMaskFilter(44f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(card.left - 14f, card.top - 14f, card.right + 14f, card.bottom + 14f), 30f, 30f, brilho)
        val moldura = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawRoundRect(RectF(card.left - 11f, card.top - 11f, card.right + 11f, card.bottom + 11f), 14f, 14f, moldura)
        posterArredondado(c, poster, card, 6f)

        seloCategoria(c, cat.rotulo, cat.corClara, cat.cor, f.titulo, 76f, 50f, false)
        logoRedonda(c, f.negrito, w - 112f, 78f)

        val tema = Tema(base = cat.cor, clara = cat.corClara, escura = misturar(cat.cor, Color.BLACK, 0.4f))
        blocoTexto(c, f, w, h, yDoTexto(h, card, if (h > 1500f) 62f else 42f), d, tema, zonaDoCartao(w, h, card, 58f))
    }

    // ═════════════════════════ MODELO 6: CLARO ══════════════════════════
    // Fundo claro com a cor do pôster, cartão branco e texto escuro.

    private fun estiloClean(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, d: Dados, w: Float, h: Float) {
        val media = corMedia(poster)
        c.drawColor(misturar(media, Color.WHITE, 0.86f))

        val blob = Paint(Paint.ANTI_ALIAS_FLAG)
        blob.color = misturar(media, Color.WHITE, 0.55f)
        c.drawCircle(w * 0.88f, h * 0.10f, 400f, blob)
        blob.color = misturar(media, Color.WHITE, 0.70f)
        c.drawCircle(w * 0.06f, h * 0.72f, 320f, blob)

        val card = cartaoPoster(w, h)
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 95
            maskFilter = BlurMaskFilter(38f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(card.left, card.top + 26f, card.right, card.bottom + 30f), 34f, 34f, sombra)
        val moldura = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawRoundRect(RectF(card.left - 12f, card.top - 12f, card.right + 12f, card.bottom + 12f), 36f, 36f, moldura)
        posterArredondado(c, poster, card, 26f)

        seloCategoria(c, cat.rotulo, cat.corClara, cat.cor, f.titulo, 44f, 50f, false)
        logoRedonda(c, f.negrito, w - 74f, 78f, cor("#14161C"))

        val tema = Tema(
            base = cat.cor,
            clara = cat.corClara,
            escura = misturar(cat.cor, Color.BLACK, 0.4f),
            titulo = cor("#14161C"),
            texto = cor("#3A3F4B"),
            rodape = cor("#5F6673"),
            meta = misturar(cat.cor, Color.BLACK, 0.15f),
            sombraTitulo = false
        )
        blocoTexto(c, f, w, h, yDoTexto(h, card, if (h > 1500f) 62f else 44f), d, tema, zonaDoCartao(w, h, card, 58f))
    }

    // ═════════════════════════ MODELO 7: VIBRANTE ═══════════════════════
    // Fundo duotone na cor do selo e pôster estilo foto (polaroid) inclinado.

    private fun estiloVibrante(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, d: Dados, w: Float, h: Float) {
        val cinza = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        c.drawColor(Color.BLACK)
        posterTelaCheia(c, poster, w, h, cinza)
        c.drawColor(alfa(cat.cor, 190))

        val baixo = Paint().apply {
            shader = LinearGradient(0f, h * 0.38f, 0f, h, Color.TRANSPARENT, alfa(Color.BLACK, 235), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, h * 0.38f, w, h, baixo)

        val card = cartaoPoster(w, h, 0.9f)
        val cx = card.centerX()
        val cy = card.centerY()
        c.save()
        c.rotate(-3f, cx, cy)
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 150
            maskFilter = BlurMaskFilter(34f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRect(RectF(card.left - 8f, card.top + 22f, card.right + 8f, card.bottom + 34f), sombra)
        val foto = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawRoundRect(RectF(card.left - 15f, card.top - 15f, card.right + 15f, card.bottom + 15f), 10f, 10f, foto)
        posterArredondado(c, poster, card, 4f)
        c.restore()

        seloCategoria(c, cat.rotulo, Color.WHITE, cor("#EEEEEE"), f.titulo, 44f, 50f, false, cat.cor)
        logoRedonda(c, f.negrito, w - 74f, 78f)

        val tema = Tema(
            base = cor("#F2F2F2"),
            clara = Color.WHITE,
            escura = cor("#D0D0D0"),
            meta = Color.WHITE,
            textoCta = cat.cor,
            bolinha = cat.cor,
            playCor = Color.WHITE
        )
        blocoTexto(c, f, w, h, yDoTexto(h, card, if (h > 1500f) 74f else 52f), d, tema, zonaDoCartao(w, h, card, 60f))
    }

    // ═════════════════════ BLOCO DE TEXTO (todos os modelos) ═════════════════════
    // meta (FILME • ano) + título grande + traço + sinopse + botão de chamada
    // + rodapé. A sinopse só aparece se sobrar espaço.

    private fun blocoTexto(c: Canvas, f: Fontes, w: Float, h: Float, yTopo: Float, d: Dados, t: Tema, z: ZonaTexto) {
        val alinhar = if (z.centro) Paint.Align.CENTER else Paint.Align.LEFT
        val alin = if (z.centro) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL
        val ancora = if (z.centro) z.x + z.larg / 2f else z.x
        val larg = z.larg.toInt()
        var y = yTopo

        // meta
        if (d.meta.isNotEmpty()) {
            val m = tp(t.meta, 30f, f.negrito, alinhar, 0.18f)
            c.drawText(d.meta, ancora, y + 26f, m)
            y += 54f
        }

        // título (o maior tamanho que couber, até z.maxLinhas linhas)
        if (d.titulo.isNotEmpty()) {
            val pt = tp(t.titulo, 100f, f.titulo, Paint.Align.LEFT, 0.01f)
            if (t.glow) {
                pt.setShadowLayer(28f, 0f, 0f, t.clara)
            } else if (t.sombraTitulo) {
                pt.setShadowLayer(14f, 0f, 6f, alfa(Color.BLACK, 170))
            }
            val alturaMax = z.ctaTop - 30f - 54f - y
            val lt = layoutTitulo(d.titulo, pt, larg, z.tituloMax, z.maxLinhas, alturaMax, alin)
            if (t.metal) {
                pt.shader = LinearGradient(
                    0f, 0f, 0f, lt.height.toFloat(),
                    intArrayOf(t.clara, t.base, t.escura, t.base),
                    floatArrayOf(0f, 0.45f, 0.85f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            c.save()
            c.translate(z.x, y)
            lt.draw(c)
            c.restore()
            y += lt.height + 16f
        }

        // traço decorativo
        if (t.metal) {
            val linha = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.base }
            if (z.centro) {
                c.drawRect(ancora - 190f, y + 6f, ancora - 24f, y + 9f, linha)
                c.drawRect(ancora + 24f, y + 6f, ancora + 190f, y + 9f, linha)
                losangoEm(c, ancora, y + 7.5f, 11f, linha)
            } else {
                c.drawRect(z.x, y + 6f, z.x + 170f, y + 9f, linha)
                losangoEm(c, z.x + 192f, y + 7.5f, 11f, linha)
            }
        } else {
            val x0 = if (z.centro) ancora - 80f else z.x
            val barra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(x0, 0f, x0 + 160f, 0f, t.clara, t.base, Shader.TileMode.CLAMP)
            }
            c.drawRoundRect(RectF(x0, y + 3f, x0 + 160f, y + 10f), 4f, 4f, barra)
        }
        y += 38f

        // sinopse (só se couber ao menos 2 linhas)
        val disponivel = z.ctaTop - 30f - y
        val linhas = minOf(3, (disponivel / 44f).toInt())
        if (d.sinopse.isNotEmpty() && linhas >= 2) {
            val sp = tp(t.texto, 30f, f.corpo, Paint.Align.LEFT)
            val sl = StaticLayout.Builder
                .obtain(d.sinopse, 0, d.sinopse.length, sp, larg)
                .setAlignment(alin)
                .setMaxLines(linhas)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(4f, 1.05f)
                .build()
            c.save()
            c.translate(z.x, y)
            sl.draw(c)
            c.restore()
        }

        botaoChamada(c, f, d, t, z)

        val rodape = tp(t.rodape, 24f, f.corpo, Paint.Align.CENTER, 0.04f)
        val txt = "DISPONÍVEL NA VLTV PLAY  •  vltvplay.tech"
        ajustar(rodape, txt, 940f)
        c.drawText(txt, w / 2f, h - 50f, rodape)
    }

    private fun layoutTitulo(
        titulo: String,
        paint: TextPaint,
        largura: Int,
        tamMax: Float,
        maxLinhas: Int,
        alturaMax: Float,
        alin: Layout.Alignment
    ): StaticLayout {
        var tam = tamMax
        var layout = montarTitulo(titulo, paint, largura, tam, alin)
        while ((layout.lineCount > maxLinhas || layout.height > alturaMax) && tam > 48f) {
            tam -= 6f
            layout = montarTitulo(titulo, paint, largura, tam, alin)
        }
        return layout
    }

    private fun montarTitulo(titulo: String, paint: TextPaint, largura: Int, tam: Float, alin: Layout.Alignment): StaticLayout {
        paint.textSize = tam
        return StaticLayout.Builder
            .obtain(titulo, 0, titulo.length, paint, largura)
            .setAlignment(alin)
            .setLineSpacing(0f, 0.95f)
            .build()
    }

    // Botão grande "ASSISTA AGORA" com o ícone de play.
    private fun botaoChamada(c: Canvas, f: Fontes, d: Dados, t: Tema, z: ZonaTexto) {
        val texto = if (d.frase.isEmpty()) "ASSISTA AGORA" else d.frase.uppercase(Locale("pt", "BR"))
        val esquerda = if (z.centro) z.x + z.larg / 2f - z.ctaLarg / 2f else z.x
        val pill = RectF(esquerda, z.ctaTop, esquerda + z.ctaLarg, z.ctaTop + 98f)
        val raio = pill.height() / 2f

        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 150
            maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(pill.left, pill.top + 8f, pill.right, pill.bottom + 12f), raio, raio, sombra)

        if (t.glow) {
            val neon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = t.clara
                alpha = 190
                maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawRoundRect(pill, raio, raio, neon)
        }

        val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = if (t.metal) {
                LinearGradient(pill.left, 0f, pill.right, 0f, intArrayOf(t.base, t.clara, t.base), null, Shader.TileMode.CLAMP)
            } else {
                LinearGradient(0f, pill.top, 0f, pill.bottom, t.clara, t.base, Shader.TileMode.CLAMP)
            }
        }
        c.drawRoundRect(pill, raio, raio, fundo)

        val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.WHITE
            alpha = 100
        }
        c.drawRoundRect(pill, raio, raio, borda)

        // bolinha com play
        val bx = pill.left + 62f
        val by = pill.centerY()
        val bolinha = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.bolinha }
        c.drawCircle(bx, by, 36f, bolinha)
        val play = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.playCor }
        val seta = Path().apply {
            moveTo(bx - 11f, by - 18f)
            lineTo(bx + 19f, by)
            lineTo(bx - 11f, by + 18f)
            close()
        }
        c.drawPath(seta, play)

        val pt = tp(t.textoCta, 56f, f.titulo, Paint.Align.CENTER, 0.08f)
        ajustar(pt, texto, pill.width() - 190f)
        c.drawText(texto, pill.centerX() + 34f, pill.centerY() + pt.textSize * 0.35f, pt)
    }

    // ═════════════════════════ PEÇAS COMPARTILHADAS ═════════════════════════

    // Fundo: o próprio pôster reduzido e esticado (vira um desfoque suave).
    private fun fundoDesfocado(c: Canvas, poster: Bitmap, w: Float, h: Float) {
        val lp = 24
        val ap = (lp.toFloat() * poster.height / poster.width).toInt().coerceAtLeast(1)
        val pequeno = Bitmap.createScaledBitmap(poster, lp, ap, true)
        val escala = maxOf(w / pequeno.width, h / pequeno.height)
        val lw = pequeno.width * escala
        val lh = pequeno.height * escala
        val destino = RectF((w - lw) / 2f, (h - lh) / 2f, (w + lw) / 2f, (h + lh) / 2f)
        c.drawBitmap(pequeno, Rect(0, 0, pequeno.width, pequeno.height), destino, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    // Cor média do pôster (usada no fundo do modelo Claro).
    private fun corMedia(poster: Bitmap): Int {
        val unico = Bitmap.createScaledBitmap(poster, 1, 1, true)
        val px = unico.getPixel(0, 0)
        return Color.rgb(Color.red(px), Color.green(px), Color.blue(px))
    }

    // Desenha o pôster preenchendo o retângulo (corta o excesso), cantos arredondados.
    private fun posterArredondado(c: Canvas, poster: Bitmap, r: RectF, raio: Float) {
        val escala = maxOf(r.width() / poster.width, r.height() / poster.height)
        val lw = poster.width * escala
        val lh = poster.height * escala
        val destino = RectF(r.centerX() - lw / 2f, r.centerY() - lh / 2f, r.centerX() + lw / 2f, r.centerY() + lh / 2f)
        c.save()
        val path = Path()
        path.addRoundRect(r, raio, raio, Path.Direction.CW)
        c.clipPath(path)
        c.drawBitmap(poster, Rect(0, 0, poster.width, poster.height), destino, Paint(Paint.FILTER_BITMAP_FLAG))
        c.restore()
    }

    private fun seloCategoria(
        c: Canvas,
        rotulo: String,
        corClara: Int,
        corBase: Int,
        fonte: Typeface,
        x: Float,
        y: Float,
        centro: Boolean,
        corTexto: Int = Color.WHITE
    ) {
        val textoPaint = tp(corTexto, 42f, fonte, Paint.Align.LEFT, 0.06f)
        val larguraTexto = textoPaint.measureText(rotulo)
        val padH = 32f
        val padV = 14f
        val esquerda = if (centro) x - (larguraTexto + padH * 2) / 2f else x
        val fundo = RectF(esquerda, y, esquerda + larguraTexto + padH * 2, y + textoPaint.textSize + padV * 2)

        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 120
            maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(fundo.left, fundo.top + 6f, fundo.right, fundo.bottom + 10f), 14f, 14f, sombra)

        val fundoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(fundo.left, fundo.top, fundo.left, fundo.bottom, corClara, corBase, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(fundo, 14f, 14f, fundoPaint)

        val brilho = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 80
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawRoundRect(RectF(fundo.left + 1f, fundo.top + 1f, fundo.right - 1f, fundo.bottom - 1f), 13f, 13f, brilho)

        c.drawText(rotulo, fundo.left + padH, fundo.top + padV + textoPaint.textSize - 10f, textoPaint)
    }

    // Ícone do app (círculo vermelho + play) com "VLTV PLAY" embaixo.
    private fun logoRedonda(c: Canvas, fonteNome: Typeface, cx: Float, cy: Float, corNome: Int = Color.WHITE) {
        val raio = 42f
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 100
            maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawCircle(cx, cy + 3f, raio, sombra)
        val circulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor("#E50914") }
        c.drawCircle(cx, cy, raio, circulo)
        val play = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val path = Path().apply {
            moveTo(cx - 13f, cy - 19f)
            lineTo(cx + 19f, cy)
            lineTo(cx - 13f, cy + 19f)
            close()
        }
        c.drawPath(path, play)
        val nome = tp(corNome, 22f, Typeface.create(fonteNome, Typeface.BOLD), Paint.Align.CENTER, 0.04f)
        if (corNome == Color.WHITE) nome.setShadowLayer(6f, 0f, 2f, cor("#AA000000"))
        c.drawText("VLTV PLAY", cx, cy + raio + 30f, nome)
    }

    private fun tp(corTxt: Int, tamanho: Float, fonte: Typeface, align: Paint.Align = Paint.Align.LEFT, espaco: Float = 0f): TextPaint {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.color = corTxt
        paint.textSize = tamanho
        paint.typeface = fonte
        paint.textAlign = align
        paint.letterSpacing = espaco
        return paint
    }

    private fun ajustar(paint: TextPaint, texto: String, larguraMax: Float) {
        while (paint.measureText(texto) > larguraMax && paint.textSize > 12f) {
            paint.textSize = paint.textSize - 2f
        }
    }

    private fun misturar(a: Int, b: Int, t: Float): Int {
        val r = Color.red(a) + ((Color.red(b) - Color.red(a)) * t).toInt()
        val g = Color.green(a) + ((Color.green(b) - Color.green(a)) * t).toInt()
        val bl = Color.blue(a) + ((Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.rgb(r, g, bl)
    }
}
