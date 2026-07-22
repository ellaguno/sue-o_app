package com.sesolibre.somnia.schedule

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sesolibre.somnia.data.MonitorStateHolder
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

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                when (action) {
                    ACTION_START -> maybeStart(context)
                    ACTION_STOP -> maybeStop(context)
                    // BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED: solo re-armar.
                }
                scheduler.sync()
            } catch (t: Throwable) {
                Log.w(TAG, "fallo procesando $action", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Arranca el monitoreo solo si ya se concedió el micrófono (no se puede pedir en 2.º plano). */
    private fun maybeStart(context: Context) {
        val micGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            Log.w(TAG, "inicio automático omitido: falta permiso de micrófono")
            return
        }
        runCatching { MonitorService.start(context) }
            .onFailure { Log.w(TAG, "no se pudo iniciar el monitoreo", it) }
    }

    /** Detiene solo si hay una sesión en curso (evita arrancar el servicio para nada). */
    private fun maybeStop(context: Context) {
        if (!stateHolder.state.value.running) return
        runCatching { MonitorService.stop(context) }
            .onFailure { Log.w(TAG, "no se pudo detener el monitoreo", it) }
    }

    companion object {
        const val ACTION_START = "com.sesolibre.somnia.schedule.START"
        const val ACTION_STOP = "com.sesolibre.somnia.schedule.STOP"
        private const val TAG = "ScheduleReceiver"
    }
}
