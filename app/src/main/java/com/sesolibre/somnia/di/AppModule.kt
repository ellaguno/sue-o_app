package com.sesolibre.somnia.di

import android.content.Context
import androidx.room.Room
import com.sesolibre.somnia.data.db.CompanionDao
import com.sesolibre.somnia.data.db.NightLogDao
import com.sesolibre.somnia.data.db.NoiseSampleDao
import com.sesolibre.somnia.data.db.QuestionnaireDao
import com.sesolibre.somnia.data.db.SessionDao
import com.sesolibre.somnia.data.db.SomniaDatabase
import com.sesolibre.somnia.data.db.SoundEventDao
import com.sesolibre.somnia.data.db.UserProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SomniaDatabase =
        Room.databaseBuilder(context, SomniaDatabase::class.java, "somnia.db")
            .addMigrations(
                SomniaDatabase.MIGRATION_1_2,
                SomniaDatabase.MIGRATION_2_3,
                SomniaDatabase.MIGRATION_3_4,
                SomniaDatabase.MIGRATION_4_5,
            )
            .build()

    @Provides
    fun provideSessionDao(db: SomniaDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideNoiseSampleDao(db: SomniaDatabase): NoiseSampleDao = db.noiseSampleDao()

    @Provides
    fun provideSoundEventDao(db: SomniaDatabase): SoundEventDao = db.soundEventDao()

    @Provides
    fun provideUserProfileDao(db: SomniaDatabase): UserProfileDao = db.userProfileDao()

    @Provides
    fun provideCompanionDao(db: SomniaDatabase): CompanionDao = db.companionDao()

    @Provides
    fun provideQuestionnaireDao(db: SomniaDatabase): QuestionnaireDao = db.questionnaireDao()

    @Provides
    fun provideNightLogDao(db: SomniaDatabase): NightLogDao = db.nightLogDao()
}
