package com.sesolibre.somnia.export

import android.content.Context
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.db.NightTag
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.questionnaires.Epworth
import com.sesolibre.somnia.questionnaires.StopBang
import com.sesolibre.somnia.stats.HighlightReason
import com.sesolibre.somnia.stats.SleepTip

/**
 * Textos de los reportes (PDF, correo) fuera de Compose: las mismas etiquetas
 * que muestra la UI, pero leídas con un [Context].
 */
internal object ReportLabels {

    fun category(context: Context, key: String?): String = context.getString(
        when (SomniaCategory.fromKey(key)) {
            SomniaCategory.SNORING -> R.string.cat_snoring
            SomniaCategory.BREATHING -> R.string.cat_breathing
            SomniaCategory.COUGH -> R.string.cat_cough
            SomniaCategory.SPEECH -> R.string.cat_speech
            SomniaCategory.MOVEMENT -> R.string.cat_movement
            SomniaCategory.ENVIRONMENT -> R.string.cat_environment
            SomniaCategory.OTHER -> R.string.cat_other
            null -> R.string.cat_unknown
        },
    )

    fun category(context: Context, category: SomniaCategory): String = category(context, category.key)

    fun reason(context: Context, reason: HighlightReason): String = context.getString(
        when (reason) {
            HighlightReason.PAUSE_PATTERN -> R.string.highlight_pause_pattern
            HighlightReason.LOUDEST_SNORE -> R.string.highlight_loud_snore
            HighlightReason.SPEECH -> R.string.highlight_speech
            HighlightReason.COUGH -> R.string.highlight_cough
            HighlightReason.LOUDEST -> R.string.highlight_loudest
        },
    )

    fun tip(context: Context, tip: SleepTip): String = context.getString(
        when (tip) {
            SleepTip.PAUSE_PATTERN -> R.string.tip_pause_pattern
            SleepTip.ALCOHOL -> R.string.tip_alcohol
            SleepTip.REDUCE_SNORING -> R.string.tip_reduce_snoring
            SleepTip.LATE_DINNER -> R.string.tip_late_dinner
            SleepTip.CAFFEINE -> R.string.tip_caffeine
            SleepTip.SCREEN_LATE -> R.string.tip_screen_late
            SleepTip.STRESS -> R.string.tip_stress
            SleepTip.SHORT_SLEEP -> R.string.tip_short_sleep
        },
    )

    fun tag(context: Context, tag: NightTag): String = context.getString(
        when (tag) {
            NightTag.CAFFEINE -> R.string.tag_caffeine
            NightTag.ALCOHOL -> R.string.tag_alcohol
            NightTag.EXERCISE -> R.string.tag_exercise
            NightTag.LATE_DINNER -> R.string.tag_late_dinner
            NightTag.STRESS -> R.string.tag_stress
            NightTag.MEDICATION -> R.string.tag_medication
            NightTag.SICK -> R.string.tag_sick
            NightTag.SCREEN_LATE -> R.string.tag_screen_late
        },
    )

    fun epworthLevel(context: Context, level: String): String = context.getString(
        when (level) {
            Epworth.LEVEL_NORMAL -> R.string.epworth_level_normal
            Epworth.LEVEL_MILD -> R.string.epworth_level_mild
            Epworth.LEVEL_MODERATE -> R.string.epworth_level_moderate
            else -> R.string.epworth_level_severe
        },
    )

    fun stopBangLevel(context: Context, level: String): String = context.getString(
        when (level) {
            StopBang.LEVEL_LOW -> R.string.stopbang_level_low
            StopBang.LEVEL_INTERMEDIATE -> R.string.stopbang_level_intermediate
            else -> R.string.stopbang_level_high
        },
    )

    /** "7h 05m" */
    fun duration(minutes: Long?): String =
        minutes?.let { "%dh %02dm".format(it / 60, it % 60) } ?: "—"
}
