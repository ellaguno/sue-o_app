package com.sesolibre.somnia.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sesolibre.somnia.audio.DbMeter

/** Una noche (o siesta) de monitoreo. */
@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochMs: Long,
    val endEpochMs: Long? = null,
    /** Offset dBFS -> dB SPL aproximado usado en esta sesión. */
    val calibrationDbOffset: Double = DbMeter.DEFAULT_CALIBRATION_OFFSET_DB,
    val batteryStartPct: Int? = null,
    val batteryEndPct: Int? = null,
    val notes: String? = null,
)

/** Serie de ruido: estadísticas de dBFS por minuto (sin audio). */
@Entity(
    tableName = "noise_samples",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class NoiseSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val minuteIndex: Int,
    val dbMin: Double,
    val dbAvg: Double,
    val dbMax: Double,
)
