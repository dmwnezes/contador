package com.dmwnezes.contador

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import kotlin.math.abs

/** Widget da tela inicial com o contador em destaque. Atualiza ao salvar, a cada 30 min e no aviso das 9h. */
class ContadorWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, gerente: AppWidgetManager, ids: IntArray) = atualizarTodos(ctx, Armazem(ctx).ler())

    companion object {
        fun atualizarTodos(ctx: Context, estado: Estado) {
            val gerente = AppWidgetManager.getInstance(ctx)
            val ids = gerente.getAppWidgetIds(ComponentName(ctx, ContadorWidget::class.java))
            if (ids.isEmpty()) return
            val c = estado.contadores.find { it.id == estado.destaque } ?: estado.contadores.firstOrNull()
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            if (c == null) {
                v.setTextViewText(R.id.wNome, "Contador")
                v.setTextViewText(R.id.wNumero, "+")
                v.setTextViewText(R.id.wRotulo, "Crie um contador")
            } else {
                val d = diasAte(c.data, LocalDate.now())
                val n = abs(d)
                v.setTextViewText(R.id.wNome, c.nome)
                v.setTextViewText(R.id.wNumero, if (d == 0L) "Hoje" else numero(n))
                v.setTextColor(R.id.wNumero, corArgb(c))
                v.setTextViewText(R.id.wRotulo, when {
                    d > 0 -> if (n == 1L) "dia falta" else "dias faltam"
                    d == 0L -> "é o dia!"
                    else -> if (n == 1L) "dia desde" else "dias desde"
                })
            }
            val abrir = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            v.setOnClickPendingIntent(R.id.wRaiz, abrir)
            gerente.updateAppWidget(ids, v)
        }
    }
}
