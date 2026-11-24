package com.sesolibre.somnia.data.db

import androidx.room.ColumnInfo
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
    /**
     * Si el experimento "sonido suave al roncar" estaba activado esa noche
     * (aunque no haya llegado a sonar): permite comparar noches con y sin él.
     */
    @ColumnInfo(defaultValue = "0")
    val snoreSoundEnabled: Boolean = false,
)

/**
 * Una vez que sonó el sonido suave de SesoLibre Sonidos durante la noche.
 * Mientras suena no se detectan eventos (el micrófono lo oiría).
 */
@Entity(
    tableName = "sound_plays",
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
data class SoundPlay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startEpochMs: Long,
    /** null mientras suena (o si el proceso murió antes de cerrarla). */
    val endEpochMs: Long? = null,
    /** Receta de SesoLibre Sonidos, p. ej. "sonar.calma-ronquido". */
    val recipeId: String,
    /** "finished" (terminó la receta), "stopped" (se cortó) o "failed". */
    val endReason: String? = null,
) {
    /** Tramo en que sonó (si no se cerró, lo que dura la receta: 10 min). */
    val range: LongRange
        get() = startEpochMs..(endEpochMs ?: (startEpochMs + 10 * 60_000L))
}

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

/**
 * Un evento de sonido detectado (ronquido, tos, ruido…). El audio del clip
 * vive como archivo en almacenamiento interno ([clipPath]); la fila conserva
 * los metadatos aunque el clip se borre por retención.
 */
@Entity(
    tableName = "sound_events",
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
data class SoundEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val durationMs: Long,
    val dbPeak: Double,
    val dbAvg: Double,
    /** Categoría del clasificador (Etapa 3); mientras tanto [CATEGORY_UNKNOWN]. */
    val category: String = CATEGORY_UNKNOWN,
    val confidence: Double? = null,
    /**
     * Etiqueta cruda de AudioSet que devolvió YAMNet (p. ej. "Throat clearing"),
     * antes de mapearla a [category]. Se conserva para poder afinar el
     * [com.sesolibre.somnia.ml.CategoryMapper] con datos reales sin depender de logcat.
     */
    val rawLabel: String? = null,
    val clipPath: String? = null,
    /**
     * Transcripción del habla (categoría `speech`), obtenida on-device con
     * SpeechRecognizer a petición del usuario. null = no transcrito.
     */
    val transcript: String? = null,
    /** Reetiquetado manual del usuario; tiene prioridad sobre [category]. */
    val manualLabel: String? = null,
    /**
     * Atribución manual: id de [SleepCompanion] si el sonido vino de otra
     * persona; null = sin atribuir (se asume del usuario).
     */
    val attributedToCompanionId: Long? = null,
) {
    companion object {
        const val CATEGORY_UNKNOWN = "unknown"
    }
}
