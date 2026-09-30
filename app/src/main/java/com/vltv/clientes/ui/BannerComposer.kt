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

// As 4 opções fixas de "selo" de destaque do banner - cada uma com sua
// própria cor, pra dar uma identidade visual diferente pra cada tipo de
// chamada (visto na tela de Gerador de Banner). corClara é usada no topo do
// degradê do selo, pra dar um efeito de profundidade em vez de cor lisa.
enum class CategoriaBanner(val rotulo: String, val cor: Int, val corClara: Int) {
    DESTAQUE_SEMANA("DESTAQUE DA SEMANA", Color.parseColor("#E50914"), Color.parseColor("#FF4655")),
    TOP10_BRASIL("TOP 10 BRASIL", Color.parseColor("#00A859"), Color.parseColor("#2ED37A")),
    TOP10_MUNDO("TOP 10 MUNDO", Color.parseColor("#1565C0"), Color.parseColor("#3B8DE8")),
    LANCAMENTO("LANÇAMENTO", Color.parseColor("#F9A825"), Color.parseColor("#FFC24D"))
}

// Monta o banner final num Canvas: pôster de fundo (cobrindo tudo, cortado
// no centro) - que já vem com o nome do filme/série na própria arte oficial,
// por isso NÃO repetimos o título em texto por cima, pra não ficar
// redundante. Em vez disso, o texto do banner é: selo de destaque (fonte
// Bebas Neue, estilo cartaz de cinema, com sombra e degradê), um trecho da
// sinopse (Poppins Regular) e uma frase de chamada opcional (Poppins
// SemiBold, na cor da categoria) - além da marca d'água do logo no canto.
object BannerComposer {

    private const val LARGURA = 1080
    private const val ALTURA = 1350

    fun compor(
        context: Context,
        posterOriginal: Bitmap,
        categoria: CategoriaBanner,
        sinopse: String,
        fraseChamada: String
    ): Bitmap {
        val fonteDestaque = ResourcesCompat.getFont(context, R.font.bebas_neue_regular) ?: Typeface.DEFAULT_BOLD
        val fonteCorpo = ResourcesCompat.getFont(context, R.font.poppins_regular) ?: Typeface.DEFAULT
        val fonteFrase = ResourcesCompat.getFont(context, R.font.poppins_semibold) ?: Typeface.DEFAULT_BOLD

        val resultado = Bitmap.createBitmap(LARGURA, ALTURA, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultado)

        desenharPosterCobrindoTudo(canvas, posterOriginal)
        desenharGradienteInferior(canvas)
        desenharLogo(canvas)
        desenharSeloCategoria(canvas, categoria, fonteDestaque)
        desenharTextosInferiores(canvas, sinopse, fraseChamada, fonteCorpo, fonteFrase, categoria.cor)

        return resultado
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

    private fun desenharGradienteInferior(canvas: Canvas) {
        val inicioGradiente = ALTURA * 0.55f
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, inicioGradiente, 0f, ALTURA.toFloat(),
                Color.TRANSPARENT, Color.parseColor("#F2000000"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, inicioGradiente, LARGURA.toFloat(), ALTURA.toFloat(), paint)

        // Um véu bem sutil no topo também, só pra garantir contraste com o
        // selo, mesmo em pôsteres muito claros ali no canto.
        val paintTopo = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, ALTURA * 0.28f,
                Color.parseColor("#8A000000"), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, LARGURA.toFloat(), ALTURA * 0.28f, paintTopo)
    }

    // Selo de destaque com fonte de cartaz de cinema (Bebas Neue), degradê
    // sutil e sombra por baixo - pra parecer um selo de verdade, não uma
    // caixinha lisa com texto em cima.
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

        // Um traço branco bem sutil na borda de cima, pra dar um brilho de
        // "vidro"/destaque, igual selo premium.
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

    // Bloco de texto inferior: sinopse (menor, corpo de texto) em cima da
    // frase de chamada (maior, colorida, opcional) e da assinatura do site.
    // Nada de nome do filme aqui - o pôster já mostra isso na própria arte.
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

        val espacoEntreBlocos = 16f
        val espacoParaAssinatura = 58f
        val alturaSinopse = sinopseLayout?.height ?: 0
        val alturaFrase = fraseLayout?.height ?: 0
        val espacosOcupados = (if (sinopseLayout != null && fraseLayout != null) espacoEntreBlocos else 0f)
        val alturaTotal = alturaSinopse + alturaFrase + espacosOcupados + espacoParaAssinatura

        var y = ALTURA - alturaTotal

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

    // Reproduz o mesmo desenho do ícone do app (círculo vermelho + triângulo
    // de play branco) como marca d'água, sem depender de carregar um PNG.
    private fun desenharLogo(canvas: Canvas) {
        val raio = 40f
        val cx = LARGURA - 66f
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
    }
}
