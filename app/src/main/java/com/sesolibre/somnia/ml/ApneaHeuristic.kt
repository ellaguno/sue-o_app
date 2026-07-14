package com.sesolibre.somnia.ml

import com.sesolibre.somnia.data.db.SoundEvent

/**
 * Heurística de "patrón a comentar con un médico": ronquido, seguido de una
 * pausa de silencio prolongada, seguida de una reanudación brusca
 * (resoplido/jadeo o más ronquido con nivel similar o mayor).
 *
 * NO diagnostica apnea; solo cuenta ocurrencias del patrón acústico para que
 * el usuario pueda comentarlo con un profesional. Lógica pura, testeable.
 */
object ApneaHeuristic {

    data class Pattern(
        val snoreEndEpochMs: Long,
        val resumeEpochMs: Long,
    ) {
        val pauseMs: Long get() = resumeEpochMs - snoreEndEpochMs
    }

    private const val MIN_PAUSE_MS = 10_000L
    private const val MAX_PAUSE_MS = 120_000L
    private const val RESUME_PEAK_TOLERANCE_DB = 3.0

    private val resumeCategories = setOf(
        SomniaCategory.SNORING.key,
        SomniaCategory.BREATHING.key,
    )

    fun effectiveCategory(event: SoundEvent): String = event.manualLabel ?: event.category

    fun detect(events: List<SoundEvent>): List<Pattern> {
        val sorted = events.sortedBy { it.startEpochMs }
        val patterns = mutableListOf<Pattern>()
        for (i in 0 until sorted.size - 1) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (effectiveCategory(a) != SomniaCategory.SNORING.key) continue
            if (effectiveCategory(b) !in resumeCategories) continue
            val pause = b.startEpochMs - a.endEpochMs
            if (pause < MIN_PAUSE_MS || pause > MAX_PAUSE_MS) continue
            if (b.dbPeak < a.dbPeak - RESUME_PEAK_TOLERANCE_DB) continue
            patterns.add(
                Pattern(snoreEndEpochMs = a.endEpochMs, resumeEpochMs = b.startEpochMs)
            )
        }
        return patterns
    }
}
