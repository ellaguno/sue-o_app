package com.sesolibre.somnia.audio

/** Estadísticas de un minuto de lecturas de dB (una lectura por segundo). */
data class MinuteStats(
    val minuteIndex: Int,
    val dbMin: Double,
    val dbAvg: Double,
    val dbMax: Double,
    val readingCount: Int,
)

/**
 * Agrega lecturas de dB de 1 segundo en estadísticas por minuto.
 * Lógica pura, sin dependencias de Android, para poder probarla en JVM.
 */
class NoiseAggregator(private val readingsPerMinute: Int = 60) {

    private val readings = ArrayList<Double>(readingsPerMinute)
    private var minuteIndex = 0

    /** Agrega una lectura; devuelve las estadísticas del minuto cuando se completa. */
    fun add(db: Double): MinuteStats? {
        readings.add(db)
        return if (readings.size >= readingsPerMinute) flush() else null
    }

    /** Cierra el minuto en curso aunque esté incompleto (fin de sesión). */
    fun flush(): MinuteStats? {
        if (readings.isEmpty()) return null
        val stats = MinuteStats(
            minuteIndex = minuteIndex,
            dbMin = readings.min(),
            dbAvg = readings.average(),
            dbMax = readings.max(),
            readingCount = readings.size,
        )
        readings.clear()
        minuteIndex++
        return stats
    }
}
