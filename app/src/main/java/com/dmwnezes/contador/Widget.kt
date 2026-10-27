package com.dmwnezes.contador

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

const val ACAO_TIQUE = "com.dmwnezes.contador.TIQUE_WIDGET"

private fun abrirApp(ctx: Context) =
    PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

private fun RemoteViews.barra(id: Int, c: Contador, hoje: LocalDate) {
    val p = progresso(c, hoje)
    if (p == null || diasAte(c.data, hoje) <= 0) { setViewVisibility(id, View.GONE); return }
    setViewVisibility(id, View.VISIBLE)
    setProgressBar(id, 100, (p * 100).toInt(), false)
    if (Build.VERSION.SDK_INT >= 31) setColorStateList(id, "setProgressTintList", ColorStateList.valueOf(corArgb(c)))
}

/**
 * Liga o Chronometer do Android: ele mesmo redesenha os segundos na tela inicial, sem o app rodar.
 * Mostra o que sobra do dia (H:MM:SS); os dias inteiros ficam no texto ao lado.
 */
private fun RemoteViews.relogio(id: Int, r: RelogioWidget?, cor: Int) {
    if (r == null) { setViewVisibility(id, View.GONE); return }
    setViewVisibility(id, View.VISIBLE)
    val agora = SystemClock.elapsedRealtime()
    val base = if (r.regressivo) agora + r.resto * 1000 else agora - r.resto * 1000
    setChronometer(id, base, null, true)
    setChronometerCountDown(id, r.regressivo)
    setTextColor(id, cor)
}

