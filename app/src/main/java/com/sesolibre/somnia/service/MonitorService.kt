package com.sesolibre.somnia.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sesolibre.somnia.MainActivity
import com.sesolibre.somnia.R
import com.sesolibre.somnia.SomniaApp
import com.sesolibre.somnia.audio.AudioEngine
import com.sesolibre.somnia.audio.AudioPipeline
import com.sesolibre.somnia.audio.ClipEncoder
import com.sesolibre.somnia.audio.DbMeter
import com.sesolibre.somnia.audio.EventDetector
import com.sesolibre.somnia.audio.NoiseAggregator
import com.sesolibre.somnia.data.MonitorState
import com.sesolibre.somnia.data.MonitorStateHolder
import com.sesolibre.somnia.data.SessionRepository
import com.sesolibre.somnia.data.prefs.SettingsRepository
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.YamnetClassifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Servicio en primer plano (tipo micrófono) que sostiene la captura de audio
 * durante toda la noche: una lectura de dB por segundo, agregada por minuto
 * a la base de datos. En Etapa 2 aquí mismo se conectará el detector de
 * eventos y el guardado de clips.
 */
@AndroidEntryPoint
class MonitorService : LifecycleService() {

    @Inject lateinit var repository: SessionRepository
    @Inject lateinit var stateHolder: MonitorStateHolder
    @Inject lateinit var settings: SettingsRepository

    private var audioEngine: AudioEngine? = null
    private var pipeline: AudioPipeline? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /** YAMNet se carga una sola vez por sesión; si falla, se sigue sin clasificar. */
    private val classifierDelegate = lazy {
        runCatching { YamnetClassifier(this) }
            .onFailure { android.util.Log.e("MonitorService", "No se pudo cargar YAMNet", it) }
            .getOrNull()
    }
    private val classifier: YamnetClassifier? get() = classifierDelegate.value
    private val aggregator = NoiseAggregator()
    private var sessionId: Long = -1
    private var sessionStartMs: Long = 0
    private var lastNotificationUpdateMs = 0L

