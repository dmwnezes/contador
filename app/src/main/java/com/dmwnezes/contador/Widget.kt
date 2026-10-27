package com.dmwnezes.contador

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import kotlin.math.abs

private fun rotuloDias(d: Long): String {
    val n = abs(d)
    return when {
        d > 0 -> if (n == 1L) "dia falta" else "dias faltam"
        d == 0L -> "é hoje!"
        else -> if (n == 1L) "dia desde" else "dias desde"
    }
}

private fun numeroWidget(d: Long) = if (d == 0L) "Hoje" else numero(abs(d))

private fun abrirApp(ctx: Context) =
    PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

private fun RemoteViews.barra(id: Int, c: Contador, hoje: LocalDate) {
    val p = progresso(c, hoje)
    if (p == null || diasAte(c.data, hoje) <= 0) { setViewVisibility(id, View.GONE); return }
    setViewVisibility(id, View.VISIBLE)
    setProgressBar(id, 100, (p * 100).toInt(), false)
    if (Build.VERSION.SDK_INT >= 31) setColorStateList(id, "setProgressTintList", ColorStateList.valueOf(corArgb(c)))
}

/** Widget do contador em destaque: quadrado (2×2) e, se esticado na largura, a versão grande (4×2). */
class ContadorWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, gerente: AppWidgetManager, ids: IntArray) = atualizarTodos(ctx, Armazem(ctx).ler())

    companion object {
        /** Atualiza os três tipos de widget. Chamado ao salvar, no aviso das 9h e pelo sistema a cada 30 min. */
        fun atualizarTodos(ctx: Context, estado: Estado) {
            val g = AppWidgetManager.getInstance(ctx)
            val hoje = LocalDate.now()
            val c = estado.contadores.find { it.id == estado.destaque } ?: estado.contadores.firstOrNull()

            g.getAppWidgetIds(ComponentName(ctx, ContadorWidget::class.java)).takeIf { it.isNotEmpty() }?.let { ids ->
                val pequeno = pequeno(ctx, c, hoje)
                val v = if (Build.VERSION.SDK_INT >= 31) RemoteViews(mapOf(SizeF(100f, 100f) to pequeno, SizeF(250f, 110f) to grande(ctx, c, estado, hoje))) else pequeno
                g.updateAppWidget(ids, v)
            }
            g.getAppWidgetIds(ComponentName(ctx, ListaWidget::class.java)).takeIf { it.isNotEmpty() }?.let { ids ->
                g.updateAppWidget(ids, lista(ctx, estado, hoje))
            }
            g.getAppWidgetIds(ComponentName(ctx, FaixaWidget::class.java)).takeIf { it.isNotEmpty() }?.let { ids ->
                g.updateAppWidget(ids, faixa(ctx, c, hoje))
            }
        }

        private fun pequeno(ctx: Context, c: Contador?, hoje: LocalDate) = RemoteViews(ctx.packageName, R.layout.widget_pequeno).apply {
            setOnClickPendingIntent(R.id.wRaiz, abrirApp(ctx))
            if (c == null) {
                setTextViewText(R.id.wNome, "Contador"); setTextViewText(R.id.wNumero, "+"); setTextViewText(R.id.wRotulo, "crie um contador")
                setViewVisibility(R.id.wBarra, View.GONE); return@apply
            }
            val d = diasAte(c.data, hoje)
            setTextViewText(R.id.wNome, c.nome)
            setTextViewText(R.id.wNumero, numeroWidget(d))
            setTextViewText(R.id.wRotulo, rotuloDias(d))
            setInt(R.id.wPonto, "setColorFilter", corArgb(c))
            barra(R.id.wBarra, c, hoje)
        }

        private fun grande(ctx: Context, c: Contador?, estado: Estado, hoje: LocalDate) = RemoteViews(ctx.packageName, R.layout.widget_grande).apply {
            setOnClickPendingIntent(R.id.wRaiz, abrirApp(ctx))
            if (c == null) {
                setTextViewText(R.id.wNome, "Contador"); setTextViewText(R.id.wData, "Toque para criar um contador")
                setTextViewText(R.id.wNumero, "+"); setTextViewText(R.id.wRotulo, ""); setViewVisibility(R.id.wBarra, View.GONE)
                setTextViewText(R.id.wProximo, ""); return@apply
            }
            val d = diasAte(c.data, hoje)
            setTextViewText(R.id.wNome, c.nome)
            setTextViewText(R.id.wData, dataTexto(c))
            setTextViewText(R.id.wNumero, numeroWidget(d))
            setTextViewText(R.id.wRotulo, rotuloDias(d))
            setInt(R.id.wPonto, "setColorFilter", corArgb(c))
            barra(R.id.wBarra, c, hoje)
            val p = progresso(c, hoje)?.takeIf { d > 0 }
            val prox = ordenar(estado.contadores, hoje).firstOrNull { it.id != c.id && diasAte(it.data, hoje) >= 0 }
            setTextViewText(R.id.wProximo, listOfNotNull(
                p?.let { "${(it * 100).toInt()}% do caminho" },
                prox?.let { "depois: ${it.nome} em ${numero(diasAte(it.data, hoje))}d" },
            ).joinToString("  ·  "))
        }

        private fun lista(ctx: Context, estado: Estado, hoje: LocalDate) = RemoteViews(ctx.packageName, R.layout.widget_lista).apply {
            setOnClickPendingIntent(R.id.wRaiz, abrirApp(ctx))
            val futuros = ordenar(estado.contadores, hoje).filter { diasAte(it.data, hoje) >= 0 }.take(3)
            val linhas = listOf(Triple(R.id.wL1, R.id.wN1, R.id.wD1), Triple(R.id.wL2, R.id.wN2, R.id.wD2), Triple(R.id.wL3, R.id.wN3, R.id.wD3))
            linhas.forEachIndexed { i, (linha, nome, dias) ->
                val c = futuros.getOrNull(i)
                if (c == null) { setViewVisibility(linha, View.GONE); return@forEachIndexed }
                setViewVisibility(linha, View.VISIBLE)
                setTextViewText(nome, c.nome)
                setTextViewText(dias, numeroWidget(diasAte(c.data, hoje)))
                setTextColor(dias, corArgb(c))
            }
            setViewVisibility(R.id.wVazio, if (futuros.isEmpty()) View.VISIBLE else View.GONE)
        }

        private fun faixa(ctx: Context, c: Contador?, hoje: LocalDate) = RemoteViews(ctx.packageName, R.layout.widget_faixa).apply {
            setOnClickPendingIntent(R.id.wRaiz, abrirApp(ctx))
            if (c == null) { setTextViewText(R.id.wTexto, "Crie um contador"); setTextViewText(R.id.wExtra, ""); return@apply }
            val d = diasAte(c.data, hoje)
            val n = numero(abs(d))
            setTextViewText(R.id.wTexto, when {
                d > 0 -> "${c.nome} em $n ${if (abs(d) == 1L) "dia" else "dias"}"
                d == 0L -> "Hoje: ${c.nome}"
                else -> "$n ${if (abs(d) == 1L) "dia" else "dias"} desde ${c.nome}"
            })
            setTextViewText(R.id.wExtra, progresso(c, hoje)?.takeIf { d > 0 }?.let { "${(it * 100).toInt()}%" } ?: "")
            setInt(R.id.wPonto, "setColorFilter", corArgb(c))
        }
    }
}

class ListaWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, gerente: AppWidgetManager, ids: IntArray) = ContadorWidget.atualizarTodos(ctx, Armazem(ctx).ler())
}

class FaixaWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, gerente: AppWidgetManager, ids: IntArray) = ContadorWidget.atualizarTodos(ctx, Armazem(ctx).ler())
}
