package com.sesolibre.somnia.ml

/**
 * Convierte los puntajes de YAMNet (máximo por clase entre ventanas) en la
 * clase final del evento. Lógica pura, testeable en JVM.
 *
 * Dos reglas aprendidas con noches reales (sesión 7, jul 2026):
 * 1. **Clases no informativas fuera del argmax.** "Silence" e "Inside, small
 *    room" describen el cuarto, no el sonido; ganaban el top-1 en eventos que,
 *    al quitarlas, resultaban ser pasos, puertas o roce de sábanas.
 * 2. **Desempate ronquido↔respiración.** En ronquidos suaves YAMNet da casi el
 *    mismo puntaje a ambas (0.89/0.89); si Snoring queda a menos de
 *    [SNORE_TIE_MARGIN] de Breathing, se elige ronquido.
 */
object ScoreDecoder {

    data class Decision(val label: String, val score: Float, val category: SomniaCategory)

    /** Describen el ambiente acústico, no lo que sonó. */
    val NON_INFORMATIVE = setOf(
        "Silence",
        "Inside, small room",
        "Inside, large room or hall",
        "Inside, public space",
        "Outside, urban or manmade",
        "Outside, rural or natural",
    )

    const val MIN_CONFIDENCE = 0.2f
    const val SNORE_TIE_MARGIN = 0.05f
    private const val SNORING_LABEL = "Snoring"

    fun decide(scores: FloatArray, labels: List<String>): Decision {
        var topIdx = -1
        for (i in scores.indices) {
            if (labels.getOrNull(i) in NON_INFORMATIVE) continue
            if (topIdx < 0 || scores[i] > scores[topIdx]) topIdx = i
        }
        if (topIdx < 0) return Decision("?", 0f, SomniaCategory.OTHER)

        var label = labels.getOrElse(topIdx) { "?" }
        var score = scores[topIdx]
        var category = CategoryMapper.fromAudioSetLabel(label)

        if (category == SomniaCategory.BREATHING) {
            val snoreIdx = labels.indexOf(SNORING_LABEL)
            if (snoreIdx in scores.indices && scores[snoreIdx] >= score - SNORE_TIE_MARGIN) {
                label = SNORING_LABEL
                score = scores[snoreIdx]
                category = SomniaCategory.SNORING
            }
        }
        if (score < MIN_CONFIDENCE) category = SomniaCategory.OTHER
        return Decision(label, score, category)
    }
}
