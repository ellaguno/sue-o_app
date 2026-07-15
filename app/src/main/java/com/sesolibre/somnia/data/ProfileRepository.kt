package com.sesolibre.somnia.data

import com.sesolibre.somnia.data.db.CompanionDao
import com.sesolibre.somnia.data.db.QuestionnaireDao
import com.sesolibre.somnia.data.db.QuestionnaireResult
import com.sesolibre.somnia.data.db.SleepCompanion
import com.sesolibre.somnia.data.db.UserProfile
import com.sesolibre.somnia.data.db.UserProfileDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: UserProfileDao,
    private val companionDao: CompanionDao,
    private val questionnaireDao: QuestionnaireDao,
) {

    fun observeProfile(): Flow<UserProfile?> = profileDao.observe()

    suspend fun saveProfile(profile: UserProfile) =
        profileDao.upsert(profile.copy(id = UserProfile.SINGLETON_ID))

    fun observeCompanions(): Flow<List<SleepCompanion>> = companionDao.observeActive()

    /** Alta de acompañante; [consentAckEpochMs] registra que el usuario confirmó avisarle. */
    suspend fun addCompanion(name: String, consentAckEpochMs: Long): Long =
        companionDao.insert(SleepCompanion(name = name.trim(), consentAckEpochMs = consentAckEpochMs))

    suspend fun removeCompanion(id: Long) = companionDao.deactivate(id)

    suspend fun saveQuestionnaireResult(result: QuestionnaireResult): Long =
        questionnaireDao.insert(result)

    fun observeLatestResult(type: String): Flow<QuestionnaireResult?> =
        questionnaireDao.observeLatest(type)
}
