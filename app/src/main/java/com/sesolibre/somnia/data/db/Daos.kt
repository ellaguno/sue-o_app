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

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface NoiseSampleDao {
    @Insert
    suspend fun insert(sample: NoiseSample)

    @Query("SELECT * FROM noise_samples WHERE sessionId = :sessionId ORDER BY minuteIndex")
    fun bySession(sessionId: Long): Flow<List<NoiseSample>>
}
