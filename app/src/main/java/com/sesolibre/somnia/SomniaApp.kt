package com.sesolibre.somnia

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.sesolibre.somnia.data.SessionRepository
import com.sesolibre.somnia.schedule.SleepScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SomniaApp : Application() {

    @Inject lateinit var repository: SessionRepository
    @Inject lateinit var sleepScheduler: SleepScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        pruneOldClips()
        closeDanglingSessions()
        // Re-arma el horario de sueño por si el proceso se recreó o cambió la config.
        appScope.launch { runCatching { sleepScheduler.sync() } }
    }

    /**
     * Si una noche anterior quedó sin cerrar (el proceso murió mientras grababa),
     * se cierra ahora con la hora del último minuto grabado. El proceso acaba de
     * arrancar, así que no hay sesión en curso que proteger.
     */
    private fun closeDanglingSessions() {
        appScope.launch {
            runCatching { repository.closeDanglingSessions(System.currentTimeMillis()) }
        }
    }

    /** Retención: borra el audio (no los metadatos) de clips viejos. */
    private fun pruneOldClips() {
        appScope.launch {
            val cutoff = System.currentTimeMillis() - CLIP_RETENTION_DAYS * 24L * 60 * 60 * 1000
            repository.pruneOldClips(cutoff)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            getString(R.string.channel_monitor_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_monitor_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "monitor"
        const val CLIP_RETENTION_DAYS = 30
    }
}
