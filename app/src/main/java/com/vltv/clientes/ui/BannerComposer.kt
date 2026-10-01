package com.vltv.clientes.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.vltv.clientes.R
import com.vltv.clientes.data.PlanoCliente
import java.text.NumberFormat
import java.util.Locale

// As 4 opções fixas de "selo" de destaque do banner de filme/série - cada
// uma com sua própria cor, com uma versão clara pro degradê do selo.
enum class CategoriaBanner(val rotulo: String, val cor: Int, val corClara: Int) {
    DESTAQUE_SEMANA("DESTAQUE DA SEMANA", Color.parseColor("#E50914"), Color.parseColor("#FF4655")),
    TOP10_BRASIL("TOP 10 BRASIL", Color.parseColor("#00A859"), Color.parseColor("#2ED37A")),
    TOP10_MUNDO("TOP 10 MUNDO", Color.parseColor("#1565C0"), Color.parseColor("#3B8DE8")),
    LANCAMENTO("LANÇAMENTO", Color.parseColor("#F9A825"), Color.parseColor("#FFC24D"))
}

// Monta banners promocionais em duas modalidades: 1) filme/série, com o
// pôster oficial do TMDB de fundo (que já traz o título na própria arte,
// por isso nunca repetimos o nome em texto) e 2) preços/planos, com um
// fundo estilizado e 4 cards - sem depender de nenhuma imagem externa.
object BannerComposer {

    private const val LARGURA = 1080
    private const val ALTURA = 1350

    // ───────────────────────── Banner de filme/série ─────────────────────

    fun compor(
        context: Context,
        posterOriginal: Bitmap,
        categoria: CategoriaBanner,
        sinopse: String,
        fraseChamada: String
    ): Bitmap {
        val fontes = carregarFontes(context)

        val resultado = Bitmap.createBitmap(LARGURA, ALTURA, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultado)

        desenharPosterCobrindoTudo(canvas, posterOriginal)
        desenharGradienteMoodGeral(canvas)
        desenharLogo(canvas, fontes.frase, comTextoVltvPlay = true)
        desenharSeloCategoria(canvas, categoria, fontes.destaque)
        desenharTextosInferiores(canvas, sinopse, fraseChamada, fontes.corpo, fontes.frase, categoria.cor)

        return resultado
    }

    // ───────────────────────── Banner de preços/planos ────────────────────

    fun comporPrecos(context: Context): Bitmap {
        val fontes = carregarFontes(context)

        val resultado = Bitmap.createBitmap(LARGURA, ALTURA, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultado)

        desenharFundoPrecos(canvas)
        desenharCabecalhoPrecos(canvas, fontes.destaque, fontes.corpo)
        desenharCardsDePreco(canvas, fontes.destaque, fontes.corpo)
        desenharAssinaturaCentralizada(canvas, fontes.corpo)

        return resultado
    }

    private data class Fontes(val destaque: Typeface, val corpo: Typeface, val frase: Typeface)

    private fun carregarFontes(context: Context): Fontes {
        val destaque = ResourcesCompat.getFont(context, R.font.bebas_neue_regular) ?: Typeface.DEFAULT_BOLD
        val corpo = ResourcesCompat.getFont(context, R.font.poppins_regular) ?: Typeface.DEFAULT
        val frase = ResourcesCompat.getFont(context, R.font.poppins_semibold) ?: Typeface.DEFAULT_BOLD
        return Fontes(destaque, corpo, frase)
    }

