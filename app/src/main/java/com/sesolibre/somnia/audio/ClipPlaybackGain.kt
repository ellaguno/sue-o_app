package com.sesolibre.somnia.audio

import kotlin.math.roundToInt

/**
 * Ganancia de reproducción para los clips. Los eventos nocturnos se graban muy
 * bajos (picos típicos ~-45 dBFS), casi inaudibles a volumen normal. Al
 * escuchar en la app subimos cada clip a un nivel parejo en función de su
 * [SoundEvent.dbPeak], igual que el `loudnorm` que se usa al revisar en PC.
 *
 * La ganancia se aplica en reproducción con `LoudnessEnhancer` (no altera el
 * archivo). Lógica pura, testeable en JVM.
 */
object ClipPlaybackGain {

    /** Pico objetivo tras amplificar (dBFS). Deja headroom para no saturar. */
    const val TARGET_PEAK_DBFS = -9.0

    /** Tope de amplificación para no volver ruido el piso de silencio. */
    const val MAX_GAIN_DB = 45.0

    /** Ganancia en decibelios para llevar [dbPeak] hacia [TARGET_PEAK_DBFS]. */
    fun gainDb(dbPeak: Double): Double =
        (TARGET_PEAK_DBFS - dbPeak).coerceIn(0.0, MAX_GAIN_DB)

    /** La misma ganancia en milibelios (100 mB = 1 dB), como la espera LoudnessEnhancer. */
    fun gainMillibels(dbPeak: Double): Int = (gainDb(dbPeak) * 100).roundToInt()
}
