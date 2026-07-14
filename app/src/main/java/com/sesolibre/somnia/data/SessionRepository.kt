package com.sesolibre.somnia.data

import com.sesolibre.somnia.audio.MinuteStats
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.NoiseSampleDao
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SessionDao
import com.sesolibre.somnia.data.db.SessionWithStats
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.data.db.SoundEventDao
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val noiseSampleDao: NoiseSampleDao,
    private val soundEventDao: SoundEventDao,
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

    fun observeSession(sessionId: Long): Flow<Session?> = sessionDao.observeById(sessionId)

    fun samples(sessionId: Long): Flow<List<NoiseSample>> = noiseSampleDao.bySession(sessionId)

    fun events(sessionId: Long): Flow<List<SoundEvent>> = soundEventDao.bySession(sessionId)

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
