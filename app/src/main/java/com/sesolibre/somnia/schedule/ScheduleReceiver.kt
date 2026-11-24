package com.sesolibre.somnia.schedule

import android.app.ActivityManager
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sesolibre.somnia.data.MonitorStateHolder
import com.sesolibre.somnia.data.SessionRepository
import com.sesolibre.somnia.service.MonitorService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recibe las alarmas del horario de sueño (inicio/fin) y los eventos del sistema
 * que obligan a re-armarlas (reinicio, cambio de hora). Tras actuar, siempre
 * vuelve a programar la siguiente ocurrencia.
 */
@AndroidEntryPoint
class ScheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: SleepScheduler
    @Inject lateinit var stateHolder: MonitorStateHolder
    @Inject lateinit var repository: SessionRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                when (action) {
                    ACTION_START -> maybeStart(context)
                    ACTION_STOP -> maybeStop(context)
                    // BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED /
                    // SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED: solo re-armar.
                }
                scheduler.sync()
            } catch (t: Throwable) {
                Log.w(TAG, "fallo procesando $action", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Hora de dormir. Android no deja encender el micrófono desde segundo plano
     * (ni con alarma exacta), así que lo normal es AVISAR con una notificación:
     * un toque en «Iniciar» arranca el monitoreo. Solo si Somnia está en pantalla
     * en ese momento se puede iniciar directamente.
     */
    private fun maybeStart(context: Context) {
        if (stateHolder.state.value.running) return
        val micGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (micGranted && isAppInForeground()) {
            runCatching { MonitorService.start(context) }
                .onFailure { Log.w(TAG, "no se pudo iniciar el monitoreo", it) }
            return
        }
        BedtimeReminder.post(context)
    }

    /** Somnia visible en pantalla: el único caso en que Android permite grabar sin un toque. */
    private fun isAppInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    /**
     * Cierra la noche. Si hay sesión en curso, detiene el servicio; además cierra
     * cualquier sesión que haya quedado abierta porque el proceso murió antes de
     * la hora de despertar (si no, esa noche se quedaría sin hora de fin para
     * siempre, ya que el estado en memoria se pierde con el proceso).
     */
    private suspend fun maybeStop(context: Context) {
        // Si el recordatorio no se tocó, ya no tiene sentido a la hora de despertar.
        BedtimeReminder.cancel(context)
        val running = stateHolder.state.value
        if (running.running) {
            runCatching { MonitorService.stop(context) }
                .onFailure { Log.w(TAG, "no se pudo detener el monitoreo", it) }
        }
        val closed = runCatching {
            repository.closeDanglingSessions(
                nowMs = System.currentTimeMillis(),
                exceptSessionId = running.sessionId,
            )
        }.getOrDefault(0)
        if (closed > 0) Log.i(TAG, "sesiones huérfanas cerradas: $closed")
    }

    companion object {
        const val ACTION_START = "com.sesolibre.somnia.schedule.START"
        const val ACTION_STOP = "com.sesolibre.somnia.schedule.STOP"
        private const val TAG = "ScheduleReceiver"
    }
}
