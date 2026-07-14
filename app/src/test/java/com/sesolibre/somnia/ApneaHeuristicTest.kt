package com.sesolibre.somnia

import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import org.junit.Assert.assertEquals
import org.junit.Test

class ApneaHeuristicTest {

    private fun event(
        startMs: Long,
        durationMs: Long,
        category: String,
        dbPeak: Double = -30.0,
        manualLabel: String? = null,
    ) = SoundEvent(
        id = startMs, // única para el test
        sessionId = 1,
        startEpochMs = startMs,
        endEpochMs = startMs + durationMs,
        durationMs = durationMs,
        dbPeak = dbPeak,
        dbAvg = dbPeak - 5,
        category = category,
        manualLabel = manualLabel,
    )

    @Test
    fun `ronquido - pausa larga - reanudacion brusca cuenta como patron`() {
        val events = listOf(
            event(0, 5_000, "snoring", dbPeak = -30.0),
            // pausa de 15 s tras el fin (5000) -> reanuda a 20000 con gasp fuerte
            event(20_000, 2_000, "breathing", dbPeak = -28.0),
        )
        assertEquals(1, ApneaHeuristic.detect(events).size)
    }

    @Test
    fun `pausa corta no cuenta`() {
        val events = listOf(
            event(0, 5_000, "snoring"),
            event(9_000, 2_000, "breathing"), // solo 4 s de pausa
        )
        assertEquals(0, ApneaHeuristic.detect(events).size)
    }

    @Test
    fun `pausa demasiado larga no cuenta`() {
        val events = listOf(
            event(0, 5_000, "snoring"),
            event(200_000, 2_000, "snoring"), // 195 s: probablemente despertó
        )
        assertEquals(0, ApneaHeuristic.detect(events).size)
    }

    @Test
    fun `reanudacion debil no cuenta`() {
        val events = listOf(
            event(0, 5_000, "snoring", dbPeak = -25.0),
            event(20_000, 2_000, "breathing", dbPeak = -40.0), // mucho más débil
        )
        assertEquals(0, ApneaHeuristic.detect(events).size)
    }

    @Test
    fun `el que precede debe ser ronquido`() {
        val events = listOf(
            event(0, 5_000, "speech"),
            event(20_000, 2_000, "breathing"),
        )
        assertEquals(0, ApneaHeuristic.detect(events).size)
    }

    @Test
    fun `la etiqueta manual tiene prioridad`() {
        val events = listOf(
            // el clasificador dijo "other" pero el usuario corrigió a ronquido
            event(0, 5_000, "other", manualLabel = "snoring"),
            event(20_000, 2_000, "breathing"),
        )
        assertEquals(1, ApneaHeuristic.detect(events).size)
    }

    @Test
    fun `varios patrones en la noche se cuentan todos`() {
        val events = listOf(
            event(0, 5_000, "snoring"),
            event(20_000, 3_000, "snoring"),   // patrón 1 (pausa 15 s)
            event(40_000, 3_000, "snoring"),   // patrón 2 (pausa 17 s)
            event(45_000, 2_000, "speech"),    // no rompe nada
        )
        assertEquals(2, ApneaHeuristic.detect(events).size)
    }
}
