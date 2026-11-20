package com.sesolibre.somnia

import com.sesolibre.somnia.calm.CalmOutcome
import com.sesolibre.somnia.data.db.CalmPlayback
import com.sesolibre.somnia.data.db.SoundEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalmOutcomeTest {
    private val minute = 60_000L

    private fun event(startMin: Double, seconds: Int, category: String = "snoring", manual: String? = null) = SoundEvent(
        sessionId = 1,
        startEpochMs = (startMin * minute).toLong(),
        endEpochMs = (startMin * minute).toLong() + seconds * 1000L,
        durationMs = seconds * 1000L,
        dbPeak = -20.0,
        dbAvg = -30.0,
        category = category,
        manualLabel = manual,
    )

    @Test
    fun `suma el ronquido de antes y de despues, sin lo que sono durante`() {
        val playback = CalmPlayback(sessionId = 1, startEpochMs = 60 * minute, endEpochMs = 70 * minute)
        val events = listOf(
            event(45.0, 30),                 // fuera de la ventana de antes
            event(52.0, 20),                 // antes
            event(59.9, 30),                 // antes: solo cuentan los 6 s previos al inicio
            event(65.0, 40),                 // durante: no cuenta
            event(71.0, 10),                 // después
            event(75.0, 60, category = "cough"), // no es ronquido
            event(78.0, 15, category = "other", manual = "snoring"), // reetiquetado a mano
            event(85.0, 30),                 // fuera de la ventana de después
        )
        val o = CalmOutcome.of(listOf(playback), events, sessionEndMs = 300 * minute).single()
        assertEquals(26_000L, o.snoringBeforeMs)
        assertEquals(25_000L, o.snoringAfterMs)
    }

    @Test
    fun `sin ventana posterior completa no hay dato de despues`() {
        val events = listOf(event(71.0, 10))
        val enCurso = CalmPlayback(sessionId = 1, startEpochMs = 60 * minute, endEpochMs = null)
        assertNull(CalmOutcome.of(listOf(enCurso), events, 300 * minute).single().snoringAfterMs)
        val alFinal = CalmPlayback(sessionId = 1, startEpochMs = 60 * minute, endEpochMs = 70 * minute)
        assertNull(CalmOutcome.of(listOf(alFinal), events, sessionEndMs = 75 * minute).single().snoringAfterMs)
        assertNull(CalmOutcome.of(listOf(alFinal), events, sessionEndMs = null).single().snoringAfterMs)
    }
}
