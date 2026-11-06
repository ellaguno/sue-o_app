package com.sesolibre.somnia.data

import com.sesolibre.somnia.audio.MinuteStats
import com.sesolibre.somnia.data.db.NightLog
import com.sesolibre.somnia.data.db.NightLogDao
import com.sesolibre.somnia.data.db.NightTag
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.NoiseSampleDao
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SessionDao
import com.sesolibre.somnia.data.db.SessionWithStats
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.data.db.SoundEventDao
import com.sesolibre.somnia.stats.NightMetrics
import com.sesolibre.somnia.stats.TrendsAnalyzer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val noiseSampleDao: NoiseSampleDao,
    private val soundEventDao: SoundEventDao,
    private val nightLogDao: NightLogDao,
) {

    /**
     * Abrir, reabrir y cerrar sesiones huérfanas pasan por este candado, y la
     * sesión que está grabando queda en [activeSessionId]: así el cierre de
     * huérfanas que corre al arrancar el proceso nunca cierra la noche que el
     * servicio acaba de retomar.
     */
    private val sessionLock = Mutex()

    @Volatile
    private var activeSessionId: Long? = null

    suspend fun startSession(nowMs: Long, batteryPct: Int?): Long = sessionLock.withLock {
        sessionDao.insert(Session(startEpochMs = nowMs, batteryStartPct = batteryPct))
            .also { activeSessionId = it }
    }

    /**
     * Retoma la última noche si el sistema mató el proceso a media grabación y
     * reinició el servicio: es reciente (empezó hace menos de [maxAgeMs]) y
     * sigue abierta o se cortó hace menos de [maxGapMs]. Así la noche no se
     * parte en dos. Devuelve la sesión reabierta, o null para empezar una nueva.
     */
    suspend fun reopenRecentSession(nowMs: Long, maxAgeMs: Long, maxGapMs: Long): Session? =
        sessionLock.withLock {
            val last = sessionDao.latest() ?: return@withLock null
            if (nowMs - last.startEpochMs > maxAgeMs) return@withLock null
            val end = last.endEpochMs
            if (end != null && nowMs - end > maxGapMs) return@withLock null
            val reopened = last.copy(endEpochMs = null)
            if (end != null) sessionDao.update(reopened)
            activeSessionId = reopened.id
            reopened
        }

    suspend fun endSession(sessionId: Long, nowMs: Long, batteryPct: Int?) = sessionLock.withLock {
        if (activeSessionId == sessionId) activeSessionId = null
        val session = sessionDao.byId(sessionId) ?: return@withLock
        sessionDao.update(session.copy(endEpochMs = nowMs, batteryEndPct = batteryPct))
    }

    /**
     * Cierra las sesiones que quedaron sin hora de fin porque el proceso murió
     * a media noche (crash, batería, el sistema mató el servicio). El fin se
     * fija en el último minuto realmente grabado —no en "ahora"— para no
     * inventar sueño que nunca se registró.
     *
     * @param exceptSessionId sesión en curso, que no debe tocarse.
     * @param minAgeMs edad mínima para considerar abandonada una sesión; evita
     *   cerrar una que acaba de arrancar en otro hilo.
     * @return cuántas sesiones se cerraron.
     */
    suspend fun closeDanglingSessions(
        nowMs: Long,
        exceptSessionId: Long? = null,
        minAgeMs: Long = 60_000L,
    ): Int = sessionLock.withLock {
        val active = activeSessionId
        val abandoned = sessionDao.openSessions()
            .filter { it.id != exceptSessionId && it.id != active && nowMs - it.startEpochMs >= minAgeMs }
        abandoned.forEach { session ->
            val lastMinute = noiseSampleDao.lastMinuteIndex(session.id)
            val endMs = if (lastMinute != null) {
                (session.startEpochMs + (lastMinute + 1) * 60_000L).coerceAtMost(nowMs)
            } else {
                session.startEpochMs
            }
            sessionDao.update(session.copy(endEpochMs = endMs))
        }
        abandoned.size
    }

    suspend fun saveMinute(sessionId: Long, stats: MinuteStats) {
        noiseSampleDao.insert(
            NoiseSample(
                sessionId = sessionId,
                minuteIndex = stats.minuteIndex,
                dbMin = stats.dbMin,
                dbAvg = stats.dbAvg,
                dbMax = stats.dbMax,
            )
        )
    }

    suspend fun saveEvent(event: SoundEvent): Long = soundEventDao.insert(event)

    suspend fun updateEvent(event: SoundEvent) = soundEventDao.update(event)

    suspend fun clipCountForSession(sessionId: Long): Int =
        soundEventDao.clipCountForSession(sessionId)

    fun sessionsWithStats(): Flow<List<SessionWithStats>> = sessionDao.sessionsWithStats()

    /** Métricas por noche para Tendencias (combina sesiones, eventos, ruido y bitácora). */
    fun nightlyMetrics(): Flow<List<NightMetrics>> = combine(
        sessionDao.observeAll(),
        soundEventDao.observeAll(),
        noiseSampleDao.peakBySession(),
        nightLogDao.observeAll(),
    ) { sessions, events, peaks, logs ->
        TrendsAnalyzer.metricsWithPeaks(
            sessions, events, peaks.associate { it.sessionId to it.dbMax }, logs,
        )
    }
        // Mientras se graba llega un minuto nuevo cada 60 s: se calcula fuera
        // del hilo principal y, si se acumulan, solo cuenta el último.
        .conflate()
        .flowOn(Dispatchers.Default)

    fun observeSession(sessionId: Long): Flow<Session?> = sessionDao.observeById(sessionId)

    fun samples(sessionId: Long): Flow<List<NoiseSample>> = noiseSampleDao.bySession(sessionId)

    fun events(sessionId: Long): Flow<List<SoundEvent>> = soundEventDao.bySession(sessionId)

    /** Atribuye (o desatribuye, con null) un evento a un acompañante. */
    suspend fun attributeEvent(eventId: Long, companionId: Long?) =
        soundEventDao.setAttribution(eventId, companionId)

    /** Guarda (o borra, con null) la transcripción del habla de un evento. */
    suspend fun saveTranscript(eventId: Long, transcript: String?) =
        soundEventDao.setTranscript(eventId, transcript)

    fun nightLog(sessionId: Long): Flow<NightLog?> = nightLogDao.observe(sessionId)

    suspend fun saveNightLog(sessionId: Long, tags: Collection<NightTag>, note: String?) {
        nightLogDao.upsert(
            NightLog(
                sessionId = sessionId,
                updatedEpochMs = System.currentTimeMillis(),
                tagsCsv = NightLog.csvOf(tags),
                note = note?.trim()?.takeIf { it.isNotEmpty() },
            )
        )
    }

    /** Borra la sesión, sus filas (cascade) y los archivos de clips. */
    suspend fun deleteSession(sessionId: Long) {
        soundEventDao.clipPathsForSession(sessionId).forEach { File(it).delete() }
        sessionDao.delete(sessionId)
    }

    /**
     * Retención: borra los ARCHIVOS de clips de sesiones anteriores al corte.
     * Los metadatos del evento se conservan siempre.
     */
    suspend fun pruneOldClips(cutoffEpochMs: Long) {
        val old = soundEventDao.eventsWithClipsOlderThan(cutoffEpochMs)
        if (old.isEmpty()) return
        old.forEach { event -> event.clipPath?.let { File(it).delete() } }
        soundEventDao.clearClipPaths(old.map { it.id })
    }
}
