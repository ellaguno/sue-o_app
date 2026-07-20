package com.sesolibre.somnia.stats

import com.sesolibre.somnia.data.db.NightTag
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory

/** Por qué un clip entró a "lo más relevante". */
enum class HighlightReason { PAUSE_PATTERN, LOUDEST_SNORE, SPEECH, COUGH, LOUDEST }

data class Highlight(val event: SoundEvent, val reason: HighlightReason)

/**
 * "Lo más relevante de anoche": selecciona unos pocos clips que vale la pena
 * escuchar, priorizando lo clínicamente interesante. Lógica pura, testeable.
 */
object Highlights {

    fun topClips(
        events: List<SoundEvent>,
        patterns: List<ApneaHeuristic.Pattern>,
        max: Int = 3,
    ): List<Highlight> {
        val withClip = events.filter { it.clipPath != null }
        // LinkedHashMap: preserva el orden de prioridad y deduplica por id.
        val picked = LinkedHashMap<Long, Highlight>()
        fun add(event: SoundEvent, reason: HighlightReason) {
            if (picked.size < max) picked.putIfAbsent(event.id, Highlight(event, reason))
        }
        fun cat(e: SoundEvent) = ApneaHeuristic.effectiveCategory(e)

        // 1. El ronquido que precede a una pausa (lo más relevante para el médico).
        val byEnd = withClip.groupBy { it.endEpochMs }
        patterns.forEach { p -> byEnd[p.snoreEndEpochMs]?.firstOrNull()?.let { add(it, HighlightReason.PAUSE_PATTERN) } }
        // 2. Ronquidos más fuertes.
        withClip.filter { cat(it) == SomniaCategory.SNORING.key }
            .sortedByDescending { it.dbPeak }
            .forEach { add(it, HighlightReason.LOUDEST_SNORE) }
        // 3. Habla (primero la que ya tiene transcripción).
        withClip.filter { cat(it) == SomniaCategory.SPEECH.key }
            .sortedByDescending { it.transcript != null }
            .forEach { add(it, HighlightReason.SPEECH) }
        // 4. Tos más fuerte.
        withClip.filter { cat(it) == SomniaCategory.COUGH.key }
            .sortedByDescending { it.dbPeak }
            .forEach { add(it, HighlightReason.COUGH) }
        // 5. Relleno con lo más fuerte de la noche.
        withClip.sortedByDescending { it.dbPeak }.forEach { add(it, HighlightReason.LOUDEST) }

        return picked.values.take(max)
    }
}

/** Recomendación de higiene del sueño (basada en reglas; NO diagnóstico). */
enum class SleepTip { PAUSE_PATTERN, ALCOHOL, REDUCE_SNORING, LATE_DINNER, CAFFEINE, SCREEN_LATE, STRESS, SHORT_SLEEP }

/**
 * Reglas simples que traducen los datos de la noche + la bitácora en consejos.
 * Descriptivo y no clínico; solo pistas para el usuario. Lógica pura.
 */
object SleepTips {

    private const val SNORING_MINUTES_THRESHOLD = 5.0
    private const val SNORING_EPISODES_THRESHOLD = 8
    private const val SHORT_SLEEP_MINUTES = 360L // 6 h

    fun forNight(summary: NightSummary, tags: Set<NightTag>): List<SleepTip> {
        val tips = mutableListOf<SleepTip>()
        val snoresALot = summary.snoringMinutes >= SNORING_MINUTES_THRESHOLD ||
            summary.snoringEpisodes >= SNORING_EPISODES_THRESHOLD

        if (summary.pausePatternCount > 0) tips += SleepTip.PAUSE_PATTERN
        if (snoresALot && NightTag.ALCOHOL in tags) tips += SleepTip.ALCOHOL
        if (snoresALot) tips += SleepTip.REDUCE_SNORING
        if (NightTag.LATE_DINNER in tags) tips += SleepTip.LATE_DINNER
        if (NightTag.CAFFEINE in tags) tips += SleepTip.CAFFEINE
        if (NightTag.SCREEN_LATE in tags) tips += SleepTip.SCREEN_LATE
        if (NightTag.STRESS in tags) tips += SleepTip.STRESS
        if (summary.durationMinutes != null && summary.durationMinutes < SHORT_SLEEP_MINUTES) {
            tips += SleepTip.SHORT_SLEEP
        }
        return tips
    }
}
