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

    /**
     * Tras un hueco de captura (el micrófono se cayó y se recuperó), continúa en
     * el minuto que corresponde al tiempo REAL transcurrido; si no, la serie
     * "encogería" la noche y los minutos dejarían de coincidir con el reloj.
     * Devuelve el minuto parcial que quedaba pendiente, si lo había.
     */
    fun resumeAt(minuteIndex: Int): MinuteStats? {
        val pending = flush()
        if (minuteIndex > this.minuteIndex) this.minuteIndex = minuteIndex
        return pending
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