    private fun desenharPosterCobrindoTudo(canvas: Canvas, poster: Bitmap) {
        val escala = maxOf(LARGURA.toFloat() / poster.width, ALTURA.toFloat() / poster.height)
        val larguraEscalada = poster.width * escala
        val alturaEscalada = poster.height * escala
        val offsetX = (LARGURA - larguraEscalada) / 2f
        val offsetY = (ALTURA - alturaEscalada) / 2f

        val destino = RectF(offsetX, offsetY, offsetX + larguraEscalada, offsetY + alturaEscalada)
        val origem = android.graphics.Rect(0, 0, poster.width, poster.height)
        canvas.drawBitmap(poster, origem, destino, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    // Escurecimento geral, só pra dar "clima" e ajudar o selo/logo a
    // aparecerem bem mesmo em pôsteres muito claros - a legibilidade do
    // bloco de texto de baixo é garantida à parte, pelo painel sólido em
    // desenharTextosInferiores (esse gradiente aqui sozinho não é confiável
    // pra isso, porque a arte de cada pôster varia muito).
    private fun desenharGradienteMoodGeral(canvas: Canvas) {
        val paintTopo = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, ALTURA * 0.28f,
                Color.parseColor("#8A000000"), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, LARGURA.toFloat(), ALTURA * 0.28f, paintTopo)
    }

    private fun desenharSeloCategoria(canvas: Canvas, categoria: CategoriaBanner, fonteDestaque: Typeface) {
        val textoPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 42f
            typeface = fonteDestaque
            isAntiAlias = true
            letterSpacing = 0.06f
        }
        val larguraTexto = textoPaint.measureText(categoria.rotulo)
        val paddingH = 32f
        val paddingV = 14f
        val margem = 44f

        val fundo = RectF(margem, margem, margem + larguraTexto + paddingH * 2, margem + textoPaint.textSize + paddingV * 2)

        val sombraPaint = Paint().apply {
            color = Color.BLACK
            alpha = 110
            isAntiAlias = true
            maskFilter = android.graphics.BlurMaskFilter(14f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(RectF(fundo.left, fundo.top + 6f, fundo.right, fundo.bottom + 10f), 14f, 14f, sombraPaint)

        val fundoPaint = Paint().apply {
            isAntiAlias = true
            shader = LinearGradient(
                fundo.left, fundo.top, fundo.left, fundo.bottom,
                categoria.corClara, categoria.cor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(fundo, 14f, 14f, fundoPaint)

        val brilhoPaint = Paint().apply {
            color = Color.WHITE
            alpha = 70
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(fundo.left + 1f, fundo.top + 1f, fundo.right - 1f, fundo.bottom - 1f), 13f, 13f, brilhoPaint)

        canvas.drawText(
            categoria.rotulo,
            fundo.left + paddingH,
            fundo.top + paddingV + textoPaint.textSize - 10f,
            textoPaint
        )
    }

    // Bloco de texto inferior: sinopse + frase de chamada + assinatura, com
    // um painel sólido atrás (não só um degradê) pra garantir legibilidade
    // por cima de QUALQUER pôster - mesmo um que já tenha o título da
    // própria obra desenhado bem naquela área da arte oficial.
    private fun desenharTextosInferiores(
        canvas: Canvas,
        sinopse: String,
        fraseChamada: String,
        fonteCorpo: Typeface,
        fonteFrase: Typeface,
        corDestaque: Int
    ) {
        val margem = 48f
        val larguraDisponivel = (LARGURA - margem * 2).toInt()

        var sinopseLayout: StaticLayout? = null
        val sinopseTexto = sinopse.trim()
        if (sinopseTexto.isNotEmpty()) {
            val sinopsePaint = TextPaint().apply {
                color = Color.parseColor("#F2F2F2")
                textSize = 32f
                typeface = fonteCorpo
                isAntiAlias = true
            }
            sinopseLayout = StaticLayout.Builder
                .obtain(sinopseTexto, 0, sinopseTexto.length, sinopsePaint, larguraDisponivel)
                .setMaxLines(3)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .setLineSpacing(4f, 1.08f)
                .build()
        }

        var fraseLayout: StaticLayout? = null
        val fraseTexto = fraseChamada.trim()
        if (fraseTexto.isNotEmpty()) {
            val frasePaint = TextPaint().apply {
                color = corDestaque
                textSize = 40f
                typeface = fonteFrase
                isAntiAlias = true
            }
            fraseLayout = StaticLayout.Builder
                .obtain(fraseTexto, 0, fraseTexto.length, frasePaint, larguraDisponivel)
                .setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .setLineSpacing(0f, 1.05f)
                .build()
        }

        val espacoEntreBlocos = if (sinopseLayout != null && fraseLayout != null) 16f else 0f
        val espacoParaAssinatura = 58f
        val paddingTopoPainel = 34f
        val alturaTexto = (sinopseLayout?.height ?: 0) + (fraseLayout?.height ?: 0) + espacoEntreBlocos + espacoParaAssinatura

        val yTextoInicio = ALTURA - alturaTexto
        val yPainelInicio = yTextoInicio - paddingTopoPainel

        // Painel: transição suave (degradê) nos primeiros ~70px, depois cor
        // sólida forte até o fim do banner - garante contraste com qualquer
        // fundo, sem parecer uma barra dura colada por cima.
        val alturaTransicao = 70f
        val paintTransicao = Paint().apply {
            shader = LinearGradient(
                0f, yPainelInicio, 0f, yPainelInicio + alturaTransicao,
                Color.TRANSPARENT, Color.parseColor("#E8000000"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, yPainelInicio, LARGURA.toFloat(), yPainelInicio + alturaTransicao, paintTransicao)

        val paintSolido = Paint().apply { color = Color.parseColor("#E8000000") }
        canvas.drawRect(0f, yPainelInicio + alturaTransicao, LARGURA.toFloat(), ALTURA.toFloat(), paintSolido)

        var y = yTextoInicio
        if (sinopseLayout != null) {
            canvas.save()
            canvas.translate(margem, y)
            sinopseLayout.draw(canvas)
            canvas.restore()
            y += sinopseLayout.height + (if (fraseLayout != null) espacoEntreBlocos else 0f)
        }

        if (fraseLayout != null) {
            canvas.save()
            canvas.translate(margem, y)
            fraseLayout.draw(canvas)
            canvas.restore()
            y += fraseLayout.height
        }

        val assinaturaPaint = TextPaint().apply {
            color = Color.parseColor("#999999")
            textSize = 24f
            typeface = fonteCorpo
            isAntiAlias = true
        }
        canvas.drawText("vltvplay.tech", margem, y + 36f, assinaturaPaint)
    }

    // Ícone (círculo vermelho + play branco, igual ao ícone do app) com o
    // nome "VLTV PLAY" escrito embaixo - vira uma marca d'água completa,
    // não só um símbolo sem contexto.
    private fun desenharLogo(canvas: Canvas, fonteNome: Typeface, comTextoVltvPlay: Boolean) {
        val raio = 40f
        val cx = LARGURA - 70f
        val cy = 66f

        val sombra = Paint().apply {
            color = Color.BLACK
            alpha = 90
            isAntiAlias = true
            maskFilter = android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy + 3f, raio, sombra)

        val circulo = Paint().apply { color = Color.parseColor("#E50914"); isAntiAlias = true }
        canvas.drawCircle(cx, cy, raio, circulo)

        val play = Paint().apply { color = Color.WHITE; isAntiAlias = true; style = Paint.Style.FILL }
        val path = android.graphics.Path().apply {
            moveTo(cx - 13f, cy - 18f)
            lineTo(cx + 18f, cy)
            lineTo(cx - 13f, cy + 18f)
            close()
        }
        canvas.drawPath(path, play)

        if (comTextoVltvPlay) {
            val nomePaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 22f
                typeface = Typeface.create(fonteNome, Typeface.BOLD)
                isAntiAlias = true
                letterSpacing = 0.04f
                textAlign = Paint.Align.CENTER
                setShadowLayer(6f, 0f, 2f, Color.parseColor("#AA000000"))
            }
            canvas.drawText("VLTV PLAY", cx, cy + raio + 30f, nomePaint)
        }
    }

    // ───────────────────────── Peças do banner de preços ──────────────────

    private fun desenharFundoPrecos(canvas: Canvas) {
        // Fundo escuro com um brilho vermelho suave atrás do topo (onde fica
        // a logo), dando profundidade sem precisar de nenhuma imagem externa.
        canvas.drawColor(Color.parseColor("#0D0D0D"))

        val brilho = Paint().apply {
            shader = android.graphics.RadialGradient(
                LARGURA / 2f, 260f, 620f,
                Color.parseColor("#40E50914"), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, LARGURA.toFloat(), ALTURA.toFloat(), brilho)
    }

    private fun desenharCabecalhoPrecos(canvas: Canvas, fonteDestaque: Typeface, fonteCorpo: Typeface) {
        val cx = LARGURA / 2f
        val cy = 170f
        val raio = 64f

        val sombra = Paint().apply {
            color = Color.BLACK
            alpha = 110
            isAntiAlias = true
            maskFilter = android.graphics.BlurMaskFilter(18f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy + 5f, raio, sombra)

        val circulo = Paint().apply { color = Color.parseColor("#E50914"); isAntiAlias = true }
        canvas.drawCircle(cx, cy, raio, circulo)

        val play = Paint().apply { color = Color.WHITE; isAntiAlias = true; style = Paint.Style.FILL }
        val path = android.graphics.Path().apply {
            moveTo(cx - 21f, cy - 29f)
            lineTo(cx + 29f, cy)
            lineTo(cx - 21f, cy + 29f)
            close()
        }
        canvas.drawPath(path, play)

        val nomePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 88f
            typeface = fonteDestaque
            isAntiAlias = true
            letterSpacing = 0.03f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("VLTV PLAY", cx, cy + raio + 90f, nomePaint)

        val subtituloPaint = TextPaint().apply {
            color = Color.parseColor("#CCCCCC")
            textSize = 32f
            typeface = fonteCorpo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Escolha seu plano", cx, cy + raio + 140f, subtituloPaint)
    }

    private fun desenharCardsDePreco(canvas: Canvas, fonteDestaque: Typeface, fonteCorpo: Typeface) {
        val planos = PlanoCliente.values()
        val margemExterna = 56f
        val espacoEntreCards = 24f
        val larguraCard = (LARGURA - margemExterna * 2 - espacoEntreCards) / 2f
        val alturaCard = 300f
        val yInicio = 560f

        val formatoMoeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

        planos.forEachIndexed { index, plano ->
            val coluna = index % 2
            val linha = index / 2
            val left = margemExterna + coluna * (larguraCard + espacoEntreCards)
            val top = yInicio + linha * (alturaCard + espacoEntreCards)
            val card = RectF(left, top, left + larguraCard, top + alturaCard)

            val destaque = plano == PlanoCliente.ANUAL // maior plano ganha selo de melhor oferta

            desenharUmCardDePreco(canvas, card, plano, formatoMoeda.format(plano.valorPadrao), destaque, fonteDestaque, fonteCorpo)
        }
    }

    private fun desenharUmCardDePreco(
        canvas: Canvas,
        card: RectF,
        plano: PlanoCliente,
        precoFormatado: String,
        destaque: Boolean,
        fonteDestaque: Typeface,
        fonteCorpo: Typeface
    ) {
        val sombra = Paint().apply {
            color = Color.BLACK
            alpha = 130
            isAntiAlias = true
            maskFilter = android.graphics.BlurMaskFilter(16f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawRoundRect(RectF(card.left, card.top + 6f, card.right, card.bottom + 8f), 22f, 22f, sombra)

        val fundoPaint = Paint().apply {
            isAntiAlias = true
            color = if (destaque) Color.parseColor("#241414") else Color.parseColor("#1A1A1A")
        }
        canvas.drawRoundRect(card, 22f, 22f, fundoPaint)

        val corBorda = if (destaque) Color.parseColor("#E50914") else Color.parseColor("#333333")
        val bordaPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = if (destaque) 3f else 1.5f
            color = corBorda
            isAntiAlias = true
        }
        canvas.drawRoundRect(card, 22f, 22f, bordaPaint)

        val cx = card.centerX()

        if (destaque) {
            val seloPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 20f
                typeface = Typeface.create(fonteCorpo, Typeface.BOLD)
                isAntiAlias = true
                letterSpacing = 0.04f
                textAlign = Paint.Align.CENTER
            }
            val larguraSelo = seloPaint.measureText("MELHOR OFERTA") + 32f
            val seloRect = RectF(cx - larguraSelo / 2f, card.top - 16f, cx + larguraSelo / 2f, card.top + 22f)
            val seloFundo = Paint().apply { color = Color.parseColor("#E50914"); isAntiAlias = true }
            canvas.drawRoundRect(seloRect, 12f, 12f, seloFundo)
            canvas.drawText("MELHOR OFERTA", cx, seloRect.top + 26f, seloPaint)
        }

        val nomePaint = TextPaint().apply {
            color = Color.parseColor("#CCCCCC")
            textSize = 30f
            typeface = fonteCorpo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(rotuloCurto(plano), cx, card.top + 58f, nomePaint)

        val precoPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 68f
            typeface = fonteDestaque
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(precoFormatado, cx, card.top + 138f, precoPaint)

        val periodoPaint = TextPaint().apply {
            color = Color.parseColor("#999999")
            textSize = 24f
            typeface = fonteCorpo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(periodoDoPlano(plano), cx, card.top + 178f, periodoPaint)
    }

    private fun rotuloCurto(plano: PlanoCliente): String = when (plano) {
        PlanoCliente.MENSAL -> "MENSAL"
        PlanoCliente.TRIMESTRAL -> "TRIMESTRAL"
        PlanoCliente.SEMESTRAL -> "SEMESTRAL"
        PlanoCliente.ANUAL -> "ANUAL"
    }

    private fun periodoDoPlano(plano: PlanoCliente): String = when (plano) {
        PlanoCliente.MENSAL -> "por mês"
        PlanoCliente.TRIMESTRAL -> "a cada 3 meses"
        PlanoCliente.SEMESTRAL -> "a cada 6 meses"
        PlanoCliente.ANUAL -> "por ano"
    }

    private fun desenharAssinaturaCentralizada(canvas: Canvas, fonteCorpo: Typeface) {
        val assinaturaPaint = TextPaint().apply {
            color = Color.parseColor("#999999")
            textSize = 26f
            typeface = fonteCorpo
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("vltvplay.tech", LARGURA / 2f, ALTURA - 60f, assinaturaPaint)
    }
}
