package com.sesolibre.somnia.stats

import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory
import kotlin.math.roundToInt

/**
 * Resumen agregado de una noche: conteos por categoría, episodios de ronquido,
 * pico de ruido y el patrón de pausas. Es la capa de dominio que alimenta la
 * Vista Noche v2 y (más adelante) Tendencias y Exportar.
 *
 * "Ruido ambiente" ([environmentCount]) se contabiliza aparte de los sonidos
 * del cuerpo para que ventilador/tráfico no tapen los ronquidos en el resumen
 * (ver Etapa 5, discriminar ruido base).
 */
data class NightSummary(
    val durationMinutes: Long?,
    val totalEvents: Int,
    /** Conteo por categoría efectiva (etiqueta manual > clasificador). */
    val countsByCategory: Map<SomniaCategory, Int>,
    /** Eventos sin categoría reconocida (category = "unknown"). */
    val unknownCount: Int,
    val snoringEpisodes: Int,
    val snoringMinutes: Double,
    val pausePatternCount: Int,
    /** dB SPL aprox del minuto más ruidoso de la noche; null si no hay muestras. */
    val peakDb: Int?,
) {
    fun count(category: SomniaCategory): Int = countsByCategory[category] ?: 0

    val snoringCount: Int get() = count(SomniaCategory.SNORING)
    val environmentCount: Int get() = count(SomniaCategory.ENVIRONMENT)
    val otherCount: Int get() = count(SomniaCategory.OTHER) + unknownCount

    /**
     * Sonidos "del cuerpo" (ronquido, respiración, tos, habla, movimiento):
     * lo relevante del resumen, sin el ruido ambiente ni lo no clasificado.
     */
    val bodyEventCount: Int
        get() = BODY_CATEGORIES.sumOf { count(it) }

    companion object {
        val BODY_CATEGORIES = listOf(
            SomniaCategory.SNORING,
            SomniaCategory.BREATHING,
            SomniaCategory.COUGH,
            SomniaCategory.SPEECH,
            SomniaCategory.MOVEMENT,
        )
    }
}

/** Una hora de la noche: minutos roncando y sonidos del cuerpo en esa hora. */
data class HourBucket(
    val hourStartEpochMs: Long,
    val snoringMinutes: Double,
    val bodyEvents: Int,
)

/** Cálculo puro del resumen de una noche; testeable en JVM sin emulador. */
object NightAnalyzer {

    private const val HOUR_MS = 60 * 60_000L

    /**
     * Reparte la noche en horas de reloj (de la hora en que empezó a la hora
     * en que terminó) y suma, por hora, los minutos roncando y los sonidos del
     * cuerpo. Un ronquido que cruza la hora se reparte entre ambas.
     *
     * @param zoneOffsetMs desfase de la zona horaria, para alinear a horas locales.
     */
    fun hourlyBuckets(
        session: Session,
        events: List<SoundEvent>,
        zoneOffsetMs: Long = 0L,
    ): List<HourBucket> {
        val end = session.endEpochMs ?: events.maxOfOrNull { it.endEpochMs } ?: return emptyList()
        if (end <= session.startEpochMs) return emptyList()
        val firstHour = Math.floorDiv(session.startEpochMs + zoneOffsetMs, HOUR_MS) * HOUR_MS - zoneOffsetMs
        val hours = ((end - firstHour + HOUR_MS - 1) / HOUR_MS).toInt().coerceAtLeast(1)
        val snoreMs = LongArray(hours)
        val body = IntArray(hours)
        val bodyKeys = NightSummary.BODY_CATEGORIES.map { it.key }.toSet()
        for (event in events) {
            val category = ApneaHeuristic.effectiveCategory(event)
            val idx = ((event.startEpochMs - firstHour) / HOUR_MS).toInt()
            if (idx in 0 until hours && category in bodyKeys) body[idx]++
            if (category != SomniaCategory.SNORING.key) continue
            var t = event.startEpochMs
            while (t < event.endEpochMs) {
                val h = ((t - firstHour) / HOUR_MS).toInt()
                val hourEnd = firstHour + (h + 1) * HOUR_MS
                val segmentEnd = minOf(hourEnd, event.endEpochMs)
                if (h in 0 until hours) snoreMs[h] += segmentEnd - t
                t = segmentEnd
            }
        }
        return (0 until hours).map { h ->
            HourBucket(firstHour + h * HOUR_MS, snoreMs[h] / 60_000.0, body[h])
        }
    }

    fun summarize(
        session: Session,
        events: List<SoundEvent>,
        samples: List<NoiseSample>,
    ): NightSummary {
        val durationMinutes = session.endEpochMs?.let { (it - session.startEpochMs) / 60_000 }

        val counts = mutableMapOf<SomniaCategory, Int>()
        var unknown = 0
        for (event in events) {
            val category = SomniaCategory.fromKey(ApneaHeuristic.effectiveCategory(event))
            if (category == null) unknown++ else counts[category] = (counts[category] ?: 0) + 1
        }

        val snores = events.filter {
            ApneaHeuristic.effectiveCategory(it) == SomniaCategory.SNORING.key
        }
        val snoringMinutes = snores.sumOf { it.durationMs } / 60_000.0

        val peakDb = samples.maxOfOrNull { it.dbMax }
            ?.let { (it + session.calibrationDbOffset).roundToInt() }

        return NightSummary(
            durationMinutes = durationMinutes,
            totalEvents = events.size,
            countsByCategory = counts,
            unknownCount = unknown,
            snoringEpisodes = snores.size,
            snoringMinutes = snoringMinutes,
            pausePatternCount = ApneaHeuristic.detect(events).size,
            peakDb = peakDb,
        )
    }
}
