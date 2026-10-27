package com.dmwnezes.contador

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Uma vez por dia, às 9h, avisa dos contadores que estão a 30, 7, 1 ou 0 dias. */
object Avisos {
    private const val CANAL = "marcos"

    fun agendar(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val agora = LocalDateTime.now()
        var prox = agora.toLocalDate().atTime(LocalTime.of(9, 0))
        if (!prox.isAfter(agora)) prox = prox.plusDays(1)
        val ms = prox.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexato: o Android pode atrasar alguns minutos para poupar bateria.
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, ms, AlarmManager.INTERVAL_DAY, pendente(ctx))
    }

    private fun pendente(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, AvisoReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun verificar(ctx: Context) {
        val estado = Armazem(ctx).ler()
        ContadorWidget.atualizarTodos(ctx, estado)
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANAL, "Marcos da contagem", NotificationManager.IMPORTANCE_DEFAULT))
        val hoje = LocalDate.now()
        estado.contadores.forEach { c ->
            val titulo = aviso(c, hoje) ?: return@forEach
            NotificationManagerCompat.from(ctx).notify(c.id.hashCode(), montar(ctx, c, titulo, hoje))
        }
    }

    private fun montar(ctx: Context, c: Contador, titulo: String, hoje: LocalDate): android.app.Notification {
        val req = c.id.hashCode()
        val abrir = PendingIntent.getActivity(ctx, req, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val comp = PendingIntent.getActivity(
            ctx, req + 1,
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_COMPARTILHAR, c.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val dias = diasAte(c.data, hoje)
        val texto = if (dias == 0L) {
            val esperou = ChronoUnit.DAYS.between(c.criado, hoje)
            if (esperou > 0) "Você esperou ${numero(esperou)} ${if (esperou == 1L) "dia" else "dias"} por isso." else dataTexto(c)
        } else {
            val p = progresso(c, hoje)
            dataTexto(c) + (p?.let { " · ${(it * 100).toInt()}% do caminho" } ?: "")
        }
        return NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(corArgb(c))
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .apply { progresso(c, hoje)?.takeIf { dias > 0 }?.let { setProgress(100, (it * 100).toInt(), false) } }
            .setContentIntent(abrir)
            .addAction(0, "Compartilhar", comp)
            .addAction(0, "Abrir", abrir)
            .setAutoCancel(true)
            .build()
    }
}

class AvisoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Depois de reiniciar o celular o alarme some; reagenda.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Avisos.agendar(ctx) else Avisos.verificar(ctx)
    }
}
