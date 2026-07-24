package com.sesolibre.somnia.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sesolibre.somnia.MainActivity
import com.sesolibre.somnia.data.prefs.SettingsRepository
import com.sesolibre.somnia.data.prefs.SleepSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Programa el inicio y fin automáticos del monitoreo por hora.
 *
 * - **Fin (parada):** alarma inexacta [AlarmManager.setAndAllowWhileIdle]. Sobrevive
 *   a Doze, no pide permisos y no muestra ícono de alarma; puede dispararse con
 *   unos minutos de holgura (irrelevante para cerrar la sesión y mejor para la
 *   batería). Es la función principal.
 * - **Inicio (arranque):** [AlarmManager.setAlarmClock] porque arrancar un servicio
 *   en primer plano de micrófono desde segundo plano requiere una exención que solo
 *   dan las alarmas exactas de tipo despertador. Es opcional (opt-in) y exige el
 *   permiso `SCHEDULE_EXACT_ALARM`, que el usuario concede en Ajustes del sistema:
 *   sin él, [AlarmManager.setAlarmClock] lanza [SecurityException] (ver
 *   [canScheduleExactAlarms]).
 *
 * El [ScheduleReceiver] re-arma la siguiente ocurrencia cada vez que una alarma se
 * dispara, y también tras reiniciar el dispositivo.
 */
@Singleton
class SleepScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    /**
     * ¿El sistema nos deja programar alarmas exactas? Desde Android 12 hay que
     * pedirlo en Ajustes; sin ese permiso [AlarmManager.setAlarmClock] lanza
     * [SecurityException] y el inicio automático nunca se arma.
     */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /** Cancela y re-arma las alarmas según la configuración actual. Idempotente. */
    suspend fun sync() {
        val schedule = settings.sleepSchedule.first()
        cancel(ScheduleReceiver.ACTION_START)
        cancel(ScheduleReceiver.ACTION_STOP)
        if (!schedule.enabled) return

        // Cada alarma se arma por separado: si una falla, la otra debe sobrevivir.
        // El fin automático se arma siempre que el horario está activo.
        runCatching { scheduleInexact(ScheduleReceiver.ACTION_STOP, nextStopAt(schedule)) }
            .onFailure { Log.w(TAG, "no se pudo armar el fin automático", it) }
        if (!schedule.autoStart) return
        if (!canScheduleExactAlarms()) {
            Log.w(TAG, "inicio automático no armado: falta el permiso de alarmas exactas")
            return
        }
        runCatching { scheduleAlarmClock(ScheduleReceiver.ACTION_START, nextStartAt(schedule)) }
            .onFailure { Log.w(TAG, "no se pudo armar el inicio automático", it) }
    }

    private fun scheduleInexact(action: String, triggerAtMs: Long) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMs, pending(action),
        )
    }

    private fun scheduleAlarmClock(action: String, triggerAtMs: Long) {
        val show = PendingIntent.getActivity(
            context, REQ_SHOW,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMs, show), pending(action),
        )
    }

    private fun cancel(action: String) = alarmManager.cancel(pending(action))

    private fun pending(action: String): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).setAction(action)
        val requestCode = if (action == ScheduleReceiver.ACTION_START) REQ_START else REQ_STOP
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Próximo inicio: la hora de dormir del primer día (hoy en adelante) cuyo
     * disparo aún no pasa. Cada día usa su horario (base o alterno).
     */
    private fun nextStartAt(schedule: SleepSchedule): Long {
        val now = Calendar.getInstance()
        return (0..7).firstNotNullOf { offset ->
            val day = dayAt(now, offset)
            atMinutes(day, schedule.bedtimeFor(isoDayOfWeek(day)))
                .takeIf { it > now.timeInMillis }
        }
    }

    /**
     * Próximo fin: la hora de despertar de cada noche, atribuida al día en que
     * EMPIEZA esa noche; si el despertar es antes (o igual) que la hora de
     * dormir, cae al día siguiente. Se incluye ayer porque su noche puede
     * terminar hoy.
     */
    private fun nextStopAt(schedule: SleepSchedule): Long {
        val now = Calendar.getInstance()
        return (-1..7).mapNotNull { offset ->
            val day = dayAt(now, offset)
            val iso = isoDayOfWeek(day)
            val wakeDay =
                if (schedule.wakeFor(iso) <= schedule.bedtimeFor(iso)) dayAt(day, 1) else day
            atMinutes(wakeDay, schedule.wakeFor(iso)).takeIf { it > now.timeInMillis }
        }.min()
    }

    private fun dayAt(base: Calendar, offsetDays: Int): Calendar =
        (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offsetDays) }

    private fun atMinutes(day: Calendar, minutesOfDay: Int): Long =
        (day.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** [Calendar.DAY_OF_WEEK] (domingo=1) → ISO (lunes=1…domingo=7). */
    private fun isoDayOfWeek(day: Calendar): Int {
        val d = day.get(Calendar.DAY_OF_WEEK)
        return if (d == Calendar.SUNDAY) 7 else d - 1
    }

    private companion object {
        const val TAG = "SleepScheduler"
        const val REQ_START = 100
        const val REQ_STOP = 101
        const val REQ_SHOW = 102
    }
}
