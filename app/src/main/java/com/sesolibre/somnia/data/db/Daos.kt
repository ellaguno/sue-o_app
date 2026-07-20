package com.sesolibre.somnia.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Sesión con estadísticas agregadas de su serie de ruido. */
data class SessionWithStats(
    @Embedded val session: Session,
    val sampleCount: Int,
    val dbMin: Double?,
    val dbAvg: Double?,
    val dbMax: Double?,
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): Session?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<Session?>

    @Query(
        """
        SELECT s.*,
               COUNT(n.id) AS sampleCount,
               MIN(n.dbMin) AS dbMin,
               AVG(n.dbAvg) AS dbAvg,
               MAX(n.dbMax) AS dbMax
        FROM sessions s
        LEFT JOIN noise_samples n ON n.sessionId = s.id
        GROUP BY s.id
        ORDER BY s.startEpochMs DESC
        """
    )
    fun sessionsWithStats(): Flow<List<SessionWithStats>>

    @Query("SELECT * FROM sessions ORDER BY startEpochMs DESC")
    fun observeAll(): Flow<List<Session>>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface NoiseSampleDao {
    @Insert
    suspend fun insert(sample: NoiseSample)

    @Query("SELECT * FROM noise_samples WHERE sessionId = :sessionId ORDER BY minuteIndex")
    fun bySession(sessionId: Long): Flow<List<NoiseSample>>

    @Query("SELECT * FROM noise_samples")
    fun observeAll(): Flow<List<NoiseSample>>
}

@Dao
interface SoundEventDao {
    @Insert
    suspend fun insert(event: SoundEvent): Long

    @Update
    suspend fun update(event: SoundEvent)

    @Query("SELECT * FROM sound_events WHERE sessionId = :sessionId ORDER BY startEpochMs")
    fun bySession(sessionId: Long): Flow<List<SoundEvent>>

    @Query("SELECT * FROM sound_events")
    fun observeAll(): Flow<List<SoundEvent>>

    @Query("SELECT COUNT(*) FROM sound_events WHERE sessionId = :sessionId AND clipPath IS NOT NULL")
    suspend fun clipCountForSession(sessionId: Long): Int

    @Query("UPDATE sound_events SET attributedToCompanionId = :companionId WHERE id = :eventId")
    suspend fun setAttribution(eventId: Long, companionId: Long?)

    @Query("UPDATE sound_events SET transcript = :transcript WHERE id = :eventId")
    suspend fun setTranscript(eventId: Long, transcript: String?)

    /** Eventos con clip cuya sesión empezó antes del corte (para retención). */
    @Query(
        """
        SELECT e.* FROM sound_events e
        JOIN sessions s ON s.id = e.sessionId
        WHERE e.clipPath IS NOT NULL AND s.startEpochMs < :cutoffEpochMs
        """
    )
    suspend fun eventsWithClipsOlderThan(cutoffEpochMs: Long): List<SoundEvent>

    @Query("UPDATE sound_events SET clipPath = NULL WHERE id IN (:ids)")
    suspend fun clearClipPaths(ids: List<Long>)

    /** Rutas de clips de una sesión (para borrarlos junto con la sesión). */
    @Query("SELECT clipPath FROM sound_events WHERE sessionId = :sessionId AND clipPath IS NOT NULL")
    suspend fun clipPathsForSession(sessionId: Long): List<String>
}