    /** Reloj monótono de la última lectura de audio; lo vigila el watchdog. */
    @Volatile
    private var lastReadingElapsedMs = 0L
    private var audioRestarts = 0
    private var lastRestartElapsedMs = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopMonitoring()
            else -> startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (stateHolder.state.value.running) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        // Si el sistema no nos deja pasar a primer plano (p. ej. el arranque
        // automático llegó sin la exención de alarma exacta), no dejamos el
        // servicio a medias: se cae ordenadamente en vez de crashear.
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(elapsedText = "00:00", dbText = "—"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "no se pudo iniciar en primer plano", t)
            stopSelf()
            return
        }

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "somnia:monitor")
            .apply { acquire(MAX_SESSION_MS) }

        val startMs = System.currentTimeMillis()
        sessionStartMs = startMs
        lastReadingElapsedMs = SystemClock.elapsedRealtime()
        audioRestarts = 0
        lifecycleScope.launch {
            sessionId = repository.startSession(startMs, batteryPct())
            stateHolder.update {
                MonitorState(
                    running = true,
                    sessionId = sessionId,
                    startedAtMs = startMs,
                )
            }
            val openMarginDb = settings.openMarginDb.first()
            pipeline = AudioPipeline(
                detector = EventDetector(EventDetector.Config(openMarginDb = openMarginDb)),
                onSecondReading = ::onSecondReading,
                onEventCaptured = ::onEventCaptured,
            )
            startAudioEngine()
            startWatchdog()
        }
    }

    private fun startAudioEngine() {
        audioEngine = AudioEngine(
            onSamples = { samples, length -> pipeline?.feed(samples, length) },
            onUnrecoverableError = { onAudioLost() },
        ).also { it.start(this) }
    }

    /**
     * Vigila que sigan llegando lecturas. El [AudioEngine] ya se auto-repara,
     * pero si se queda colgado (un `read()` que nunca vuelve) nadie lo notaría:
     * la noche seguiría "grabando" sin guardar un solo minuto. Aquí se reinicia
     * la captura y, si ni así vuelve, se cierra la sesión con lo que sí se grabó.
     */
    private fun startWatchdog() {
        lifecycleScope.launch {
            while (isActive) {
                delay(WATCHDOG_PERIOD_MS)
                if (!stateHolder.state.value.running) return@launch
                val silentMs = SystemClock.elapsedRealtime() - lastReadingElapsedMs
                if (silentMs < WATCHDOG_STALE_MS) continue

                // Los reintentos se cuentan por racha: un tropiezo aislado a las
                // 2 y otro a las 5 no deben sumar para cerrar la noche.
                val now = SystemClock.elapsedRealtime()
                if (now - lastRestartElapsedMs > RESTART_WINDOW_MS) audioRestarts = 0
                if (audioRestarts >= MAX_AUDIO_RESTARTS) {
                    android.util.Log.e(TAG, "micrófono sin señal; se cierra la noche")
                    stopMonitoring(unexpected = true)
                    return@launch
                }
                audioRestarts++
                lastRestartElapsedMs = now
                android.util.Log.w(
                    TAG, "sin lecturas por ${silentMs / 1000} s; reiniciando captura ($audioRestarts)",
                )
                restartAudio()
            }
        }
    }

    /** Reabre el micrófono conservando el pipeline y la alineación con el reloj. */
    private suspend fun restartAudio() {
        val previous = audioEngine
        audioEngine = null
        // stop() espera al hilo de audio (que es justo el que está colgado):
        // fuera del hilo principal para no arriesgar un ANR.
        withContext(Dispatchers.IO) { runCatching { previous?.stop() } }

        // El hueco sin captura debe verse como hueco, no comprimir la noche.
        val elapsedMinutes = ((System.currentTimeMillis() - sessionStartMs) / 60_000L).toInt()
        aggregator.resumeAt(elapsedMinutes)?.let { pending ->
            if (sessionId > 0) repository.saveMinute(sessionId, pending)
        }
        lastReadingElapsedMs = SystemClock.elapsedRealtime()
        startAudioEngine()
    }

    /** El motor de audio se rindió: cerrar la noche con lo grabado y avisar. */
    private fun onAudioLost() {
        lifecycleScope.launch { stopMonitoring(unexpected = true) }
    }

    /** Llamado desde el hilo de audio cuando el detector cierra un evento válido. */
    private fun onEventCaptured(capture: AudioPipeline.EventCapture) {
        val currentSessionId = sessionId
        if (currentSessionId <= 0) return
        lifecycleScope.launch(Dispatchers.IO) {
            // Clasificación on-device (YAMNet) sobre el PCM del evento
            val classification = runCatching { classifier?.classify(capture.pcm) }.getOrNull()

            // Tope de clips por noche: los metadatos se guardan siempre,
            // pero el audio ya no, para no llenar el almacenamiento.
            val underCap = repository.clipCountForSession(currentSessionId) < MAX_CLIPS_PER_SESSION
            var clipPath: String? = null
            if (underCap) {
                val dir = File(filesDir, "clips/session_$currentSessionId")
                val file = File(dir, "evt_${capture.startOffsetMs}.${ClipEncoder.fileExtension}")
                if (ClipEncoder.encode(capture.pcm, AudioEngine.SAMPLE_RATE, file)) {
                    clipPath = file.absolutePath
                }
            }
            repository.saveEvent(
                SoundEvent(
                    sessionId = currentSessionId,
                    startEpochMs = sessionStartMs + capture.startOffsetMs,
                    endEpochMs = sessionStartMs + capture.endOffsetMs,
                    durationMs = capture.endOffsetMs - capture.startOffsetMs,
                    dbPeak = capture.peakDbfs,
                    dbAvg = capture.avgDbfs,
                    category = classification?.category?.key ?: SoundEvent.CATEGORY_UNKNOWN,
                    confidence = classification?.score?.toDouble(),
                    rawLabel = classification?.label,
                    clipPath = clipPath,
                )
            )
            stateHolder.update { it.copy(eventsDetected = it.eventsDetected + 1) }
        }
    }

    /** Llamado desde el hilo de audio, una vez por segundo. */
    private fun onSecondReading(dbfs: Double) {
        lastReadingElapsedMs = SystemClock.elapsedRealtime()
        stateHolder.update { it.copy(currentDbfs = dbfs) }
        val minute = aggregator.add(dbfs)
        if (minute != null && sessionId > 0) {
            lifecycleScope.launch {
                repository.saveMinute(sessionId, minute)
                stateHolder.update { it.copy(minutesSaved = minute.minuteIndex + 1) }
            }
        }
        maybeUpdateNotification(dbfs)
    }

    private fun maybeUpdateNotification(dbfs: Double) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotificationUpdateMs < NOTIFICATION_UPDATE_MS) return
        lastNotificationUpdateMs = now

        val startedAt = stateHolder.state.value.startedAtMs ?: return
        val elapsedMin = (System.currentTimeMillis() - startedAt) / 60_000
        val elapsedText = "%02d:%02d".format(elapsedMin / 60, elapsedMin % 60)
        val dbText = getString(
            R.string.db_approx_format,
            (dbfs + DbMeter.DEFAULT_CALIBRATION_OFFSET_DB).roundToInt(),
        )
        getSystemService(android.app.NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(elapsedText, dbText))
    }

    /**
     * @param unexpected true si la noche se corta sola (se perdió el micrófono);
     *   en ese caso se deja un aviso para que se note por la mañana.
     */
    private fun stopMonitoring(unexpected: Boolean = false) {
        audioEngine?.stop()
        audioEngine = null
        pipeline = null
        val endedSessionId = sessionId
        lifecycleScope.launch {
            val endMs = System.currentTimeMillis()
            if (endedSessionId > 0) {
                aggregator.flush()?.let { repository.saveMinute(endedSessionId, it) }
                repository.endSession(endedSessionId, endMs, batteryPct())
            }
            stateHolder.update { MonitorState() }
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            ServiceCompat.stopForeground(this@MonitorService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            if (unexpected) notifyUnexpectedStop(endMs)
            stopSelf()
        }
    }

    /** Aviso (no permanente) de que la noche se cortó sola y a qué hora. */
    private fun notifyUnexpectedStop(endMs: Long) {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(endMs))
        val notification = NotificationCompat.Builder(this, SomniaApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle(getString(R.string.notif_monitor_lost_title))
            .setContentText(getString(R.string.notif_monitor_lost_text, time))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(getString(R.string.notif_monitor_lost_text, time)),
            )
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 2,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        runCatching {
            getSystemService(android.app.NotificationManager::class.java)
                .notify(NOTIFICATION_LOST_ID, notification)
        }
    }

    private fun buildNotification(elapsedText: String, dbText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, SomniaApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle(getString(R.string.notif_monitoring_title))
            .setContentText(getString(R.string.notif_monitoring_text, elapsedText, dbText))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    private fun batteryPct(): Int? =
        (getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it in 0..100 }

    override fun onDestroy() {
        audioEngine?.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        if (classifierDelegate.isInitialized()) {
            runCatching { classifierDelegate.value?.close() }
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.sesolibre.somnia.action.START"
        const val ACTION_STOP = "com.sesolibre.somnia.action.STOP"
        private const val TAG = "MonitorService"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_LOST_ID = 2
        private const val NOTIFICATION_UPDATE_MS = 30_000L
        private const val WATCHDOG_PERIOD_MS = 60_000L
        /** Sin una sola lectura en 2 min, la captura está caída. */
        private const val WATCHDOG_STALE_MS = 120_000L
        private const val MAX_AUDIO_RESTARTS = 3
        /** Los reintentos solo se acumulan dentro de esta ventana. */
        private const val RESTART_WINDOW_MS = 30 * 60_000L
        private const val MAX_SESSION_MS = 14 * 60 * 60 * 1000L // tope de seguridad: 14 h
        // Un clip Opus de evento pesa ~33 KB (medido en la alfa del 2026-07-14),
        // así que 1000 clips son ~35 MB por noche como techo.
        private const val MAX_CLIPS_PER_SESSION = 1000

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, MonitorService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MonitorService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