/** Widget do contador em destaque: quadrado (2×2) e, esticado na largura, a versão grande (4×2). */
class ContadorWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, gerente: AppWidgetManager, ids: IntArray) = atualizarTodos(ctx, Armazem(ctx).ler())

    companion object {
        /** Redesenha os três widgets e agenda o próximo redesenho (virada do dia ou fim do relógio). */
        fun atualizarTodos(ctx: Context, estado: Estado) {
            val g = AppWidgetManager.getInstance(ctx)
            val agora = LocalDateTime.now()
            val hoje = agora.toLocalDate()
            val c = estado.contadores.find { it.id == estado.destaque } ?: estado.contadores.firstOrNull()

            val destaque = g.getAppWidgetIds(ComponentName(ctx, ContadorWidget::class.java))
            val giro = g.getAppWidgetIds(ComponentName(ctx, ListaWidget::class.java))
            val faixa = g.getAppWidgetIds(ComponentName(ctx, FaixaWidget::class.java))

            if (destaque.isNotEmpty()) {
                val p = pequeno(ctx, c, agora)
                g.updateAppWidget(destaque, if (Build.VERSION.SDK_INT >= 31) RemoteViews(mapOf(SizeF(100f, 100f) to p, SizeF(250f, 110f) to grande(ctx, c, agora))) else p)
            }
            if (giro.isNotEmpty()) g.updateAppWidget(giro, lista(ctx, estado, hoje))
            if (faixa.isNotEmpty()) g.updateAppWidget(faixa, faixa(ctx, c, agora))

            if (destaque.isNotEmpty() || giro.isNotEmpty() || faixa.isNotEmpty()) agendarTique(ctx, proximoTique(c, agora))
        }

        private fun agendarTique(ctx: Context, quando: LocalDateTime) {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                ctx, 7, Intent(ctx, AvisoReceiver::class.java).setAction(ACAO_TIQUE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val ms = quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 500
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am.setWindow(AlarmManager.RTC_WAKEUP, ms, 60_000, pi)
        }

        private fun pequeno(ctx: Context, c: Contador?, agora: LocalDateTime) = RemoteViews(ctx.packageName, R.layout.widget_pequeno).apply {
            setOnClickPendingIntent(R.id.wRaiz, abrirApp(ctx))
            if (c == null) {
                setTextViewText(R.id.wNome, "Contador"); setTextViewText(R.id.wNumero, "+"); setTextViewText(R.id.wRotulo, "crie um contador")
                setViewVisibility(R.id.wBarra, View.GONE); setViewVisibility(R.id.wRelogio, View.GONE); return@apply
            }
            val r = relogioWidget(c, agora)
            setTextViewText(R.id.wNome, c.nome)
            setTextViewText(R.id.wNumero, if (r == null) "Hoje" else numero(r.dias))
            setTextViewText(R.id.wRotulo, when { r == null -> "é agora!"; r.dias == 1L -> "dia"; else -> "dias" })
            setInt(R.id.wPonto, "setColorFilter", corArgb(c))
            relogio(R.id.wRelogio, r, corArgb(c))
            barra(R.id.wBarra, c, agora.toLocalDate())
        }

        private fun grande(ctx: Context, c: Contador?, agora: LocalDateTime) = RemoteViews(ctx.packageName, R.layout.widget_grande).apply {
            setOnClickPendingIntent(R.id.wRaiz, abrirApp(ctx))
            if (c == null) {
                setTextViewText(R.id.wNome, "Contador"); setTextViewText(R.id.wData, "Toque para criar um contador")
                setTextViewText(R.id.wNumero, "+"); setViewVisibility(R.id.wBlocoRelogio, View.GONE)
                setViewVisibility(R.id.wBarra, View.GONE); setTextViewText(R.id.wPct, ""); setViewVisibility(R.id.wChip, View.GONE); return@apply
            }
            val r = relogioWidget(c, agora)
            val hoje = agora.toLocalDate()
            setTextViewText(R.id.wNome, c.nome)
            setTextViewText(R.id.wData, dataTexto(c))
            setTextViewText(R.id.wChip, if (r?.regressivo == false) "● DESDE" else "● AO VIVO")
            setTextColor(R.id.wChip, corArgb(c))
            setTextViewText(R.id.wNumero, if (r == null) "Hoje" else numero(r.dias))
            setTextViewText(R.id.wRotulo, if (r?.dias == 1L) "DIA" else "DIAS")
            setViewVisibility(R.id.wBlocoRelogio, if (r == null) View.GONE else View.VISIBLE)
            relogio(R.id.wRelogio, r, corArgb(c))
            barra(R.id.wBarra, c, hoje)
            setTextViewText(R.id.wPct, progresso(c, hoje)?.takeIf { diasAte(c.data, hoje) > 0 }?.let { "${(it * 100).toInt()}%" } ?: "")
        }

        /** Próximos eventos se revezando sozinhos (ViewFlipper com animação de entrada e saída). */
        private fun lista(ctx: Context, estado: Estado, hoje: LocalDate) = RemoteViews(ctx.packageName, R.layout.widget_lista).apply {
            setOnClickPendingIntent(R.id.wRaiz, abrirApp(ctx))
            val futuros = ordenar(estado.contadores, hoje).filter { diasAte(it.data, hoje) >= 0 }.take(5)
            setViewVisibility(R.id.wVazio, if (futuros.isEmpty()) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.wGiro, if (futuros.isEmpty()) View.GONE else View.VISIBLE)
            removeAllViews(R.id.wGiro)
            futuros.forEachIndexed { i, c ->
                val d = diasAte(c.data, hoje)
                addView(R.id.wGiro, RemoteViews(ctx.packageName, R.layout.widget_giro_item).apply {
                    setTextViewText(R.id.gNome, c.nome)
                    setTextViewText(R.id.gDias, if (d == 0L) "Hoje" else numero(d))
                    setTextColor(R.id.gDias, corArgb(c))
                    setTextViewText(R.id.gRotulo, when { d == 0L -> ""; d == 1L -> "dia"; else -> "dias" })
                    setTextViewText(R.id.gPos, "${i + 1} de ${futuros.size}")
                })
            }
        }

        private fun faixa(ctx: Context, c: Contador?, agora: LocalDateTime) = RemoteViews(ctx.packageName, R.layout.widget_faixa).apply {
            setOnClickPendingIntent(R.id.wRaiz, abrirApp(ctx))
            if (c == null) { setTextViewText(R.id.wTexto, "Crie um contador"); setTextViewText(R.id.wExtra, ""); setViewVisibility(R.id.wRelogio, View.GONE); return@apply }
            val r = relogioWidget(c, agora)
            setTextViewText(R.id.wTexto, c.nome)
            setTextViewText(R.id.wExtra, if (r == null) "Hoje!" else "${numero(r.dias)}d")
            setInt(R.id.wPonto, "setColorFilter", corArgb(c))
            relogio(R.id.wRelogio, r, corArgb(c))
        }
    }
}

class ListaWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, gerente: AppWidgetManager, ids: IntArray) = ContadorWidget.atualizarTodos(ctx, Armazem(ctx).ler())
}

class FaixaWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, gerente: AppWidgetManager, ids: IntArray) = ContadorWidget.atualizarTodos(ctx, Armazem(ctx).ler())
}
