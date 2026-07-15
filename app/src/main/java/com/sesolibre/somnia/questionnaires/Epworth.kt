package com.sesolibre.somnia.questionnaires

/**
 * Escala de Somnolencia de Epworth (ESS): 8 situaciones, cada una con
 * probabilidad de dormitar 0–3. Puntaje total 0–24.
 *
 * Lógica pura, sin dependencias de Android. Los textos viven en strings.xml;
 * aquí solo el conteo y la interpretación estándar. NO es un diagnóstico.
 */
object Epworth {

    const val QUESTION_COUNT = 8
    const val MIN_ANSWER = 0
    const val MAX_ANSWER = 3

    const val LEVEL_NORMAL = "normal"       // 0–7: somnolencia dentro de lo normal
    const val LEVEL_MILD = "mild"           // 8–9: promedio alto
    const val LEVEL_MODERATE = "moderate"   // 10–15: somnolencia excesiva
    const val LEVEL_SEVERE = "severe"       // 16–24: somnolencia severa

    fun score(answers: List<Int>): Int {
        require(answers.size == QUESTION_COUNT) {
            "Epworth requiere $QUESTION_COUNT respuestas, llegaron ${answers.size}"
        }
        require(answers.all { it in MIN_ANSWER..MAX_ANSWER }) {
            "Cada respuesta debe estar entre $MIN_ANSWER y $MAX_ANSWER"
        }
        return answers.sum()
    }

    fun riskLevel(score: Int): String = when {
        score <= 7 -> LEVEL_NORMAL
        score <= 9 -> LEVEL_MILD
        score <= 15 -> LEVEL_MODERATE
        else -> LEVEL_SEVERE
    }
}
