package com.sesolibre.somnia.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Session::class, NoiseSample::class],
    version = 1,
    exportSchema = false,
)
abstract class SomniaDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun noiseSampleDao(): NoiseSampleDao
}
