package com.sesolibre.somnia.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Session::class, NoiseSample::class, SoundEvent::class,
        UserProfile::class, SleepCompanion::class, QuestionnaireResult::class, NightLog::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class SomniaDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun noiseSampleDao(): NoiseSampleDao
    abstract fun soundEventDao(): SoundEventDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun companionDao(): CompanionDao
    abstract fun questionnaireDao(): QuestionnaireDao
    abstract fun nightLogDao(): NightLogDao

    companion object {
        /** v2: tabla de eventos de sonido (Etapa 2). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `sound_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `startEpochMs` INTEGER NOT NULL,
                        `endEpochMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `dbPeak` REAL NOT NULL,
                        `dbAvg` REAL NOT NULL,
                        `category` TEXT NOT NULL,
                        `confidence` REAL,
                        `clipPath` TEXT,
                        `manualLabel` TEXT,
                        FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sound_events_sessionId` " +
                        "ON `sound_events` (`sessionId`)"
                )
            }
        }

        /** v3: perfil, acompañantes, cuestionarios, bitácora y atribución (Etapa 4). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `sound_events` ADD COLUMN `attributedToCompanionId` INTEGER"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_profile` (
                        `id` INTEGER PRIMARY KEY NOT NULL,
                        `displayName` TEXT,
                        `birthYear` INTEGER,
                        `sex` TEXT,
                        `heightCm` INTEGER,
                        `weightKg` REAL,
                        `neckCm` INTEGER,
                        `sleepsAlone` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `companions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `consentAckEpochMs` INTEGER NOT NULL,
                        `active` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `questionnaire_results` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `type` TEXT NOT NULL,
                        `answeredEpochMs` INTEGER NOT NULL,
                        `score` INTEGER NOT NULL,
                        `riskLevel` TEXT NOT NULL,
                        `answersCsv` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `night_logs` (
                        `sessionId` INTEGER PRIMARY KEY NOT NULL,
                        `updatedEpochMs` INTEGER NOT NULL,
                        `tagsCsv` TEXT NOT NULL,
                        `note` TEXT,
                        FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        /** v4: label crudo de YAMNet por evento, para afinar el mapeo (Etapa 3). */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sound_events` ADD COLUMN `rawLabel` TEXT")
            }
        }
    }
}
