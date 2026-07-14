package com.sesolibre.somnia.audio

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Cálculo de nivel de sonido a partir de PCM 16-bit.
 *
 * El resultado es dBFS (decibeles relativos a escala completa, siempre <= 0).
 * Los micrófonos de teléfono no están calibrados: para mostrar un valor
 * aproximado de dB SPL se suma un offset de calibración (por defecto
 * [DEFAULT_CALIBRATION_OFFSET_DB], ajustable por sesión).
 */
object DbMeter {

    /** Piso de medición: silencio digital absoluto. */
    const val FLOOR_DBFS = -96.0

    /** Offset por defecto dBFS -> dB SPL aproximado. */
    const val DEFAULT_CALIBRATION_OFFSET_DB = 90.0

    /** dBFS del RMS de [length] muestras PCM 16-bit. */
    fun dbfs(samples: ShortArray, length: Int = samples.size): Double {
        if (length <= 0) return FLOOR_DBFS
        var sum = 0.0
        for (i in 0 until length) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        return dbfsFromSumOfSquares(sum, length)
    }

    /** dBFS a partir de una suma de cuadrados acumulada (para ventanas por chunks). */
    fun dbfsFromSumOfSquares(sumOfSquares: Double, count: Int): Double {
        if (count <= 0) return FLOOR_DBFS
        val rms = sqrt(sumOfSquares / count)
        if (rms < 1e-9) return FLOOR_DBFS
        return (20.0 * log10(rms / 32768.0)).coerceIn(FLOOR_DBFS, 0.0)
    }
}
