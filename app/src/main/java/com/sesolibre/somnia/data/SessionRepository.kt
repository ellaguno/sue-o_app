package com.sesolibre.somnia.data

import com.sesolibre.somnia.audio.MinuteStats
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.NoiseSampleDao
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SessionDao
import com.sesolibre.somnia.data.db.SessionWithStats
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val noiseSampleDao: NoiseSampleDao,
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

    fun sessionsWithStats(): Flow<List<SessionWithStats>> = sessionDao.sessionsWithStats()

    fun samples(sessionId: Long): Flow<List<NoiseSample>> = noiseSampleDao.bySession(sessionId)

    suspend fun deleteSession(sessionId: Long) = sessionDao.delete(sessionId)
}
