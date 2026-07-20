package com.sesolibre.somnia

import com.sesolibre.somnia.data.db.NightTag
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.stats.HighlightReason
import com.sesolibre.somnia.stats.Highlights
import com.sesolibre.somnia.stats.NightAnalyzer
import com.sesolibre.somnia.stats.SleepTip
import com.sesolibre.somnia.stats.SleepTips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NightHighlightsTest {

    private fun event(
        id: Long,
        startMs: Long,
        durationMs: Long,
        category: String,
        dbPeak: Double,
        clip: Boolean = true,
    ) = SoundEvent(
        id = id,
        sessionId = 1,
        startEpochMs = startMs,
        endEpochMs = startMs + durationMs,
        durationMs = durationMs,
        dbPeak = dbPeak,
        dbAvg = dbPeak - 5,
        category = category,
        clipPath = if (clip) "/clips/$id.ogg" else null,
    )

    @Test
    fun `el ronquido antes de una pausa es el primer destacado`() {
        val events = listOf(
            event(1, 0, 5_000, "snoring", dbPeak = -20.0),          // fuerte
            event(2, 3_000, 5_000, "snoring", dbPeak = -30.0),      // fin 8000 -> pausa
            event(3, 25_000, 2_000, "breathing", dbPeak = -28.0),   // reanuda tras 17 s
        )
        val patterns = ApneaHeuristic.detect(events)
        assertTrue(patterns.isNotEmpty())
        val top = Highlights.topClips(events, patterns, max = 3)
        assertEquals(HighlightReason.PAUSE_PATTERN, top.first().reason)
        assertEquals(2L, top.first().event.id) // el ronquido que termina en 8000
    }

    @Test
    fun `solo se eligen eventos con clip y sin duplicar`() {
        val events = listOf(
            event(1, 0, 3_000, "snoring", dbPeak = -15.0, clip = false), // sin clip: fuera
            event(2, 10_000, 3_000, "snoring", dbPeak = -25.0),
            event(3, 20_000, 3_000, "cough", dbPeak = -22.0),
        )
        val top = Highlights.topClips(events, emptyList(), max = 3)
        assertEquals(listOf(2L, 3L), top.map { it.event.id })
        assertEquals(top.map { it.event.id }.toSet().size, top.size) // sin duplicados
    }

    @Test
    fun `recomendaciones se disparan por datos y bitacora`() {
        val session = Session(id = 1, startEpochMs = 0, endEpochMs = 5 * 60 * 60_000L) // 5 h < 6 h
        val events = List(10) { i ->
            event(i.toLong(), i * 10_000L, 6_000, "snoring", dbPeak = -25.0)
        }
        val summary = NightAnalyzer.summarize(session, events, emptyList())
        val tips = SleepTips.forNight(summary, setOf(NightTag.ALCOHOL))
        assertTrue(SleepTip.REDUCE_SNORING in tips) // 10 episodios de ronquido
        assertTrue(SleepTip.ALCOHOL in tips)        // ronca + alcohol
        assertTrue(SleepTip.SHORT_SLEEP in tips)    // durmió 5 h
    }

    @Test
    fun `noche tranquila sin bitacora no genera recomendaciones`() {
        val session = Session(id = 1, startEpochMs = 0, endEpochMs = 8 * 60 * 60_000L)
        val summary = NightAnalyzer.summarize(session, emptyList(), emptyList())
        assertEquals(emptyList<SleepTip>(), SleepTips.forNight(summary, emptySet()))
    }
}
