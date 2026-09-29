package com.vltv.clientes.ui

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

// As 4 opções fixas de "selo" de destaque do banner - cada uma com sua
// própria cor, pra dar uma identidade visual diferente pra cada tipo de
// chamada (visto na tela de Gerador de Banner).
enum class CategoriaBanner(val rotulo: String, val cor: Int) {
    DESTAQUE_SEMANA("DESTAQUE DA SEMANA", Color.parseColor("#E50914")),
    TOP10_BRASIL("TOP 10 BRASIL", Color.parseColor("#00A859")),
    TOP10_MUNDO("TOP 10 MUNDO", Color.parseColor("#1565C0")),
    LANCAMENTO("LANÇAMENTO", Color.parseColor("#F9A825"))
}

// Monta o banner final num Canvas: pôster de fundo (cobrindo tudo, cortado
// no centro), gradiente escuro embaixo pra legibilidade, selo da categoria,
// título do filme/série e a marca d'água do logo (círculo vermelho + play
// branco, igual ao ícone do app) no canto.
object BannerComposer {

    private const val LARGURA = 1080
    private const val ALTURA = 1350

    fun compor(posterOriginal: Bitmap, categoria: CategoriaBanner, titulo: String): Bitmap {
        val resultado = Bitmap.createBitmap(LARGURA, ALTURA, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultado)

        desenharPosterCobrindoTudo(canvas, posterOriginal)
        desenharGradienteInferior(canvas)
        desenharSeloCategoria(canvas, categoria)
        desenharTitulo(canvas, titulo)
        desenharLogo(canvas)

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
        val inicioGradiente = ALTURA * 0.45f
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, inicioGradiente, 0f, ALTURA.toFloat(),
                Color.TRANSPARENT, Color.parseColor("#E6000000"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, inicioGradiente, LARGURA.toFloat(), ALTURA.toFloat(), paint)
    }

    private fun desenharSeloCategoria(canvas: Canvas, categoria: CategoriaBanner) {
        val textoPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.03f
        }
        val larguraTexto = textoPaint.measureText(categoria.rotulo)
        val paddingH = 28f
        val paddingV = 16f
        val margem = 40f

        val fundo = RectF(margem, margem, margem + larguraTexto + paddingH * 2, margem + textoPaint.textSize + paddingV * 2)
        val fundoPaint = Paint().apply { color = categoria.cor; isAntiAlias = true }
        canvas.drawRoundRect(fundo, 10f, 10f, fundoPaint)

        canvas.drawText(
            categoria.rotulo,
            fundo.left + paddingH,
            fundo.top + paddingV + textoPaint.textSize - 6f,
            textoPaint
        )
    }

    private fun desenharTitulo(canvas: Canvas, titulo: String) {
        val textoPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            isAntiAlias = true
        }
        val margem = 48f
        val larguraDisponivel = (LARGURA - margem * 2).toInt()

        val staticLayout = StaticLayout.Builder
            .obtain(titulo, 0, titulo.length, textoPaint, larguraDisponivel)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.05f)
            .build()

        canvas.save()
        canvas.translate(margem, ALTURA - 130f - staticLayout.height)
        staticLayout.draw(canvas)
        canvas.restore()

        // Linha de assinatura pequena embaixo do título.
        val assinaturaPaint = TextPaint().apply {
            color = Color.parseColor("#CCCCCC")
            textSize = 28f
            isAntiAlias = true
        }
        canvas.drawText("vltvplay.tech", margem, ALTURA - 70f, assinaturaPaint)
    }

    // Reproduz o mesmo desenho do ícone do app (círculo vermelho + triângulo
    // de play branco) como marca d'água, sem depender de carregar um PNG.
    private fun desenharLogo(canvas: Canvas) {
        val raio = 44f
        val cx = LARGURA - 70f
        val cy = 70f

        val circulo = Paint().apply { color = Color.parseColor("#E50914"); isAntiAlias = true }
        canvas.drawCircle(cx, cy, raio, circulo)

        val play = Paint().apply { color = Color.WHITE; isAntiAlias = true; style = Paint.Style.FILL }
        val path = android.graphics.Path().apply {
            moveTo(cx - 14f, cy - 20f)
            lineTo(cx + 20f, cy)
            lineTo(cx - 14f, cy + 20f)
            close()
        }
        canvas.drawPath(path, play)
    }
}
