package com.sesolibre.somnia.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Session::class, NoiseSample::class, SoundEvent::class],
    version = 2,
    exportSchema = false,
)
abstract class SomniaDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun noiseSampleDao(): NoiseSampleDao
    abstract fun soundEventDao(): SoundEventDao

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
    }
}
