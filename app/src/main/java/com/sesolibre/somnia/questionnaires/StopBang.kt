package com.sesolibre.somnia.questionnaires

/**
 * Cuestionario STOP-Bang de riesgo de apnea obstructiva del sueño:
 * 8 ítems sí/no (Snoring, Tiredness, Observed, Pressure, BMI, Age, Neck,
 * Gender). Puntaje = número de "sí". NO es un diagnóstico.
 *
 * Los ítems B/A/N/G pueden precargarse desde el perfil (IMC > 35,
 * edad > 50, cuello > 40 cm, sexo masculino).
 */
object StopBang {

    const val QUESTION_COUNT = 8

    const val LEVEL_LOW = "low"                   // 0–2
    const val LEVEL_INTERMEDIATE = "intermediate" // 3–4
    const val LEVEL_HIGH = "high"                 // 5–8

    const val BMI_THRESHOLD = 35.0
    const val AGE_THRESHOLD = 50
    const val NECK_CM_THRESHOLD = 40

    fun score(answers: List<Boolean>): Int {
        require(answers.size == QUESTION_COUNT) {
            "STOP-Bang requiere $QUESTION_COUNT respuestas, llegaron ${answers.size}"
        }
        return answers.count { it }
    }

    fun riskLevel(score: Int): String = when {
        score <= 2 -> LEVEL_LOW
        score <= 4 -> LEVEL_INTERMEDIATE
        else -> LEVEL_HIGH
    }

    /** Prellenado de los ítems B/A/N/G desde datos del perfil (null = desconocido). */
    fun bmiItem(bmi: Double?): Boolean? = bmi?.let { it > BMI_THRESHOLD }
    fun ageItem(age: Int?): Boolean? = age?.let { it > AGE_THRESHOLD }
    fun neckItem(neckCm: Int?): Boolean? = neckCm?.let { it > NECK_CM_THRESHOLD }
    fun genderItem(sex: String?): Boolean? = sex?.let { it == "m" }
}
