package com.sesolibre.somnia.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = ${UserProfile.SINGLETON_ID}")
    fun observe(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = ${UserProfile.SINGLETON_ID}")
    suspend fun get(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)
}

@Dao
interface CompanionDao {
    @Query("SELECT * FROM companions WHERE active = 1 ORDER BY name")
    fun observeActive(): Flow<List<SleepCompanion>>

    @Query("SELECT * FROM companions ORDER BY name")
    suspend fun all(): List<SleepCompanion>

    @Insert
    suspend fun insert(companion: SleepCompanion): Long

    @Update
    suspend fun update(companion: SleepCompanion)

    @Query("UPDATE companions SET active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}

@Dao
interface QuestionnaireDao {
    @Insert
    suspend fun insert(result: QuestionnaireResult): Long

    @Query(
        "SELECT * FROM questionnaire_results WHERE type = :type " +
            "ORDER BY answeredEpochMs DESC LIMIT 1"
    )
    fun observeLatest(type: String): Flow<QuestionnaireResult?>

    @Query("SELECT * FROM questionnaire_results WHERE type = :type ORDER BY answeredEpochMs DESC")
    fun observeHistory(type: String): Flow<List<QuestionnaireResult>>
}

@Dao
interface NightLogDao {
    @Query("SELECT * FROM night_logs WHERE sessionId = :sessionId")
    fun observe(sessionId: Long): Flow<NightLog?>

    @Query("SELECT * FROM night_logs")
    fun observeAll(): Flow<List<NightLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: NightLog)
}
