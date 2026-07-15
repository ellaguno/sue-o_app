package com.sesolibre.somnia.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Perfil del usuario (fila única, id fijo [UserProfile.SINGLETON_ID]).
 * Los datos corporales alimentan STOP-Bang (IMC, cuello, edad, sexo);
 * todos son opcionales.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Long = SINGLETON_ID,
    val displayName: String? = null,
    val birthYear: Int? = null,
    /** "m" | "f" | null. STOP-Bang suma un punto si es masculino. */
    val sex: String? = null,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    /** Circunferencia de cuello (STOP-Bang: >40 cm suma un punto). */
    val neckCm: Int? = null,
    /** Si es false, los reportes avisan que los sonidos pueden ser de otros. */
    val sleepsAlone: Boolean = true,
) {
    val age: Int?
        get() = birthYear?.let { java.time.Year.now().value - it }

    val bmi: Double?
        get() {
            val h = heightCm ?: return null
            val w = weightKg ?: return null
            if (h <= 0) return null
            val meters = h / 100.0
            return w / (meters * meters)
        }

    companion object {
        const val SINGLETON_ID = 1L
        const val SEX_MALE = "m"
        const val SEX_FEMALE = "f"
    }
}

/** Persona que comparte la habitación (pareja, bebé, mascota con nombre…). */
@Entity(tableName = "companions")
data class SleepCompanion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Cuándo el usuario confirmó haber avisado a esta persona que se graba audio. */
    val consentAckEpochMs: Long,
    /** Baja lógica: se conserva para no romper atribuciones históricas. */
    val active: Boolean = true,
)

/** Resultado de un cuestionario validado (Epworth, STOP-Bang). */
@Entity(tableName = "questionnaire_results")
data class QuestionnaireResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [TYPE_EPWORTH] | [TYPE_STOP_BANG] */
    val type: String,
    val answeredEpochMs: Long,
    val score: Int,
    /** Clave de interpretación (p. ej. "normal", "moderate", "high"). */
    val riskLevel: String,
    /** Respuestas crudas separadas por coma, en el orden del cuestionario. */
    val answersCsv: String,
) {
    companion object {
        const val TYPE_EPWORTH = "epworth"
        const val TYPE_STOP_BANG = "stopbang"
    }
}

/** Bitácora rápida de una noche: chips de hábitos + nota libre. */
@Entity(
    tableName = "night_logs",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class NightLog(
    @PrimaryKey val sessionId: Long,
    val updatedEpochMs: Long,
    /** Claves de [NightTag] separadas por coma. */
    val tagsCsv: String,
    val note: String? = null,
) {
    val tags: Set<NightTag>
        get() = tagsCsv.split(',')
            .mapNotNull { key -> NightTag.entries.firstOrNull { it.key == key.trim() } }
            .toSet()

    companion object {
        fun csvOf(tags: Collection<NightTag>): String =
            tags.joinToString(",") { it.key }
    }
}

/** Chips de la bitácora nocturna. */
enum class NightTag(val key: String) {
    CAFFEINE("caffeine"),
    ALCOHOL("alcohol"),
    EXERCISE("exercise"),
    LATE_DINNER("late_dinner"),
    STRESS("stress"),
    MEDICATION("medication"),
    SICK("sick"),
    SCREEN_LATE("screen_late"),
}
