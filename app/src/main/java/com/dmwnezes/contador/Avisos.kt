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
        val abrir = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val hoje = LocalDate.now()
        estado.contadores.forEach { c ->
            val texto = aviso(c, hoje) ?: return@forEach
            val n = NotificationCompat.Builder(ctx, CANAL)
                .setSmallIcon(R.drawable.ic_notificacao)
                .setColor(corArgb(c))
                .setContentTitle(texto)
                .setContentText(dataTexto(c))
                .setContentIntent(abrir)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(ctx).notify(c.id.hashCode(), n)
        }
    }
}

class AvisoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // Depois de reiniciar o celular o alarme some; reagenda.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Avisos.agendar(ctx) else Avisos.verificar(ctx)
    }
}
