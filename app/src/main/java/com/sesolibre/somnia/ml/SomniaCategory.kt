package com.sesolibre.somnia.ml

/**
 * Categorías propias de la app. Cada evento se clasifica en una de estas a
 * partir de las 521 clases de AudioSet que reporta YAMNet.
 * [key] es el valor que se persiste en la base de datos.
 */
enum class SomniaCategory(val key: String) {
    SNORING("snoring"),
    BREATHING("breathing"),
    COUGH("cough"),
    SPEECH("speech"),
    MOVEMENT("movement"),
    ENVIRONMENT("environment"),
    OTHER("other");

    companion object {
        fun fromKey(key: String?): SomniaCategory? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Mapea el nombre de una clase AudioSet a una categoría de Somnia.
 *
 * Compara PALABRAS completas, no subcadenas: con `contains` "infant" casaba con
 * "fan" (llanto de bebé → ambiente) y "mastication" con "cat" (masticar →
 * ambiente). Una palabra clave de varias palabras ("throat clearing") debe
 * aparecer seguida en el nombre.
 */
object CategoryMapper {

    // Antes que las reglas: tokenizedRules lo usa al inicializarse el objeto.
    private val NON_LETTERS = Regex("[^a-z]+")

    private val rules: List<Pair<SomniaCategory, List<String>>> = listOf(
        SomniaCategory.SNORING to listOf("snoring", "snort"),
        SomniaCategory.BREATHING to listOf(
            "breathing", "gasp", "wheeze", "pant", "sigh", "hiccup",
        ),
        SomniaCategory.COUGH to listOf("cough", "throat clearing", "sneeze", "sniff"),
        SomniaCategory.SPEECH to listOf(
            "speech", "conversation", "narration", "babbling", "whispering",
            "laughter", "giggle", "chuckle", "singing", "humming", "shout",
            "yell", "screaming", "crying", "cry", "sobbing", "whimper",
            // Vocalizaciones dormido (p. ej. quejidos al exhalar).
            "groan", "grunt",
        ),
        SomniaCategory.MOVEMENT to listOf(
            "rustle", "rustling", "squeak", "creak", "door", "cupboard",
            "drawer", "footsteps", "walk", "thump", "thud", "knock", "tap",
            "zipper", "velcro", "cloth", "fabric",
            // Masticar / rechinar dientes (bruxismo) es movimiento del cuerpo.
            "chewing", "mastication",
        ),
        SomniaCategory.ENVIRONMENT to listOf(
            "vehicle", "car", "truck", "motorcycle", "traffic", "engine",
            "aircraft", "airplane", "helicopter", "train", "siren", "horn",
            "dog", "dogs", "bark", "cat", "cats", "caterwaul", "meow", "bird",
            "rooster", "insect", "cricket", "mosquito", "rain", "thunder",
            "thunderstorm", "wind", "water", "toilet", "music", "television",
            "radio", "alarm", "clock", "tick", "doorbell", "air conditioning",
            "fan", "hum", "refrigerator",
            // Aparatos de ruido blanco, estática, ruido de fondo genérico.
            "noise", "static",
        ),
    )

    /** Palabras de cada palabra clave, precalculadas. */
    private val tokenizedRules = rules.map { (category, keywords) ->
        category to keywords.map { tokenize(it) }
    }

    fun fromAudioSetLabel(label: String): SomniaCategory {
        val words = tokenize(label)
        for ((category, keywords) in tokenizedRules) {
            if (keywords.any { containsSequence(words, it) }) return category
        }
        return SomniaCategory.OTHER
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(NON_LETTERS).filter { it.isNotEmpty() }

    private fun containsSequence(words: List<String>, keyword: List<String>): Boolean {
        if (keyword.isEmpty() || keyword.size > words.size) return false
        return (0..words.size - keyword.size).any { start ->
            keyword.indices.all { words[start + it] == keyword[it] }
        }
    }

}
