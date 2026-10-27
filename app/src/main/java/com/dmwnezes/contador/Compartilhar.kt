package com.dmwnezes.contador

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import kotlin.math.abs

/** Gera um card 4:5 (formato do feed do Instagram) e abre o menu de compartilhar. */
fun compartilhar(ctx: Context, c: Contador, agora: LocalDateTime) {
    val arquivo = File(ctx.cacheDir, "compartilhar").apply { mkdirs() }.resolve("contador.png")
    arquivo.outputStream().use { desenharCard(c, agora).compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.arquivos", arquivo)
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, legenda(c, agora))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(envio, "Compartilhar contador").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun legenda(c: Contador, agora: LocalDateTime): String {
    val d = diasAte(c.data, agora.toLocalDate())
    val n = abs(d)
    return when {
        d > 0 -> "${if (n == 1L) "Falta 1 dia" else "Faltam ${numero(n)} dias"} para ${c.nome}"
        d == 0L -> "É hoje: ${c.nome}!"
        else -> "${if (n == 1L) "1 dia" else "${numero(n)} dias"} desde ${c.nome}"
    }
}

private fun desenharCard(c: Contador, agora: LocalDateTime): Bitmap {
    val w = 1080
    val h = 1350
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val d = diasAte(c.data, agora.toLocalDate())
        val acc = corArgb(c)

    // Fundo vidro escuro com bolhas de cor
    cv.drawColor(0xFF0B0912.toInt())
    fun bolha(cor: Int, x: Float, y: Float, r: Float) {
        val tinta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(x, y, r, intArrayOf((cor and 0x00FFFFFF) or 0x99000000.toInt(), cor and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        }
        cv.drawCircle(x, y, r, tinta)
    }
    bolha(acc, 160f, 220f, 760f)
    bolha(0xFF6C5CFF.toInt(), 1000f, 760f, 700f)
    bolha(0xFF2EC5FF.toInt(), 360f, 1380f, 600f)
    // Cartão de vidro
    cv.drawRoundRect(RectF(50f, 70f, w - 50f, h - 170f), 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1AFFFFFF })
    cv.drawRoundRect(RectF(50f, 70f, w - 50f, h - 170f), 64f, 64f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF; style = Paint.Style.STROKE; strokeWidth = 3f })

    val m = 90f
    // Selo de fase
    val chip = when { d > 0 -> "FALTAM"; d == 0L -> "É HOJE"; else -> "JÁ SE PASSARAM" }
    val chipP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF160C26.toInt(); textSize = 34f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f }
    val cw = chipP.measureText(chip)
    cv.drawRoundRect(RectF(m, 120f, m + cw + 56f, 184f), 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = acc })
    cv.drawText(chip, m + 28f, 164f, chipP)

    // Nome (quebra em até 3 linhas)
    val nomeP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 84f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val nome = StaticLayout.Builder.obtain(c.nome, 0, c.nome.length, nomeP, (w - 2 * m).toInt())
        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(3).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
    cv.save(); cv.translate(m, 240f); nome.draw(cv); cv.restore()
    var y = 240f + nome.height + 60f

    val dataP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xB3FFFFFF.toInt(); textSize = 42f }
    cv.drawText(dataTexto(c), m, y, dataP)
    y += 300f

    // Número grande
    val txt = if (d == 0L) "HOJE" else numero(abs(d))
    val numP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = when { txt.length <= 3 -> 340f; txt.length <= 5 -> 260f; else -> 190f }
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(40f, 0f, 0f, 0x55FFFFFF)
    }
    cv.drawText(txt, m - 10f, y, numP)
    if (d != 0L) {
        val uniP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 64f; typeface = Typeface.DEFAULT_BOLD }
        cv.drawText(if (abs(d) == 1L) "dia" else "dias", m + numP.measureText(txt) + 10f, y, uniP)
    }

    val rodape = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textSize = 34f; typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.2f }
    cv.drawText("CONTADOR", m, h - 90f, rodape)
    return bmp
}
