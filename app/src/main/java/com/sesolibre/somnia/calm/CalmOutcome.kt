package com.sesolibre.somnia.calm

import com.sesolibre.somnia.data.db.CalmPlayback
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory

/**
 * Qué pasó alrededor de una reproducción del sonido de calma: cuánto se roncó en los
 * [WINDOW_MS] anteriores y en los [WINDOW_MS] posteriores a que terminara. Es una
 * comparación, no una prueba: el ronquido también cambia solo.
 */
data class CalmOutcome(
    val startEpochMs: Long,
    val snoringBeforeMs: Long,
    /** null si la noche acabó (o el sonido sigue) antes de completar la ventana posterior. */
    val snoringAfterMs: Long?,
) {
    companion object {
        const val WINDOW_MS = 10 * 60_000L

        fun of(playbacks: List<CalmPlayback>, events: List<SoundEvent>, sessionEndMs: Long?): List<CalmOutcome> {
            val snores = events.filter { ApneaHeuristic.effectiveCategory(it) == SomniaCategory.SNORING.key }
            fun snoringIn(from: Long, to: Long): Long =
                snores.sumOf { (minOf(it.endEpochMs, to) - maxOf(it.startEpochMs, from)).coerceAtLeast(0L) }
            return playbacks.sortedBy { it.startEpochMs }.map { p ->
                val end = p.endEpochMs
                val complete = end != null && sessionEndMs != null && sessionEndMs >= end + WINDOW_MS
                CalmOutcome(
                    startEpochMs = p.startEpochMs,
                    snoringBeforeMs = snoringIn(p.startEpochMs - WINDOW_MS, p.startEpochMs),
                    snoringAfterMs = if (complete) snoringIn(end!!, end + WINDOW_MS) else null,
                )
            }
        }
    }
}
