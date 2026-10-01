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
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import com.vltv.clientes.R
import com.vltv.clientes.data.PlanoCliente
import java.util.Locale
import kotlin.random.Random

private fun cor(hex: String): Int = Color.parseColor(hex)

private fun alfa(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

// Selo de destaque do banner de filme/série - cada um com sua cor.
enum class CategoriaBanner(val rotulo: String, val cor: Int, val corClara: Int) {
    DESTAQUE_SEMANA("DESTAQUE DA SEMANA", cor("#E50914"), cor("#FF4655")),
    TOP10_BRASIL("TOP 10 BRASIL", cor("#00A859"), cor("#2ED37A")),
    TOP10_MUNDO("TOP 10 MUNDO", cor("#1565C0"), cor("#3B8DE8")),
    LANCAMENTO("LANÇAMENTO", cor("#F9A825"), cor("#FFC24D"))
}

// Paletas dos banners de preço/indicação: cor "metálica" (claro/base/escuro)
// usada nos textos e botões + 2 tons de fundo.
enum class PaletaBanner(
    val rotulo: String,
    val claro: Int,
    val base: Int,
    val escuro: Int,
    val fundoTopo: Int,
    val fundoBase: Int
) {
    DOURADO("Dourado", cor("#F6D9BC"), cor("#C8956C"), cor("#8A5A3A"), cor("#13213D"), cor("#060A12")),
    VERMELHO("Vermelho", cor("#FF6B73"), cor("#E50914"), cor("#8F0710"), cor("#2A0B0F"), cor("#070505")),
    AZUL("Azul", cor("#9BE0FF"), cor("#2D9CDB"), cor("#14608F"), cor("#0B2F52"), cor("#03101F")),
    VERDE("Verde", cor("#8FF5BC"), cor("#1FBF6B"), cor("#0F7A43"), cor("#0B2F20"), cor("#030D08")),
    ROXO("Roxo", cor("#D3B3FF"), cor("#9B5CF6"), cor("#5B2BB0"), cor("#25104A"), cor("#080414"))
}

// Modelos (layouts) dos banners de preço e de indicação.
enum class EstiloPreco(val rotulo: String) {
    PREMIUM("Premium"),
    VIDRO("Vidro"),
    CIRCULO("Círculo"),
    TABELA("Todos os planos"),
    FESTA("Festa")
}

// Formato final do banner (Paisagem 16:9, Quadrado 1:1, Story 9:16).
enum class FormatoBanner(val rotulo: String, val largura: Int, val altura: Int) {
    PAISAGEM("Paisagem", 1600, 900),
    QUADRADO("Quadrado", 1080, 1080),
    STORY("Story", 1080, 1920)
}

// Modelos (layouts) dos banners de filme/série.
enum class EstiloFilme(val rotulo: String) {
    CINEMA("Cinema"),
    CARTAZ("Cartaz"),
    NEON("Neon")
}

// Gera todos os banners desenhando direto no Canvas (sem depender de
// internet nem de app externo). Cada função recebe "escala": 1f gera o
// banner final em alta resolução; 0.25f gera miniaturas rápidas pra galeria
// de modelos.
object BannerComposer {

    // Banners de preço/indicação: paisagem 16:9
    private const val LARG_H = 1600
    private const val ALT_H = 900

    // Banners de filme/série: retrato 4:5 (bom pro status do WhatsApp/feed)
    private const val LARG_F = 1080
    private const val ALT_F = 1350

    const val NOTA_INDICACAO_PADRAO = "O indicado deve mencionar o seu nome."

    private class Fontes(val titulo: Typeface, val corpo: Typeface, val negrito: Typeface)

    private class DadosPlano(
        val rotulo: String,
        val nomeCurto: String,
        val valor: Double,
        val descricao1: String,
        val descricao2: String
    )

    private val tonsTela = arrayOf(
        intArrayOf(cor("#2B6F77"), cor("#0F2A33")),
        intArrayOf(cor("#C2692A"), cor("#3A1A0C")),
        intArrayOf(cor("#7A2230"), cor("#220A10")),
        intArrayOf(cor("#3C4A63"), cor("#12182A")),
        intArrayOf(cor("#2E7D4B"), cor("#0C2416")),
        intArrayOf(cor("#B8923A"), cor("#33250A"))
    )

    private fun carregarFontes(context: Context): Fontes {
        val titulo = ResourcesCompat.getFont(context, R.font.bebas_neue_regular) ?: Typeface.DEFAULT_BOLD
        val corpo = ResourcesCompat.getFont(context, R.font.poppins_regular) ?: Typeface.DEFAULT
        val negrito = ResourcesCompat.getFont(context, R.font.poppins_semibold) ?: Typeface.DEFAULT_BOLD
        return Fontes(titulo, corpo, negrito)
    }

    private fun novoCanvas(w: Int, h: Int, escala: Float): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(
            (w * escala).toInt().coerceAtLeast(1),
            (h * escala).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        canvas.scale(escala, escala)
        return Pair(bmp, canvas)
    }

    // ═════════════════════════ PREÇO DE UM PLANO ═════════════════════════

    fun comporPlano(
        context: Context,
        plano: PlanoCliente,
        valor: Double,
        estilo: EstiloPreco,
        paleta: PaletaBanner,
        textoExtra: String,
        formato: FormatoBanner = FormatoBanner.PAISAGEM,
        escala: Float = 1f
    ): Bitmap {
        val f = carregarFontes(context)
        val (bmp, c) = novoCanvas(LARG_H, ALT_H, escala)
        val d = dadosDoPlano(plano, valor, textoExtra)

        when (estilo) {
            EstiloPreco.PREMIUM -> layoutPremium(c, f, paleta, d)
            EstiloPreco.VIDRO -> layoutVidro(c, f, paleta, d)
            EstiloPreco.CIRCULO -> layoutCirculo(c, f, paleta, d)
            EstiloPreco.TABELA -> layoutTabela(c, f, paleta, plano, valor)
            EstiloPreco.FESTA -> layoutPremium(c, f, paleta, d)
        }
        return noFormato(bmp, formato, paleta, escala)
    }

    private fun dadosDoPlano(plano: PlanoCliente, valor: Double, extra: String): DadosPlano {
        val meses = when (plano) {
            PlanoCliente.MENSAL -> 1
            PlanoCliente.TRIMESTRAL -> 3
            PlanoCliente.SEMESTRAL -> 6
            PlanoCliente.ANUAL -> 12
        }
        val nome = when (plano) {
            PlanoCliente.MENSAL -> "MENSAL"
            PlanoCliente.TRIMESTRAL -> "TRIMESTRAL"
            PlanoCliente.SEMESTRAL -> "SEMESTRAL"
            PlanoCliente.ANUAL -> "ANUAL"
        }
        val linha1 = if (extra.isNotBlank()) {
            extra.trim()
        } else if (meses == 1) {
            "Aproveite 1 mês de conteúdo incrível!"
        } else {
            "Aproveite $meses meses de conteúdo incrível!"
        }
        val linha2 = if (extra.isNotBlank()) "" else "Filmes, Séries, Esportes e Canais Ao Vivo"
        return DadosPlano("PLANO $nome", nome, valor, linha1, linha2)
    }

    // ── Modelo 1: Premium (fundo escuro, texto metálico grande, aparelhos) ──
    private fun layoutPremium(c: Canvas, f: Fontes, p: PaletaBanner, d: DadosPlano) {
        val w = LARG_H.toFloat()
        val h = ALT_H.toFloat()
        fundoEscuro(c, w, h, p, 11)

        // grade de pôsteres no fundo, esmaecida
        val rnd = Random(5)
        for (l in 0..1) {
            for (col in 0..3) {
                val r = RectF(900f + col * 170f, 40f + l * 130f, 1060f + col * 170f, 160f + l * 130f)
                tile(c, r, rnd)
            }
        }
        val veu = Paint().apply {
            shader = LinearGradient(
                900f, 0f, 1400f, 300f,
                alfa(p.fundoTopo, 150), alfa(p.fundoTopo, 235), Shader.TileMode.CLAMP
            )
        }
        c.drawRect(860f, 0f, w, 320f, veu)

        iconeQuadrado(c, 1010f, 130f, 80f, 0, p)
        iconeQuadrado(c, 1110f, 130f, 80f, 1, p)
        iconeQuadrado(c, 1210f, 130f, 80f, 2, p)

        aparelho(c, RectF(900f, 210f, 1540f, 570f), 0, p, f, 1)
        aparelho(c, RectF(860f, 520f, 1170f, 750f), 1, p, f, 2)
        aparelho(c, RectF(1250f, 470f, 1450f, 860f), 2, p, f, 3)

        logo(c, 90f, 190f, 150f, f, p, Paint.Align.LEFT)

        val tag = tp(Color.WHITE, 30f, f.corpo, Paint.Align.LEFT, 0.06f)
        ajustar(tag, "SUA MELHOR EXPERIÊNCIA DE ENTRETENIMENTO", 740f)
        c.drawText("SUA MELHOR EXPERIÊNCIA DE ENTRETENIMENTO", 90f, 250f, tag)

        val tamRotulo = tamanhoQueCabe(d.rotulo, f.titulo, 118f, 760f, 0.02f)
        textoMetal(c, d.rotulo, 90f, 420f, tamRotulo, f.titulo, p, Paint.Align.LEFT, 0.02f)

        precoGrande(c, d.valor, 90f, 625f, 210f, f, p, false)

        val l1 = tp(Color.WHITE, 36f, f.negrito, Paint.Align.LEFT)
        ajustar(l1, d.descricao1, 760f)
        c.drawText(d.descricao1, 90f, 705f, l1)
        if (d.descricao2.isNotEmpty()) {
            val l2 = tp(cor("#D8DCE6"), 30f, f.corpo, Paint.Align.LEFT)
            ajustar(l2, d.descricao2, 760f)
            c.drawText(d.descricao2, 90f, 750f, l2)
        }

        botaoCta(c, RectF(90f, 790f, 450f, 870f), "ASSINE JÁ", f, p, 38f)
        val lado = tp(Color.WHITE, 26f, f.corpo, Paint.Align.LEFT)
        c.drawText("Disponível na sua TV", 480f, 822f, lado)
        c.drawText("e Celular. vltvplay.tech", 480f, 856f, lado)
    }

    // ── Modelo 2: Vidro (cartão translúcido com o preço) ──
    private fun layoutVidro(c: Canvas, f: Fontes, p: PaletaBanner, d: DadosPlano) {
        val w = LARG_H.toFloat()
        val h = ALT_H.toFloat()
        fundoEscuro(c, w, h, p, 21)

        val blob = Paint(Paint.ANTI_ALIAS_FLAG)
        blob.color = alfa(p.escuro, 120)
        c.drawCircle(150f, 170f, 430f, blob)
        blob.color = alfa(p.base, 60)
        c.drawCircle(1480f, 820f, 460f, blob)
        blob.color = alfa(p.claro, 35)
        c.drawCircle(1500f, 100f, 300f, blob)

        aparelho(c, RectF(330f, 310f, 780f, 620f), 1, p, f, 7)
        aparelho(c, RectF(110f, 370f, 350f, 810f), 2, p, f, 8)

        logo(c, w / 2f, 150f, 110f, f, p, Paint.Align.CENTER)
        val tag = tp(Color.WHITE, 30f, f.corpo, Paint.Align.CENTER, 0.06f)
        ajustar(tag, "SUA MELHOR EXPERIÊNCIA DE ENTRETENIMENTO", 900f)
        c.drawText("SUA MELHOR EXPERIÊNCIA DE ENTRETENIMENTO", w / 2f, 215f, tag)

        val card = RectF(880f, 280f, 1520f, 750f)
        cartaoVidro(c, card, p)
        val cx = card.centerX()

        val tamRotulo = tamanhoQueCabe(d.rotulo, f.titulo, 78f, 560f, 0.02f)
        val rot = tp(Color.WHITE, tamRotulo, f.titulo, Paint.Align.CENTER, 0.02f)
        c.drawText(d.rotulo, cx, 375f, rot)

        precoGrande(c, d.valor, cx, 580f, 190f, f, p, true)

        val l1 = tp(Color.WHITE, 30f, f.negrito, Paint.Align.CENTER)
        ajustar(l1, d.descricao1, 570f)
        c.drawText(d.descricao1, cx, 655f, l1)
        if (d.descricao2.isNotEmpty()) {
            val l2 = tp(cor("#E3E8F2"), 26f, f.corpo, Paint.Align.CENTER)
            ajustar(l2, d.descricao2, 570f)
            c.drawText(d.descricao2, cx, 700f, l2)
        }

        botaoCta(c, RectF(570f, 790f, 1010f, 870f), "ASSINE JÁ", f, p, 38f)
    }

    // ── Modelo 3: Círculo (preço dentro de um selo redondo) ──
    private fun layoutCirculo(c: Canvas, f: Fontes, p: PaletaBanner, d: DadosPlano) {
        val w = LARG_H.toFloat()
        val h = ALT_H.toFloat()
        fundoEscuro(c, w, h, p, 31)
        redeDeLinhas(c, w, h, p)
        moldura(c, w, h, p)

        logo(c, 110f, 230f, 118f, f, p, Paint.Align.LEFT)

        val titulo = tp(Color.WHITE, 84f, f.titulo, Paint.Align.LEFT, 0.02f)
        c.drawText("ENTRETENIMENTO", 110f, 355f, titulo)
        c.drawText("ILIMITADO EM", 110f, 435f, titulo)
        c.drawText("UM SÓ LUGAR", 110f, 515f, titulo)

        val itens = listOf(
            "Assista a Filmes & Séries",
            "Canais de Esportes e Notícias",
            "Alta Qualidade HD / 4K",
            "Conteúdo para Toda a Família",
            "Compatível com Smart TV, Celular, Tablet, PC"
        )
        val tb = tp(Color.WHITE, 28f, f.negrito, Paint.Align.LEFT)
        val bolinha = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.base }
        itens.forEachIndexed { i, t ->
            val y = 600f + i * 50f
            c.drawCircle(122f, y - 9f, 8f, bolinha)
            ajustar(tb, t, 720f)
            c.drawText(t, 148f, y, tb)
        }

        // selo redondo com o preço
        val cx = 1060f
        val cy = 390f
        val r = 175f
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r * 1.6f, alfa(p.base, 110), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, r * 1.6f, glow)
        val miolo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy - 40f, r, alfa(p.fundoTopo, 255), alfa(p.fundoBase, 255), Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, r, miolo)
        val anel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            shader = LinearGradient(cx, cy - r, cx, cy + r, p.claro, p.escuro, Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, r, anel)
        anel.strokeWidth = 2f
        anel.alpha = 150
        c.drawCircle(cx, cy, r - 18f, anel)

        val inteiro = partesPreco(d.valor).first
        val cent = partesPreco(d.valor).second
        textoMetal(c, "R$", cx, 305f, 56f, f.titulo, p, Paint.Align.CENTER, 0f)
        val tamInt = tamanhoQueCabe(inteiro, f.titulo, 190f, 250f, 0f)
        textoMetal(c, inteiro, cx, 465f, tamInt, f.titulo, p, Paint.Align.CENTER, 0f)
        if (cent != ",00") {
            textoMetal(c, cent, cx + 120f, 465f, 50f, f.titulo, p, Paint.Align.LEFT, 0f)
        }
        val lab = tp(Color.WHITE, 38f, f.titulo, Paint.Align.CENTER, 0.08f)
        c.drawText(d.nomeCurto, cx, 525f, lab)

        botaoCta(c, RectF(850f, 640f, 1270f, 735f), "ASSINE AGORA E COMECE A VER!", f, p, 25f)
        val url = tp(cor("#C9D1DE"), 28f, f.corpo, Paint.Align.CENTER)
        c.drawText("https://vltvplay.tech", cx, 790f, url)

        aparelho(c, RectF(1310f, 300f, 1550f, 440f), 0, p, f, 41)
        aparelho(c, RectF(1440f, 410f, 1550f, 650f), 2, p, f, 42)
    }

    // ── Modelo 4: Tabela com todos os planos, o escolhido em destaque ──
    private fun layoutTabela(c: Canvas, f: Fontes, p: PaletaBanner, escolhido: PlanoCliente, valorEditado: Double) {
        val w = LARG_H.toFloat()
        val h = ALT_H.toFloat()
        fundoEscuro(c, w, h, p, 51)

        logo(c, w / 2f, 150f, 120f, f, p, Paint.Align.CENTER)
        val sub = tp(cor("#DDE2EC"), 38f, f.titulo, Paint.Align.CENTER, 0.12f)
        c.drawText("ESCOLHA SEU PLANO", w / 2f, 215f, sub)

        val planos = PlanoCliente.values()
        val margem = 80f
        val gap = 24f
        val larg = (w - margem * 2 - gap * (planos.size - 1)) / planos.size
        val topo = 290f
        val alt = 400f

        planos.forEachIndexed { i, plano ->
            val left = margem + i * (larg + gap)
            val card = RectF(left, topo, left + larg, topo + alt)
            val sel = plano == escolhido
            val valor = if (sel) valorEditado else plano.valorPadrao

            val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                alpha = 130
                maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawRoundRect(RectF(card.left, card.top + 8f, card.right, card.bottom + 10f), 28f, 28f, sombra)

            val fundo = Paint(Paint.ANTI_ALIAS_FLAG)
            if (sel) {
                fundo.shader = LinearGradient(card.left, card.top, card.left, card.bottom, alfa(p.base, 70), alfa(p.fundoBase, 255), Shader.TileMode.CLAMP)
            } else {
                fundo.color = cor("#14171F")
            }
            c.drawRoundRect(card, 28f, 28f, fundo)

            val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = if (sel) 5f else 2f
                color = if (sel) p.base else cor("#2C313D")
            }
            c.drawRoundRect(card, 28f, 28f, borda)

            val nome = tp(if (sel) p.claro else cor("#C5CBD6"), 34f, f.negrito, Paint.Align.CENTER, 0.05f)
            c.drawText(nomeCurtoDoPlano(plano), card.centerX(), card.top + 80f, nome)

            precoGrande(c, valor, card.centerX(), card.top + 220f, 120f, f, p, true)

            val per = tp(cor("#9AA2B1"), 26f, f.corpo, Paint.Align.CENTER)
            c.drawText(periodoDoPlano(plano), card.centerX(), card.top + 275f, per)

            if (plano == PlanoCliente.ANUAL) {
                val selo = tp(Color.WHITE, 22f, f.negrito, Paint.Align.CENTER, 0.05f)
                val lSelo = selo.measureText("MELHOR OFERTA") + 40f
                val rSelo = RectF(card.centerX() - lSelo / 2f, card.top - 20f, card.centerX() + lSelo / 2f, card.top + 24f)
                val pSelo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor("#E50914") }
                c.drawRoundRect(rSelo, 14f, 14f, pSelo)
                c.drawText("MELHOR OFERTA", card.centerX(), rSelo.top + 31f, selo)
            }
        }

        botaoCta(c, RectF(560f, 740f, 1040f, 830f), "ASSINE JÁ", f, p, 38f)
        val url = tp(cor("#9AA2B1"), 26f, f.corpo, Paint.Align.CENTER)
        c.drawText("vltvplay.tech", w / 2f, 872f, url)
    }

    private fun nomeCurtoDoPlano(plano: PlanoCliente): String = when (plano) {
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

    // ═════════════════════════ INDIQUE E GANHE ═════════════════════════

    fun comporIndicacao(
        context: Context,
        estilo: EstiloPreco,
        paleta: PaletaBanner,
        qtdAmigos: Int,
        nota: String,
        foto: Bitmap?,
        formato: FormatoBanner = FormatoBanner.PAISAGEM,
        escala: Float = 1f
    ): Bitmap {
        val f = carregarFontes(context)
        val observacao = if (nota.isBlank()) NOTA_INDICACAO_PADRAO else nota.trim()
        val qtd = qtdAmigos.coerceAtLeast(1)

        if (estilo == EstiloPreco.FESTA) {
            val (bmpF, cF) = novoCanvas(formato.largura, formato.altura, escala)
            indicacaoFesta(cF, f, paleta, qtd, observacao, foto, formato.largura.toFloat(), formato.altura.toFloat())
            return bmpF
        }

        val (bmp, c) = novoCanvas(LARG_H, ALT_H, escala)
        val amigos = if (qtd == 1) "1 amigo" else "$qtd amigos"
        val msg = "Indique $amigos e ganhe 1 mês grátis. $observacao"

        when (estilo) {
            EstiloPreco.PREMIUM -> indicacaoPremium(c, f, paleta, msg)
            EstiloPreco.VIDRO -> indicacaoVidro(c, f, paleta, msg)
            else -> indicacaoCirculo(c, f, paleta, msg)
        }
        return noFormato(bmp, formato, paleta, escala)
    }

    // Coloca um banner paisagem dentro de um quadro quadrado/story, com o
    // mesmo fundo escuro nas sobras (em cima e embaixo).
    private fun noFormato(base: Bitmap, formato: FormatoBanner, p: PaletaBanner, escala: Float): Bitmap {
        if (formato == FormatoBanner.PAISAGEM) return base
        val w = formato.largura.toFloat()
        val h = formato.altura.toFloat()
        val (bmp, c) = novoCanvas(formato.largura, formato.altura, escala)
        fundoEscuro(c, w, h, p, 5)
        val altura = w * ALT_H / LARG_H
        val destino = RectF(0f, (h - altura) / 2f, w, (h + altura) / 2f)
        c.drawBitmap(base, null, destino, Paint(Paint.FILTER_BITMAP_FLAG))
        return bmp
    }

    // ── Modelo "Festa": colorido, com foto (ou presente), emojis e respingos ──
    private fun indicacaoFesta(c: Canvas, f: Fontes, p: PaletaBanner, qtd: Int, nota: String, foto: Bitmap?, w: Float, h: Float) {
        val cor1 = misturar(p.base, p.escuro, 0.25f)
        val hsv = FloatArray(3)
        Color.colorToHSV(p.base, hsv)
        hsv[0] = (hsv[0] + 160f) % 360f
        hsv[1] = (hsv[1] * 0.75f).coerceIn(0.4f, 0.9f)
        hsv[2] = 0.85f
        val cor2 = Color.HSVToColor(hsv)

        // fundo claro com leve degradê
        val topo = misturar(p.claro, Color.WHITE, 0.55f)
        val baixo = misturar(p.claro, Color.WHITE, 0.9f)
        val fundo = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h, topo, baixo, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, fundo)

        // tudo abaixo é desenhado num quadro de 1080 de largura, centralizado
        val u = if (w > h) h / 1080f else w / 1080f
        val lw = 1080f
        val lh = h / u
        c.save()
        c.translate((w - lw * u) / 2f, 0f)
        c.scale(u, u)

        val rnd = Random(77)
        val respingo = Paint(Paint.ANTI_ALIAS_FLAG)
        val cxFoto = lw / 2f
        val cyFoto = lh / 2f
        repeat(46) {
            val ang = rnd.nextFloat() * 6.2832f
            val dist = 180f + rnd.nextFloat() * 420f
            respingo.color = alfa(cor2, 90 + rnd.nextInt(110))
            c.drawCircle(cxFoto + Math.cos(ang.toDouble()).toFloat() * dist, cyFoto + Math.sin(ang.toDouble()).toFloat() * dist * 0.9f, 4f + rnd.nextFloat() * 20f, respingo)
        }
        respingo.color = alfa(cor2, 70)
        c.drawCircle(cxFoto, cyFoto, 330f, respingo)

        // foto (ou presente)
        val caixa = RectF(140f, 300f, 940f, lh - 300f)
        if (foto != null) {
            val esc = minOf(caixa.width() / foto.width, caixa.height() / foto.height)
            val fw = foto.width * esc
            val fh = foto.height * esc
            val dest = RectF(caixa.centerX() - fw / 2f, caixa.centerY() - fh / 2f, caixa.centerX() + fw / 2f, caixa.centerY() + fh / 2f)
            if (foto.hasAlpha()) {
                c.drawBitmap(foto, Rect(0, 0, foto.width, foto.height), dest, Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    alpha = 90
                    maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
                }
                c.drawRoundRect(RectF(dest.left, dest.top + 10f, dest.right, dest.bottom + 14f), 44f, 44f, sombra)
                val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                c.drawRoundRect(RectF(dest.left - 8f, dest.top - 8f, dest.right + 8f, dest.bottom + 8f), 50f, 50f, borda)
                posterArredondado(c, foto, dest, 44f)
            }
        } else {
            presente(c, cxFoto, caixa.centerY(), minOf(caixa.height(), 460f), p)
        }

        // emojis flutuantes
        emoji(c, "😍", 135f, 330f, 190f, -12f)
        emoji(c, "❤️", 960f, 190f, 120f, 10f)
        emoji(c, "👍", 890f, 480f, 170f, 12f)
        emoji(c, "❤️", 110f, lh - 520f, 130f, -8f)
        emoji(c, "😍", 945f, lh - 440f, 180f, 8f)

        // título
        val tIndique = tp(cor1, 80f, f.titulo, Paint.Align.CENTER, 0.02f)
        c.drawText("Indique", lw / 2f, 92f, tIndique)
        val tAmigos = tp(cor2, 250f, f.titulo, Paint.Align.CENTER, 0.01f)
        c.drawText("AMIGOS", lw / 2f, 300f, tAmigos)
        val tGanhe = tp(cor1, 140f, f.titulo, Paint.Align.CENTER, 0.02f)
        c.drawText("E GANHE", lw / 2f, 420f, tGanhe)

        // logo pequena no canto
        val medida = tp(Color.WHITE, 46f, f.titulo, Paint.Align.LEFT, 0.02f)
        val wVlt = medida.measureText("VLT")
        textoMetal(c, "VLT", 36f, 70f, 46f, f.titulo, p, Paint.Align.LEFT, 0.02f)
        c.drawText("PLAY", 36f + wVlt + 6f, 70f, tp(cor("#222222"), 46f, f.titulo, Paint.Align.LEFT, 0.02f))

        // chamada de baixo
        val amigos = if (qtd == 1) "AMIGO" else "AMIGOS"
        val l1 = listOf(Pair("INDIQUE ", cor1), Pair("$qtd", cor2), Pair(" $amigos E", cor1))
        val l2 = listOf(Pair("GANHE ", cor1), Pair("1 MÊS GRÁTIS", cor2))
        linhaColorida(c, l1, lw / 2f, lh - 225f, 108f, f.titulo, 980f)
        linhaColorida(c, l2, lw / 2f, lh - 120f, 108f, f.titulo, 980f)

        val tn = tp(cor1, 40f, f.negrito, Paint.Align.CENTER)
        ajustar(tn, nota, 940f)
        c.drawText(nota, lw / 2f, lh - 48f, tn)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor1 }
        val larguraNota = tn.measureText(nota)
        c.drawRect(lw / 2f - larguraNota / 2f, lh - 36f, lw / 2f + larguraNota / 2f, lh - 31f, sub)

        c.restore()
    }

    // Uma linha com pedaços de cores diferentes, centralizada, com contorno branco.
    private fun linhaColorida(c: Canvas, partes: List<Pair<String, Int>>, cx: Float, y: Float, tamanhoMax: Float, fonte: Typeface, larguraMax: Float) {
        val tudo = partes.joinToString("") { it.first }
        val tam = tamanhoQueCabe(tudo, fonte, tamanhoMax, larguraMax, 0f)
        val medida = tp(Color.WHITE, tam, fonte)
        val total = medida.measureText(tudo)
        var x = cx - total / 2f
        partes.forEach { (texto, cor) ->
            val contorno = tp(Color.WHITE, tam, fonte)
            contorno.style = Paint.Style.STROKE
            contorno.strokeWidth = tam * 0.12f
            contorno.strokeJoin = Paint.Join.ROUND
            c.drawText(texto, x, y, contorno)
            val preench = tp(cor, tam, fonte)
            c.drawText(texto, x, y, preench)
            x += medida.measureText(texto)
        }
    }

    private fun emoji(c: Canvas, texto: String, cx: Float, cy: Float, tamanho: Float, rotacao: Float) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = tamanho
        paint.textAlign = Paint.Align.CENTER
        c.save()
        c.rotate(rotacao, cx, cy)
        c.drawText(texto, cx, cy + tamanho * 0.35f, paint)
        c.restore()
    }

    private fun misturar(a: Int, b: Int, t: Float): Int {
        val r = Color.red(a) + ((Color.red(b) - Color.red(a)) * t).toInt()
        val g = Color.green(a) + ((Color.green(b) - Color.green(a)) * t).toInt()
        val bl = Color.blue(a) + ((Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    private fun indicacaoPremium(c: Canvas, f: Fontes, p: PaletaBanner, msg: String) {
        val w = LARG_H.toFloat()
        val h = ALT_H.toFloat()
        fundoEscuro(c, w, h, p, 61)

        logo(c, 90f, 170f, 120f, f, p, Paint.Align.LEFT)

        val tam = tamanhoQueCabe("INDIQUE E GANHE", f.titulo, 170f, 820f, 0.02f)
        textoMetal(c, "INDIQUE E GANHE", 90f, 390f, tam, f.titulo, p, Paint.Align.LEFT, 0.02f)

        val sub = tp(Color.WHITE, 120f, f.titulo, Paint.Align.LEFT, 0.02f)
        sombraTexto(c, "1 MÊS GRÁTIS", 90f, 520f, sub)

        desenharParagrafo(c, msg, 90f, 560f, 780, tp(cor("#E6EAF2"), 38f, f.corpo), 3)

        botaoCta(c, RectF(90f, 770f, 470f, 850f), "INDIQUE AGORA", f, p, 34f)
        val url = tp(cor("#C9D1DE"), 28f, f.corpo, Paint.Align.LEFT)
        c.drawText("vltvplay.tech", 510f, 822f, url)

        presente(c, 1220f, 430f, 380f, p)
        iconeQuadrado(c, 1000f, 200f, 70f, 0, p)
        iconeQuadrado(c, 1450f, 220f, 70f, 1, p)
        iconeQuadrado(c, 1460f, 650f, 70f, 2, p)
    }

    private fun indicacaoVidro(c: Canvas, f: Fontes, p: PaletaBanner, msg: String) {
        val w = LARG_H.toFloat()
        val h = ALT_H.toFloat()
        fundoEscuro(c, w, h, p, 71)

        val blob = Paint(Paint.ANTI_ALIAS_FLAG)
        blob.color = alfa(p.escuro, 120)
        c.drawCircle(120f, 780f, 420f, blob)
        blob.color = alfa(p.base, 60)
        c.drawCircle(1500f, 120f, 420f, blob)

        logo(c, 70f, 120f, 70f, f, p, Paint.Align.LEFT)

        val card = RectF(300f, 250f, 1300f, 750f)
        cartaoVidro(c, card, p)
        presente(c, 800f, 235f, 190f, p)

        val tam = tamanhoQueCabe("INDIQUE E GANHE", f.titulo, 130f, 880f, 0.02f)
        textoMetal(c, "INDIQUE E GANHE", 800f, 450f, tam, f.titulo, p, Paint.Align.CENTER, 0.02f)

        val sub = tp(Color.WHITE, 96f, f.titulo, Paint.Align.CENTER, 0.03f)
        sombraTexto(c, "1 MÊS GRÁTIS", 800f, 545f, sub)

        val par = tp(cor("#E6EAF2"), 32f, f.corpo, Paint.Align.CENTER)
        desenharParagrafoCentral(c, msg, 800f, 590f, 840, par, 3)

        botaoCta(c, RectF(560f, 790f, 1040f, 870f), "INDIQUE AGORA", f, p, 34f)
    }

    private fun indicacaoCirculo(c: Canvas, f: Fontes, p: PaletaBanner, msg: String) {
        val w = LARG_H.toFloat()
        val h = ALT_H.toFloat()
        fundoEscuro(c, w, h, p, 81)
        redeDeLinhas(c, w, h, p)
        moldura(c, w, h, p)

        logo(c, 110f, 200f, 110f, f, p, Paint.Align.LEFT)

        val branco = tp(Color.WHITE, 100f, f.titulo, Paint.Align.LEFT, 0.02f)
        c.drawText("INDIQUE UM AMIGO", 110f, 350f, branco)
        textoMetal(c, "E GANHE", 110f, 450f, 100f, f.titulo, p, Paint.Align.LEFT, 0.02f)

        desenharParagrafo(c, msg, 110f, 490f, 700, tp(cor("#E6EAF2"), 32f, f.corpo), 3)

        passo(c, 170f, 720f, "1", "Indique", f, p)
        passo(c, 420f, 720f, "2", "Ele assina", f, p)
        passo(c, 670f, 720f, "3", "Você ganha", f, p)

        val cx = 1190f
        val cy = 430f
        val r = 230f
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, r * 1.5f, alfa(p.base, 110), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, r * 1.5f, glow)
        val miolo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy - 50f, r, p.fundoTopo, p.fundoBase, Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, r, miolo)
        val anel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f
            shader = LinearGradient(cx, cy - r, cx, cy + r, p.claro, p.escuro, Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, r, anel)
        anel.strokeWidth = 2f
        anel.alpha = 150
        c.drawCircle(cx, cy, r - 22f, anel)

        textoMetal(c, "1 MÊS", cx, 440f, 150f, f.titulo, p, Paint.Align.CENTER, 0.02f)
        val gr = tp(Color.WHITE, 96f, f.titulo, Paint.Align.CENTER, 0.06f)
        sombraTexto(c, "GRÁTIS", cx, 535f, gr)

        val url = tp(cor("#C9D1DE"), 28f, f.corpo, Paint.Align.CENTER)
        c.drawText("vltvplay.tech", cx, 740f, url)
    }

    private fun passo(c: Canvas, cx: Float, cy: Float, numero: String, rotulo: String, f: Fontes, p: PaletaBanner) {
        val bola = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx, cy - 36f, cx, cy + 36f, p.claro, p.escuro, Shader.TileMode.CLAMP)
        }
        c.drawCircle(cx, cy, 36f, bola)
        val n = tp(cor("#1A1208"), 44f, f.titulo, Paint.Align.CENTER)
        c.drawText(numero, cx, cy + 15f, n)
        val t = tp(Color.WHITE, 28f, f.negrito, Paint.Align.CENTER)
        c.drawText(rotulo, cx, cy + 82f, t)
    }

    private fun presente(c: Canvas, cx: Float, cy: Float, s: Float, p: PaletaBanner) {
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 120
            maskFilter = BlurMaskFilter(s * 0.08f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(cx - s * 0.5f, cy - s * 0.1f + 12f, cx + s * 0.5f, cy + s * 0.45f + 16f), 14f, 14f, sombra)

        val corpo = RectF(cx - s * 0.5f, cy - s * 0.12f, cx + s * 0.5f, cy + s * 0.45f)
        val pCorpo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(corpo.left, corpo.top, corpo.right, corpo.bottom, p.base, p.escuro, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(corpo, 14f, 14f, pCorpo)

        val tampa = RectF(cx - s * 0.56f, cy - s * 0.34f, cx + s * 0.56f, cy - s * 0.08f)
        val pTampa = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(tampa.left, tampa.top, tampa.right, tampa.bottom, p.claro, p.base, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(tampa, 14f, 14f, pTampa)

        val fita = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(cx, cy - s * 0.34f, cx, cy + s * 0.45f, Color.WHITE, p.claro, Shader.TileMode.CLAMP)
        }
        c.drawRect(cx - s * 0.07f, cy - s * 0.34f, cx + s * 0.07f, cy + s * 0.45f, fita)

        val laco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = s * 0.06f
            color = Color.WHITE
        }
        c.drawOval(RectF(cx - s * 0.27f, cy - s * 0.52f, cx - s * 0.01f, cy - s * 0.32f), laco)
        c.drawOval(RectF(cx + s * 0.01f, cy - s * 0.52f, cx + s * 0.27f, cy - s * 0.32f), laco)
    }

    // ═════════════════════════ FILME / SÉRIE ═════════════════════════

    fun compor(
        context: Context,
        poster: Bitmap,
        categoria: CategoriaBanner,
        sinopse: String,
        fraseChamada: String,
        estilo: EstiloFilme,
        escala: Float = 1f
    ): Bitmap {
        val f = carregarFontes(context)
        val (bmp, c) = novoCanvas(LARG_F, ALT_F, escala)

        when (estilo) {
            EstiloFilme.CINEMA -> filmeCinema(c, f, poster, categoria, sinopse, fraseChamada)
            EstiloFilme.CARTAZ -> filmeCartaz(c, f, poster, categoria, fraseChamada)
            EstiloFilme.NEON -> filmeNeon(c, f, poster, categoria, sinopse, fraseChamada)
        }
        return bmp
    }

    private fun filmeCinema(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, sinopse: String, frase: String) {
        val w = LARG_F.toFloat()
        val h = ALT_F.toFloat()
        fundoDesfocado(c, poster, w, h)
        c.drawColor(alfa(Color.BLACK, 150))
        val vinheta = Paint().apply {
            shader = RadialGradient(w / 2f, h * 0.4f, h * 0.75f, Color.TRANSPARENT, alfa(Color.BLACK, 200), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, vinheta)

        val card = RectF(240f, 150f, 840f, 1050f)
        val brilho = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = alfa(cat.cor, 150)
            maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(card.left - 14f, card.top - 14f, card.right + 14f, card.bottom + 14f), 40f, 40f, brilho)
        posterArredondado(c, poster, card, 30f)

        seloCategoria(c, cat, f.titulo, 44f, 44f)
        logoRedonda(c, f.negrito, w - 70f, 66f)

        textosInferiores(c, f, sinopse, frase, cat.corClara, 1085f, 3)
        rodape(c, f)
    }

    private fun filmeCartaz(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, frase: String) {
        val w = LARG_F.toFloat()
        val h = ALT_F.toFloat()
        fundoDesfocado(c, poster, w, h)
        c.drawColor(alfa(Color.BLACK, 120))

        val area = RectF(140f, 0f, 940f, 1200f)
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 170
            maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRect(RectF(area.left - 10f, 0f, area.right + 10f, area.bottom + 14f), sombra)
        posterArredondado(c, poster, area, 0f)

        val base = Paint().apply {
            shader = LinearGradient(0f, 1120f, 0f, h, Color.TRANSPARENT, alfa(Color.BLACK, 235), Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 1120f, w, h, base)

        seloCategoria(c, cat, f.titulo, 160f, 44f)
        logoRedonda(c, f.negrito, w - 70f, 66f)

        val texto = if (frase.isBlank()) "ASSISTA AGORA NA VLTV PLAY" else frase.trim().uppercase(Locale("pt", "BR"))
        val pill = RectF(150f, 1215f, 930f, 1295f)
        val sombraPill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 140
            maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(pill.left, pill.top + 6f, pill.right, pill.bottom + 10f), 40f, 40f, sombraPill)
        val fundoPill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(pill.left, pill.top, pill.left, pill.bottom, cat.corClara, cat.cor, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(pill, 40f, 40f, fundoPill)
        val t = tp(Color.WHITE, 38f, f.titulo, Paint.Align.CENTER, 0.06f)
        ajustar(t, texto, pill.width() - 60f)
        c.drawText(texto, pill.centerX(), pill.centerY() + t.textSize * 0.34f, t)

        val url = tp(cor("#B8BEC9"), 24f, f.corpo, Paint.Align.CENTER)
        c.drawText("vltvplay.tech", w / 2f, 1328f, url)
    }

    private fun filmeNeon(c: Canvas, f: Fontes, poster: Bitmap, cat: CategoriaBanner, sinopse: String, frase: String) {
        val w = LARG_F.toFloat()
        val h = ALT_F.toFloat()
        fundoDesfocado(c, poster, w, h)
        c.drawColor(alfa(Color.BLACK, 140))
        c.drawColor(alfa(cat.cor, 70))

        val card = RectF(260f, 150f, 820f, 990f)
        val neonLargo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 16f
            color = cat.corClara
            maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(card, 26f, 26f, neonLargo)
        posterArredondado(c, poster, card, 26f)
        val neonFino = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = cat.corClara
        }
        c.drawRoundRect(card, 26f, 26f, neonFino)

        seloCategoria(c, cat, f.titulo, 44f, 44f)
        logoRedonda(c, f.negrito, w - 70f, 66f)

        val painel = RectF(60f, 1035f, 1020f, 1290f)
        val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alfa(Color.BLACK, 165) }
        c.drawRoundRect(painel, 28f, 28f, fundo)
        val barra = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cat.corClara }
        c.drawRoundRect(RectF(painel.left + 24f, painel.top + 28f, painel.left + 32f, painel.bottom - 28f), 4f, 4f, barra)

        var y = painel.top + 34f
        val larg = (painel.width() - 100f).toInt()
        val fraseTxt = frase.trim()
        if (fraseTxt.isNotEmpty()) {
            val fp = tp(cat.corClara, 38f, f.negrito)
            val fl = layoutDe(fraseTxt, fp, larg, 1)
            c.save()
            c.translate(painel.left + 60f, y)
            fl.draw(c)
            c.restore()
            y += fl.height + 8f
        }
        val sin = sinopse.trim()
        if (sin.isNotEmpty()) {
            val sp = tp(cor("#F0F0F0"), 28f, f.corpo)
            val sl = layoutDe(sin, sp, larg, if (fraseTxt.isNotEmpty()) 3 else 4)
            c.save()
            c.translate(painel.left + 60f, y)
            sl.draw(c)
            c.restore()
        }
        rodape(c, f)
    }

    private fun textosInferiores(c: Canvas, f: Fontes, sinopse: String, frase: String, corFrase: Int, yInicio: Float, maxLinhasSinopse: Int) {
        val margem = 60f
        val larg = (LARG_F - margem * 2).toInt()
        var y = yInicio
        val fraseTxt = frase.trim()
        if (fraseTxt.isNotEmpty()) {
            val fp = tp(corFrase, 42f, f.negrito)
            val fl = layoutDe(fraseTxt, fp, larg, 1)
            c.save()
            c.translate(margem, y)
            fl.draw(c)
            c.restore()
            y += fl.height + 10f
        }
        val sin = sinopse.trim()
        if (sin.isNotEmpty()) {
            val sp = tp(cor("#EDEDED"), 30f, f.corpo)
            val sl = layoutDe(sin, sp, larg, maxLinhasSinopse)
            c.save()
            c.translate(margem, y)
            sl.draw(c)
            c.restore()
        }
    }

    private fun rodape(c: Canvas, f: Fontes) {
        val url = tp(cor("#B8BEC9"), 24f, f.corpo, Paint.Align.CENTER)
        c.drawText("vltvplay.tech", LARG_F / 2f, ALT_F - 22f, url)
    }

    private fun layoutDe(texto: String, paint: TextPaint, largura: Int, maxLinhas: Int): StaticLayout =
        StaticLayout.Builder
            .obtain(texto, 0, texto.length, paint, largura)
            .setMaxLines(maxLinhas)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(4f, 1.08f)
            .build()

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

    // Desenha o pôster preenchendo o retângulo (corta o excesso), com cantos arredondados.
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

    private fun seloCategoria(c: Canvas, cat: CategoriaBanner, fonte: Typeface, x: Float, y: Float) {
        val textoPaint = tp(Color.WHITE, 42f, fonte, Paint.Align.LEFT, 0.06f)
        val larguraTexto = textoPaint.measureText(cat.rotulo)
        val padH = 32f
        val padV = 14f
        val fundo = RectF(x, y, x + larguraTexto + padH * 2, y + textoPaint.textSize + padV * 2)

        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 110
            maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(fundo.left, fundo.top + 6f, fundo.right, fundo.bottom + 10f), 14f, 14f, sombra)

        val fundoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(fundo.left, fundo.top, fundo.left, fundo.bottom, cat.corClara, cat.cor, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(fundo, 14f, 14f, fundoPaint)

        val brilho = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 70
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        c.drawRoundRect(RectF(fundo.left + 1f, fundo.top + 1f, fundo.right - 1f, fundo.bottom - 1f), 13f, 13f, brilho)

        c.drawText(cat.rotulo, fundo.left + padH, fundo.top + padV + textoPaint.textSize - 10f, textoPaint)
    }

    // Ícone do app (círculo vermelho + play) com "VLTV PLAY" embaixo.
    private fun logoRedonda(c: Canvas, fonteNome: Typeface, cx: Float, cy: Float) {
        val raio = 40f
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 90
            maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawCircle(cx, cy + 3f, raio, sombra)
        val circulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor("#E50914") }
        c.drawCircle(cx, cy, raio, circulo)
        val play = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val path = Path().apply {
            moveTo(cx - 13f, cy - 18f)
            lineTo(cx + 18f, cy)
            lineTo(cx - 13f, cy + 18f)
            close()
        }
        c.drawPath(path, play)
        val nome = tp(Color.WHITE, 22f, Typeface.create(fonteNome, Typeface.BOLD), Paint.Align.CENTER, 0.04f)
        nome.setShadowLayer(6f, 0f, 2f, cor("#AA000000"))
        c.drawText("VLTV PLAY", cx, cy + raio + 30f, nome)
    }

    // ═════════════════════════ PEÇAS COMPARTILHADAS ═════════════════════════

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

    private fun tamanhoQueCabe(texto: String, fonte: Typeface, tamanhoMax: Float, larguraMax: Float, espaco: Float): Float {
        val paint = tp(Color.WHITE, tamanhoMax, fonte, Paint.Align.LEFT, espaco)
        ajustar(paint, texto, larguraMax)
        return paint.textSize
    }

    private fun shaderMetal(p: PaletaBanner, topo: Float, base: Float): Shader =
        LinearGradient(
            0f, topo, 0f, base,
            intArrayOf(p.claro, p.base, p.escuro, p.base),
            floatArrayOf(0f, 0.45f, 0.8f, 1f),
            Shader.TileMode.CLAMP
        )

    private fun textoMetal(c: Canvas, s: String, x: Float, y: Float, tamanho: Float, fonte: Typeface, p: PaletaBanner, align: Paint.Align, espaco: Float) {
        val sombra = tp(Color.BLACK, tamanho, fonte, align, espaco)
        sombra.alpha = 120
        c.drawText(s, x + 3f, y + 5f, sombra)
        val paint = tp(Color.WHITE, tamanho, fonte, align, espaco)
        paint.shader = shaderMetal(p, y - tamanho * 0.78f, y)
        c.drawText(s, x, y, paint)
    }

    private fun sombraTexto(c: Canvas, s: String, x: Float, y: Float, paint: TextPaint) {
        val sombra = TextPaint(paint)
        sombra.color = Color.BLACK
        sombra.alpha = 130
        c.drawText(s, x + 3f, y + 5f, sombra)
        c.drawText(s, x, y, paint)
    }

    private fun partesPreco(valor: Double): Pair<String, String> {
        val total = Math.round(valor * 100.0)
        val inteiro = total / 100
        val cent = total % 100
        return Pair(inteiro.toString(), String.format(Locale("pt", "BR"), ",%02d", cent))
    }

    // "R$" pequeno + valor grande + centavos pequenos, tudo metálico.
    private fun precoGrande(c: Canvas, valor: Double, xRef: Float, yBase: Float, tamanho: Float, f: Fontes, p: PaletaBanner, centralizar: Boolean) {
        val (inteiro, cent) = partesPreco(valor)
        val pequeno = tamanho * 0.4f
        val pRs = tp(Color.WHITE, pequeno, f.titulo)
        val pInt = tp(Color.WHITE, tamanho, f.titulo)
        val pCent = tp(Color.WHITE, pequeno, f.titulo)
        val wRs = pRs.measureText("R$")
        val wInt = pInt.measureText(inteiro)
        val wCent = pCent.measureText(cent)
        val gap = tamanho * 0.05f
        val total = wRs + gap + wInt + gap + wCent
        val x0 = if (centralizar) xRef - total / 2f else xRef
        val subir = tamanho * 0.42f

        textoMetal(c, "R$", x0, yBase - subir, pequeno, f.titulo, p, Paint.Align.LEFT, 0f)
        textoMetal(c, inteiro, x0 + wRs + gap, yBase, tamanho, f.titulo, p, Paint.Align.LEFT, 0f)
        textoMetal(c, cent, x0 + wRs + gap + wInt + gap, yBase - subir, pequeno, f.titulo, p, Paint.Align.LEFT, 0f)
    }

    private fun logo(c: Canvas, x: Float, yBase: Float, tamanho: Float, f: Fontes, p: PaletaBanner, align: Paint.Align) {
        val medida = tp(Color.WHITE, tamanho, f.titulo, Paint.Align.LEFT, 0.02f)
        val w1 = medida.measureText("VLT")
        val w2 = medida.measureText("PLAY")
        val gap = tamanho * 0.14f
        val total = w1 + gap + w2
        val x0 = if (align == Paint.Align.CENTER) x - total / 2f else x

        textoMetal(c, "VLT", x0, yBase, tamanho, f.titulo, p, Paint.Align.LEFT, 0.02f)
        val branco = tp(Color.WHITE, tamanho, f.titulo, Paint.Align.LEFT, 0.02f)
        sombraTexto(c, "PLAY", x0 + w1 + gap, yBase, branco)
    }

    private fun botaoCta(c: Canvas, r: RectF, texto: String, f: Fontes, p: PaletaBanner, tamanho: Float) {
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 140
            maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(r.left, r.top + 6f, r.right, r.bottom + 10f), r.height() / 2f, r.height() / 2f, sombra)

        val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(r.left, 0f, r.right, 0f, intArrayOf(p.base, p.claro, p.base), null, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, fundo)

        val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.WHITE
            alpha = 90
        }
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, borda)

        val t = tp(cor("#1A1208"), tamanho, f.negrito, Paint.Align.CENTER, 0.03f)
        ajustar(t, texto, r.width() - 50f)
        c.drawText(texto, r.centerX(), r.centerY() + t.textSize * 0.35f, t)
    }

    private fun desenharParagrafo(c: Canvas, texto: String, x: Float, y: Float, largura: Int, paint: TextPaint, maxLinhas: Int) {
        val layout = layoutDe(texto, paint, largura, maxLinhas)
        c.save()
        c.translate(x, y)
        layout.draw(c)
        c.restore()
    }

    private fun desenharParagrafoCentral(c: Canvas, texto: String, cx: Float, y: Float, largura: Int, paint: TextPaint, maxLinhas: Int) {
        paint.textAlign = Paint.Align.LEFT
        val layout = layoutDe(texto, paint, largura, maxLinhas)
        c.save()
        c.translate(cx - largura / 2f, y)
        layout.draw(c)
        c.restore()
    }

    private fun fundoEscuro(c: Canvas, w: Float, h: Float, p: PaletaBanner, seed: Int) {
        val base = Paint().apply {
            shader = LinearGradient(0f, 0f, w * 0.45f, h, p.fundoTopo, p.fundoBase, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, base)

        val faixa = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 10 }
        val f1 = Path().apply {
            moveTo(0f, h * 0.75f); lineTo(w * 0.5f, 0f); lineTo(w * 0.72f, 0f); lineTo(w * 0.18f, h); lineTo(0f, h); close()
        }
        c.drawPath(f1, faixa)
        val f2 = Path().apply {
            moveTo(w * 0.35f, h); lineTo(w * 0.9f, h * 0.1f); lineTo(w, h * 0.1f); lineTo(w, h * 0.5f); lineTo(w * 0.7f, h); close()
        }
        c.drawPath(f2, faixa)

        val brilho = Paint().apply {
            shader = RadialGradient(w * 0.82f, h * 0.2f, w * 0.5f, alfa(p.base, 55), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, brilho)

        val rnd = Random(seed)
        val ponto = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(36) {
            ponto.color = alfa(p.claro, 40 + rnd.nextInt(90))
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h, 1.5f + rnd.nextFloat() * 3f, ponto)
        }
    }

    private fun redeDeLinhas(c: Canvas, w: Float, h: Float, p: PaletaBanner) {
        val rnd = Random(99)
        val pontos = List(16) { Pair(w * 0.35f + rnd.nextFloat() * w * 0.65f, rnd.nextFloat() * h) }
        val linha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = alfa(p.claro, 28)
            strokeWidth = 2f
        }
        for (i in pontos.indices) {
            for (j in i + 1 until pontos.size) {
                val dx = pontos[i].first - pontos[j].first
                val dy = pontos[i].second - pontos[j].second
                if (dx * dx + dy * dy < 320f * 320f) {
                    c.drawLine(pontos[i].first, pontos[i].second, pontos[j].first, pontos[j].second, linha)
                }
            }
        }
        val bola = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alfa(p.claro, 90) }
        pontos.forEach { c.drawCircle(it.first, it.second, 4f, bola) }
    }

    private fun moldura(c: Canvas, w: Float, h: Float, p: PaletaBanner) {
        val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            shader = LinearGradient(0f, 0f, w, h, p.claro, p.escuro, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(RectF(36f, 36f, w - 36f, h - 36f), 40f, 40f, borda)
    }

    private fun cartaoVidro(c: Canvas, r: RectF, p: PaletaBanner) {
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 110
            maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(r.left, r.top + 10f, r.right, r.bottom + 14f), 40f, 40f, sombra)

        val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(r.left, r.top, r.right, r.bottom, alfa(Color.WHITE, 52), alfa(p.base, 45), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(r, 40f, 40f, fundo)
        val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            shader = LinearGradient(r.left, r.top, r.right, r.bottom, p.claro, p.escuro, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(r, 40f, 40f, borda)
    }

    // Ícone flutuante: 0 = play, 1 = bola de futebol, 2 = TV
    private fun iconeQuadrado(c: Canvas, cx: Float, cy: Float, lado: Float, tipo: Int, p: PaletaBanner) {
        val r = RectF(cx - lado / 2f, cy - lado / 2f, cx + lado / 2f, cy + lado / 2f)
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 120
            maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(r.left, r.top + 6f, r.right, r.bottom + 8f), lado * 0.22f, lado * 0.22f, sombra)
        val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(r.left, r.top, r.right, r.bottom, p.claro, p.escuro, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(r, lado * 0.22f, lado * 0.22f, fundo)

        val glifo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor("#2A1B10") }
        when (tipo) {
            0 -> {
                val path = Path().apply {
                    moveTo(cx - lado * 0.14f, cy - lado * 0.22f)
                    lineTo(cx + lado * 0.24f, cy)
                    lineTo(cx - lado * 0.14f, cy + lado * 0.22f)
                    close()
                }
                c.drawPath(path, glifo)
            }
            1 -> {
                c.drawCircle(cx, cy, lado * 0.26f, glifo)
                val miolo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.claro }
                c.drawCircle(cx, cy, lado * 0.10f, miolo)
            }
            else -> {
                c.drawRoundRect(RectF(cx - lado * 0.28f, cy - lado * 0.2f, cx + lado * 0.28f, cy + lado * 0.12f), 6f, 6f, glifo)
                c.drawRect(cx - lado * 0.12f, cy + lado * 0.16f, cx + lado * 0.12f, cy + lado * 0.22f, glifo)
            }
        }
    }

    // Quadradinho com cara de capa de filme (gradiente + "figura" clara).
    private fun tile(c: Canvas, r: RectF, rnd: Random) {
        val tom = tonsTela[rnd.nextInt(tonsTela.size)]
        val raio = minOf(r.width(), r.height()) * 0.07f
        val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(r.left, r.top, r.right, r.bottom, tom[0], tom[1], Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(r, raio, raio, fundo)
        val figura = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alfa(Color.WHITE, 38) }
        c.drawOval(
            RectF(
                r.left + r.width() * (0.35f + rnd.nextFloat() * 0.2f),
                r.top + r.height() * 0.12f,
                r.left + r.width() * 0.85f,
                r.top + r.height() * 0.75f
            ),
            figura
        )
        val rodape = Paint().apply {
            shader = LinearGradient(0f, r.top + r.height() * 0.55f, 0f, r.bottom, Color.TRANSPARENT, alfa(Color.BLACK, 150), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(r, raio, raio, rodape)
    }

    // Tela de um aparelho com a "interface" do app (barra, destaque, capas).
    private fun tela(c: Canvas, r: RectF, p: PaletaBanner, f: Fontes, seed: Int) {
        val rnd = Random(seed)
        val fundo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor("#0B0E15") }
        val raio = minOf(r.width(), r.height()) * 0.05f
        c.drawRoundRect(r, raio, raio, fundo)

        val pad = r.width() * 0.035f
        val barra = r.height() * 0.09f
        val t = tp(p.base, barra * 0.55f, f.titulo)
        c.drawText("VLT PLAY", r.left + pad * 1.5f, r.top + barra * 0.75f, t)

        val topoHero = r.top + barra
        val altHero = (r.height() - barra) * 0.42f
        tile(c, RectF(r.left + pad, topoHero, r.right - pad, topoHero + altHero), rnd)

        val colunas = if (r.width() > r.height()) 5 else 3
        val linhas = 2
        val topoGrade = topoHero + altHero + pad
        val altTile = (r.bottom - pad - topoGrade - pad * (linhas - 1)) / linhas
        val largTile = (r.width() - pad * (colunas + 1)) / colunas
        for (l in 0 until linhas) {
            for (col in 0 until colunas) {
                val left = r.left + pad + col * (largTile + pad)
                val top = topoGrade + l * (altTile + pad)
                tile(c, RectF(left, top, left + largTile, top + altTile), rnd)
            }
        }
    }

    // tipo: 0 = TV, 1 = tablet, 2 = celular
    private fun aparelho(c: Canvas, r: RectF, tipo: Int, p: PaletaBanner, f: Fontes, seed: Int) {
        val canto = if (tipo == 2) r.width() * 0.13f else 16f
        val sombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 150
            maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(r.left, r.top + 12f, r.right, r.bottom + 16f), canto, canto, sombra)

        val moldura = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor("#151922") }
        c.drawRoundRect(r, canto, canto, moldura)
        val borda = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = alfa(p.base, 170)
        }
        c.drawRoundRect(r, canto, canto, borda)

        val inset = if (tipo == 0) r.width() * 0.012f + 4f else r.width() * 0.04f
        tela(c, RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset), p, f, seed)

        if (tipo == 0) {
            val pe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor("#222733") }
            c.drawRect(r.centerX() - 12f, r.bottom, r.centerX() + 12f, r.bottom + 22f, pe)
            c.drawRoundRect(RectF(r.centerX() - r.width() * 0.16f, r.bottom + 18f, r.centerX() + r.width() * 0.16f, r.bottom + 30f), 6f, 6f, pe)
        }
    }
}
