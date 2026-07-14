package com.sesolibre.somnia

import com.sesolibre.somnia.ml.CategoryMapper
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.ml.YamnetClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryMapperTest {

    @Test
    fun `clases clave de AudioSet mapean a la categoria correcta`() {
        val cases = mapOf(
            "Snoring" to SomniaCategory.SNORING,
            "Snort" to SomniaCategory.SNORING,
            "Breathing" to SomniaCategory.BREATHING,
            "Gasp" to SomniaCategory.BREATHING,
            "Wheeze" to SomniaCategory.BREATHING,
            "Cough" to SomniaCategory.COUGH,
            "Throat clearing" to SomniaCategory.COUGH,
            "Speech" to SomniaCategory.SPEECH,
            "Whispering" to SomniaCategory.SPEECH,
            "Laughter" to SomniaCategory.SPEECH,
            "Walk, footsteps" to SomniaCategory.MOVEMENT,
            "Door" to SomniaCategory.MOVEMENT,
            "Vehicle" to SomniaCategory.ENVIRONMENT,
            "Dog" to SomniaCategory.ENVIRONMENT,
            "Rain" to SomniaCategory.ENVIRONMENT,
            "Mechanical fan" to SomniaCategory.ENVIRONMENT,
            "Air conditioning" to SomniaCategory.ENVIRONMENT,
            "Music" to SomniaCategory.ENVIRONMENT,
        )
        for ((label, expected) in cases) {
            assertEquals("label=$label", expected, CategoryMapper.fromAudioSetLabel(label))
        }
    }

    @Test
    fun `clases desconocidas caen en OTHER`() {
        assertEquals(SomniaCategory.OTHER, CategoryMapper.fromAudioSetLabel("Theremin"))
        assertEquals(SomniaCategory.OTHER, CategoryMapper.fromAudioSetLabel("Didgeridoo"))
    }

    @Test
    fun `parseo de nombres del csv con y sin comillas`() {
        assertEquals("Speech", YamnetClassifier.parseDisplayName("0,/m/09x0r,Speech"))
        assertEquals(
            "Child speech, kid speaking",
            YamnetClassifier.parseDisplayName("1,/m/0ytgt,\"Child speech, kid speaking\""),
        )
        assertEquals("Snoring", YamnetClassifier.parseDisplayName("38,/m/01d3sd,Snoring"))
    }

    @Test
    fun `fromKey resuelve claves persistidas`() {
        assertEquals(SomniaCategory.SNORING, SomniaCategory.fromKey("snoring"))
        assertEquals(null, SomniaCategory.fromKey("unknown"))
        assertEquals(null, SomniaCategory.fromKey(null))
    }
}
