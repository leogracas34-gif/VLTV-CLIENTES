package com.vltv.clientes.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
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
    DOURADO("Dourado")
}

// Formato final do banner.
enum class FormatoBanner(val rotulo: String, val largura: Int, val altura: Int) {
    STORY("Story 9:16", 1080, 1920),
    FEED("Feed 4:5", 1080, 1350)
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
        val cor: Int,
        val clara: Int,
        val escura: Int,
        val metal: Boolean,
        val glow: Boolean
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
        }
        return bmp
    }

    // Retângulo do pôster em destaque (proporção 2:3), conforme o formato.
    private fun cartaoPoster(w: Float, h: Float, fator: Float = 1f): RectF {
        val story = h > 1500f
        val pwBase = if (story) 640f else 430f
        val topoBase = if (story) 190f else 125f
        val pw = pwBase * fator
        val ph = pw * 1.5f
        val ajusteTopo = pwBase * 1.5f * (1f - fator) / 2f
        val topo = topoBase + ajusteTopo
        return RectF((w - pw) / 2f, topo, (w + pw) / 2f, topo + ph)
    }

    // ═════════════════════════ MODELO 1: CINEMA ═════════════════════════

    private fun estiloCinema(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, d: Dados, w: Float, h: Float) {
        fundoDesfocado(c, poster, w, h)
        c.drawColor(alfa(Color.BLACK, 150))

        // raios de luz saindo do topo
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

        val tema = Tema(cat.cor, cat.corClara, misturar(cat.cor, Color.BLACK, 0.4f), false, false)
        blocoTexto(c, f, w, h, card.bottom + (if (h > 1500f) 60f else 40f), d, tema)
    }

    // ═════════════════════════ MODELO 2: CARTAZ ═════════════════════════

    private fun estiloCartaz(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, d: Dados, w: Float, h: Float) {
        fundoDesfocado(c, poster, w, h)

        // pôster em tela cheia, alinhado ao topo (rostos aparecem)
        val esc = maxOf(w / poster.width, h / poster.height)
        val lw = poster.width * esc
        val lh = poster.height * esc
        val dest = RectF((w - lw) / 2f, 0f, (w + lw) / 2f, lh)
        c.drawBitmap(poster, Rect(0, 0, poster.width, poster.height), dest, Paint(Paint.FILTER_BITMAP_FLAG))

        val topo = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 340f, alfa(Color.BLACK, 185), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, 340f, topo)

        val base = Paint().apply {
            shader = LinearGradient(
                0f, h * 0.36f, 0f, h,
                intArrayOf(Color.TRANSPARENT, alfa(Color.BLACK, 205), alfa(Color.BLACK, 252)),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, h * 0.36f, w, h, base)

        val glow = Paint().apply {
            shader = RadialGradient(w / 2f, h, w * 0.85f, alfa(cat.cor, 110), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, h * 0.5f, w, h, glow)

        seloCategoria(c, cat.rotulo, cat.corClara, cat.cor, f.titulo, 44f, 50f, false)
        logoRedonda(c, f.negrito, w - 74f, 78f)

        val tema = Tema(cat.cor, cat.corClara, misturar(cat.cor, Color.BLACK, 0.4f), false, false)
        blocoTexto(c, f, w, h, h * 0.60f, d, tema)
    }

    // ═════════════════════════ MODELO 3: NEON ═══════════════════════════

    private fun estiloNeon(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, d: Dados, w: Float, h: Float) {
        fundoDesfocado(c, poster, w, h)
        c.drawColor(alfa(Color.BLACK, 155))
        c.drawColor(alfa(cat.cor, 85))

        // linhas de neon decorativas
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

        val tema = Tema(cat.cor, cat.corClara, misturar(cat.cor, Color.BLACK, 0.4f), false, true)
        blocoTexto(c, f, w, h, card.bottom + (if (h > 1500f) 85f else 55f), d, tema)
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

        // selo dourado, centralizado
        seloCategoria(c, cat.rotulo, OURO_CLARO, OURO, f.titulo, w / 2f, if (h > 1500f) 88f else 74f, true)

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

        val tema = Tema(OURO, OURO_CLARO, OURO_ESCURO, true, false)
        blocoTexto(c, f, w, h, card.bottom + (if (h > 1500f) 66f else 46f), d, tema)
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

    // ═════════════════════ BLOCO DE TEXTO (todos os modelos) ═════════════════════
    // meta (FILME • ano) + título grande + traço + sinopse + botão de chamada
    // + rodapé. A sinopse só aparece se sobrar espaço.

    private fun blocoTexto(c: Canvas, f: Fontes, w: Float, h: Float, yTopo: Float, d: Dados, t: Tema) {
        val margem = 70f
        val larg = (w - margem * 2).toInt()
        val ctaTop = h - 215f
        var y = yTopo

        // meta
        if (d.meta.isNotEmpty()) {
            val m = tp(t.clara, 30f, f.negrito, Paint.Align.CENTER, 0.18f)
            c.drawText(d.meta, w / 2f, y + 26f, m)
            y += 54f
        }

        // título (até 2 linhas, o maior tamanho que couber)
        if (d.titulo.isNotEmpty()) {
            val pt = tp(Color.WHITE, 100f, f.titulo, Paint.Align.LEFT, 0.01f)
            if (t.glow) {
                pt.setShadowLayer(28f, 0f, 0f, t.clara)
            } else {
                pt.setShadowLayer(14f, 0f, 6f, alfa(Color.BLACK, 170))
            }
            val lt = layoutTitulo(d.titulo, pt, larg, if (h > 1500f) 150f else 128f)
            if (t.metal) {
                pt.shader = LinearGradient(
                    0f, 0f, 0f, lt.height.toFloat(),
                    intArrayOf(t.clara, t.cor, t.escura, t.cor),
                    floatArrayOf(0f, 0.45f, 0.85f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            c.save()
            c.translate(margem, y)
            lt.draw(c)
            c.restore()
            y += lt.height + 16f
        }

        // traço decorativo
        if (t.metal) {
            val linha = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.cor }
            c.drawRect(w / 2f - 190f, y + 6f, w / 2f - 24f, y + 9f, linha)
            c.drawRect(w / 2f + 24f, y + 6f, w / 2f + 190f, y + 9f, linha)
            losangoEm(c, w / 2f, y + 7.5f, 11f, linha)
        } else {
            val barra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(w / 2f - 80f, 0f, w / 2f + 80f, 0f, t.clara, t.cor, Shader.TileMode.CLAMP)
            }
            c.drawRoundRect(RectF(w / 2f - 80f, y + 3f, w / 2f + 80f, y + 10f), 4f, 4f, barra)
        }
        y += 38f

        // sinopse (só se couber ao menos 2 linhas)
        val disponivel = ctaTop - 30f - y
        val linhas = minOf(3, (disponivel / 44f).toInt())
        if (d.sinopse.isNotEmpty() && linhas >= 2) {
            val sp = tp(cor("#E8E8E8"), 30f, f.corpo, Paint.Align.LEFT)
            val sl = StaticLayout.Builder
                .obtain(d.sinopse, 0, d.sinopse.length, sp, larg)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(linhas)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(4f, 1.05f)
                .build()
            c.save()
            c.translate(margem, y)
            sl.draw(c)
            c.restore()
        }

        botaoChamada(c, f, w, ctaTop, d, t)

        val rodape = tp(cor("#B8BEC9"), 24f, f.corpo, Paint.Align.CENTER, 0.04f)
        val txt = "DISPONÍVEL NA VLTV PLAY  •  vltvplay.tech"
        ajustar(rodape, txt, 940f)
        c.drawText(txt, w / 2f, h - 50f, rodape)
    }

    private fun layoutTitulo(titulo: String, paint: TextPaint, largura: Int, tamMax: Float): StaticLayout {
        var tam = tamMax
        var layout = montarTitulo(titulo, paint, largura, tam)
        while (layout.lineCount > 2 && tam > 56f) {
            tam -= 6f
            layout = montarTitulo(titulo, paint, largura, tam)
        }
        return layout
    }

    private fun montarTitulo(titulo: String, paint: TextPaint, largura: Int, tam: Float): StaticLayout {
        paint.textSize = tam
        return StaticLayout.Builder
            .obtain(titulo, 0, titulo.length, paint, largura)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 0.95f)
            .build()
    }

    // Botão grande "ASSISTA AGORA" com o ícone de play.
    private fun botaoChamada(c: Canvas, f: Fontes, w: Float, topo: Float, d: Dados, t: Tema) {
        val texto = if (d.frase.isEmpty()) "ASSISTA AGORA" else d.frase.uppercase(Locale("pt", "BR"))
        val pill = RectF(w / 2f - 360f, topo, w / 2f + 360f, topo + 98f)
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
                LinearGradient(pill.left, 0f, pill.right, 0f, intArrayOf(t.cor, t.clara, t.cor), null, Shader.TileMode.CLAMP)
            } else {
                LinearGradient(0f, pill.top, 0f, pill.bottom, t.clara, t.cor, Shader.TileMode.CLAMP)
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
        val corTexto = if (t.metal) cor("#1A1208") else Color.WHITE
        val bx = pill.left + 62f
        val by = pill.centerY()
        val bolinha = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (t.metal) cor("#1A1208") else Color.WHITE }
        c.drawCircle(bx, by, 36f, bolinha)
        val play = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (t.metal) t.clara else t.cor }
        val seta = Path().apply {
            moveTo(bx - 11f, by - 18f)
            lineTo(bx + 19f, by)
            lineTo(bx - 11f, by + 18f)
            close()
        }
        c.drawPath(seta, play)

        val pt = tp(corTexto, 56f, f.titulo, Paint.Align.CENTER, 0.08f)
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

    private fun seloCategoria(c: Canvas, rotulo: String, corClara: Int, corBase: Int, fonte: Typeface, x: Float, y: Float, centro: Boolean) {
        val textoPaint = tp(Color.WHITE, 42f, fonte, Paint.Align.LEFT, 0.06f)
        val larguraTexto = textoPaint.measureText(rotulo)
        val padH = 32f
        val padV = 14f
        val esquerda = if (centro) x - (larguraTexto + padH * 2) / 2f else x
        val fundo = RectF(esquerda, y, esquerda + larguraTexto + padH * 2, y + textoPaint.textSize + padV * 2)
        val ouro = corBase == OURO

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

        if (ouro) textoPaint.color = cor("#1A1208")
        c.drawText(rotulo, fundo.left + padH, fundo.top + padV + textoPaint.textSize - 10f, textoPaint)
    }

    // Ícone do app (círculo vermelho + play) com "VLTV PLAY" embaixo.
    private fun logoRedonda(c: Canvas, fonteNome: Typeface, cx: Float, cy: Float) {
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
        val nome = tp(Color.WHITE, 22f, Typeface.create(fonteNome, Typeface.BOLD), Paint.Align.CENTER, 0.04f)
        nome.setShadowLayer(6f, 0f, 2f, cor("#AA000000"))
        c.drawText("VLTV PLAY", cx, cy + raio + 30f, nome)
    }

    private fun tp(cor: Int, tamanho: Float, fonte: Typeface, align: Paint.Align = Paint.Align.LEFT, espaco: Float = 0f): TextPaint {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.color = cor
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
