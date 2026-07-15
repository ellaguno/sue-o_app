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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
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

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(elapsedText = "00:00", dbText = "—"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "somnia:monitor")
            .apply { acquire(MAX_SESSION_MS) }

        val startMs = System.currentTimeMillis()
        sessionStartMs = startMs
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
            audioEngine = AudioEngine { samples, length ->
                pipeline?.feed(samples, length)
            }.also { it.start(this@MonitorService) }
        }
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
                    clipPath = clipPath,
                )
            )
            stateHolder.update { it.copy(eventsDetected = it.eventsDetected + 1) }
        }
    }

    /** Llamado desde el hilo de audio, una vez por segundo. */
    private fun onSecondReading(dbfs: Double) {
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

    private fun stopMonitoring() {
        audioEngine?.stop()
        audioEngine = null
        pipeline = null
        val endedSessionId = sessionId
        lifecycleScope.launch {
            if (endedSessionId > 0) {
                aggregator.flush()?.let { repository.saveMinute(endedSessionId, it) }
                repository.endSession(endedSessionId, System.currentTimeMillis(), batteryPct())
            }
            stateHolder.update { MonitorState() }
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            ServiceCompat.stopForeground(this@MonitorService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
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
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_UPDATE_MS = 30_000L
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
