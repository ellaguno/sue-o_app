package com.sesolibre.somnia.schedule

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sesolibre.somnia.MainActivity
import com.sesolibre.somnia.R
import com.sesolibre.somnia.service.MonitorService

/**
 * Recordatorio a la hora de dormir.
 *
 * Android no deja que una app en segundo plano encienda el micrófono por su
 * cuenta (desde Android 11; en 14+ lanza SecurityException), aunque la
 * despierte una alarma exacta: el servicio arranca, pero sin acceso al
 * micrófono. La excepción es que el usuario toque algo de la app, como una
 * notificación. Por eso, a la hora de dormir Somnia no intenta grabar sola:
 * avisa, y un toque en «Iniciar» arranca el monitoreo (sin abrir la app,
 * también desde la pantalla de bloqueo).
 */
object BedtimeReminder {

    const val CHANNEL_ID = "bedtime"
    private const val NOTIFICATION_ID = 3
    /** Si nadie lo toca, el aviso se quita solo después de esto. */
    private const val TIMEOUT_MS = 6 * 60 * 60_000L

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_bedtime_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_bedtime_desc)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** ¿Puede mostrarse? (permiso de notificaciones y canal no silenciado). */
    fun canNotify(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Publica el aviso. [failedToStart] = el arranque automático lo bloqueó
     * Android (texto distinto, para que se entienda por qué no está grabando).
     */
    @SuppressLint("MissingPermission") // se comprueba con canNotify()
    fun post(context: Context, failedToStart: Boolean = false) {
        if (!canNotify(context)) return
        // Botón «Iniciar»: arranca el servicio directamente. Que el usuario
        // toque la notificación es lo que habilita el micrófono.
        val start = PendingIntent.getForegroundService(
            context, REQ_START,
            Intent(context, MonitorService::class.java)
                .setAction(MonitorService.ACTION_START)
                .putExtra(MonitorService.EXTRA_FROM_REMINDER, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Tocar el cuerpo abre Somnia y también inicia (por si hay que conceder permisos).
        val open = PendingIntent.getActivity(
            context, REQ_OPEN,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_START_FROM_REMINDER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = context.getString(
            if (failedToStart) R.string.bedtime_text_blocked else R.string.bedtime_text,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle(context.getString(R.string.bedtime_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.bedtime_action_start), start)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    /** Se quita al iniciar el monitoreo o a la hora de despertar. */
    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private const val REQ_START = 300
    private const val REQ_OPEN = 301
}
