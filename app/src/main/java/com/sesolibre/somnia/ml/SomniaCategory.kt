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

/** Mapea el nombre de una clase AudioSet a una categoría de Somnia. */
object CategoryMapper {

    private val rules: List<Pair<SomniaCategory, List<String>>> = listOf(
        SomniaCategory.SNORING to listOf("snoring", "snort"),
        SomniaCategory.BREATHING to listOf("breathing", "gasp", "wheeze", "pant", "sigh"),
        SomniaCategory.COUGH to listOf("cough", "throat clearing", "sneeze", "sniff"),
        SomniaCategory.SPEECH to listOf(
            "speech", "conversation", "narration", "babbling", "whispering",
            "laughter", "giggle", "chuckle", "singing", "humming", "shout",
            "yell", "screaming", "crying", "sobbing", "whimper",
        ),
        SomniaCategory.MOVEMENT to listOf(
            "rustle", "rustling", "squeak", "creak", "door", "cupboard",
            "drawer", "footsteps", "walk", "thump", "thud", "knock", "tap",
            "zipper", "velcro",
        ),
        SomniaCategory.ENVIRONMENT to listOf(
            "vehicle", "car", "truck", "motorcycle", "traffic", "engine",
            "aircraft", "airplane", "helicopter", "train", "siren", "horn",
            "dog", "bark", "cat", "meow", "bird", "rooster", "insect",
            "cricket", "mosquito", "rain", "thunder", "wind", "water",
            "toilet", "music", "television", "radio", "alarm", "clock",
            "tick", "air conditioning", "fan", "mechanical fan", "hum",
            "refrigerator", "snoring cat",
        ),
    )

    fun fromAudioSetLabel(label: String): SomniaCategory {
        val l = label.lowercase()
        for ((category, keywords) in rules) {
            if (keywords.any { l.contains(it) }) return category
        }
        return SomniaCategory.OTHER
    }
}
