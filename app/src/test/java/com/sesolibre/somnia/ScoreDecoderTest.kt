package com.sesolibre.somnia

import com.sesolibre.somnia.ml.ScoreDecoder
import com.sesolibre.somnia.ml.SomniaCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreDecoderTest {

    private val labels = listOf("Silence", "Inside, small room", "Snoring", "Breathing", "Walk, footsteps", "Speech")

    private fun scores(vararg pairs: Pair<String, Float>): FloatArray {
        val s = FloatArray(labels.size)
        pairs.forEach { (label, v) -> s[labels.indexOf(label)] = v }
        return s
    }

    @Test
    fun `Silence e Inside no ganan aunque tengan el puntaje mas alto`() {
        val d = ScoreDecoder.decide(
            scores("Silence" to 0.59f, "Inside, small room" to 0.26f, "Walk, footsteps" to 0.41f),
            labels,
        )
        assertEquals("Walk, footsteps", d.label)
        assertEquals(SomniaCategory.MOVEMENT, d.category)
    }

    @Test
    fun `lo real pero debil queda en OTHER con su etiqueta`() {
        val d = ScoreDecoder.decide(scores("Silence" to 0.67f, "Speech" to 0.04f), labels)
        assertEquals("Speech", d.label)
        assertEquals(SomniaCategory.OTHER, d.category)
    }

    @Test
    fun `empate ronquido-respiracion se resuelve a ronquido`() {
        val d = ScoreDecoder.decide(scores("Breathing" to 0.89f, "Snoring" to 0.86f), labels)
        assertEquals(SomniaCategory.SNORING, d.category)
        assertEquals(0.86f, d.score, 1e-6f)
    }

    @Test
    fun `respiracion clara sigue siendo respiracion`() {
        val d = ScoreDecoder.decide(scores("Breathing" to 0.9f, "Snoring" to 0.3f), labels)
        assertEquals(SomniaCategory.BREATHING, d.category)
    }
}
