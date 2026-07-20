package com.sesolibre.somnia.stats

import com.sesolibre.somnia.data.db.NightLog
import com.sesolibre.somnia.data.db.NightTag
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SoundEvent

/** Métricas de una noche para las gráficas de tendencias. */
data class NightMetrics(
    val sessionId: Long,
    val startEpochMs: Long,
    val durationMinutes: Long?,
    val snoringEpisodes: Int,
    val snoringMinutes: Double,
    val bodyEventCount: Int,
    val pausePatternCount: Int,
    val peakDb: Int?,
    val tags: Set<NightTag>,
)

/** Promedios sobre el conjunto de noches (encabezado de Tendencias). */
data class TrendsOverview(
    val nights: Int,
    val avgSnoringMinutes: Double,
    val avgBodyEvents: Double,
    val avgDurationMinutes: Double,
    val nightsWithPausePattern: Int,
)

/**
 * Efecto simple de una etiqueta de bitácora sobre los ronquidos: promedio de
 * minutos roncando en las noches CON la etiqueta vs las noches SIN ella. No es
 * causalidad, solo una correlación descriptiva para que el usuario la explore.
 */
data class TagEffect(
    val tag: NightTag,
    val nightsWith: Int,
    val nightsWithout: Int,
    val avgSnoringWith: Double,
    val avgSnoringWithout: Double,
) {
    val deltaMinutes: Double get() = avgSnoringWith - avgSnoringWithout
}

/** Agregación de tendencias entre noches; lógica pura, testeable en JVM. */
object TrendsAnalyzer {

    /** Noches más cortas que esto se consideran pruebas/abortos y se excluyen. */
    const val MIN_NIGHT_MINUTES = 5L

    /** Se necesita al menos una noche con y una sin la etiqueta para comparar. */
    private const val MIN_NIGHTS_PER_GROUP = 1

    fun metrics(
        sessions: List<Session>,
        events: List<SoundEvent>,
        samples: List<NoiseSample>,
        logs: List<NightLog>,
    ): List<NightMetrics> {
        val eventsBySession = events.groupBy { it.sessionId }
        val samplesBySession = samples.groupBy { it.sessionId }
        val tagsBySession = logs.associate { it.sessionId to it.tags }

        return sessions
            .filter { session ->
                val minutes = session.endEpochMs?.let { (it - session.startEpochMs) / 60_000 }
                minutes != null && minutes >= MIN_NIGHT_MINUTES
            }
            .map { session ->
                val summary = NightAnalyzer.summarize(
                    session = session,
                    events = eventsBySession[session.id].orEmpty(),
                    samples = samplesBySession[session.id].orEmpty(),
                )
                NightMetrics(
                    sessionId = session.id,
                    startEpochMs = session.startEpochMs,
                    durationMinutes = summary.durationMinutes,
                    snoringEpisodes = summary.snoringEpisodes,
                    snoringMinutes = summary.snoringMinutes,
                    bodyEventCount = summary.bodyEventCount,
                    pausePatternCount = summary.pausePatternCount,
                    peakDb = summary.peakDb,
                    tags = tagsBySession[session.id].orEmpty(),
                )
            }
            .sortedBy { it.startEpochMs } // cronológico: izquierda→derecha en la gráfica
    }

    fun overview(metrics: List<NightMetrics>): TrendsOverview {
        if (metrics.isEmpty()) return TrendsOverview(0, 0.0, 0.0, 0.0, 0)
        return TrendsOverview(
            nights = metrics.size,
            avgSnoringMinutes = metrics.map { it.snoringMinutes }.average(),
            avgBodyEvents = metrics.map { it.bodyEventCount.toDouble() }.average(),
            avgDurationMinutes = metrics.mapNotNull { it.durationMinutes?.toDouble() }
                .ifEmpty { listOf(0.0) }.average(),
            nightsWithPausePattern = metrics.count { it.pausePatternCount > 0 },
        )
    }

    /**
     * Correlaciones ronquido↔bitácora: para cada etiqueta con noches a ambos
     * lados, el promedio de minutos roncando con y sin ella. Ordenadas por el
     * efecto absoluto (las más llamativas primero).
     */
    fun correlations(metrics: List<NightMetrics>): List<TagEffect> {
        return NightTag.entries.mapNotNull { tag ->
            val (with, without) = metrics.partition { tag in it.tags }
            if (with.size < MIN_NIGHTS_PER_GROUP || without.size < MIN_NIGHTS_PER_GROUP) {
                return@mapNotNull null
            }
            TagEffect(
                tag = tag,
                nightsWith = with.size,
                nightsWithout = without.size,
                avgSnoringWith = with.map { it.snoringMinutes }.average(),
                avgSnoringWithout = without.map { it.snoringMinutes }.average(),
            )
        }.sortedByDescending { kotlin.math.abs(it.deltaMinutes) }
    }
}
