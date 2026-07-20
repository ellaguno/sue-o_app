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

/** Cálculo puro del resumen de una noche; testeable en JVM sin emulador. */
object NightAnalyzer {

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
