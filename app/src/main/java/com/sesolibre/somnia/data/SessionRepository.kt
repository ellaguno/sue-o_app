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
import kotlinx.coroutines.flow.combine
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

    suspend fun startSession(nowMs: Long, batteryPct: Int?): Long =
        sessionDao.insert(Session(startEpochMs = nowMs, batteryStartPct = batteryPct))

    suspend fun endSession(sessionId: Long, nowMs: Long, batteryPct: Int?) {
        val session = sessionDao.byId(sessionId) ?: return
        sessionDao.update(session.copy(endEpochMs = nowMs, batteryEndPct = batteryPct))
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
        noiseSampleDao.observeAll(),
        nightLogDao.observeAll(),
    ) { sessions, events, samples, logs ->
        TrendsAnalyzer.metrics(sessions, events, samples, logs)
    }

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
