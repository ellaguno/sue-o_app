package com.sesolibre.somnia

import com.sesolibre.somnia.data.db.NightLog
import com.sesolibre.somnia.data.db.NightTag
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.stats.TrendsAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Test

class NightlyTrendsTest {

    private val hour = 60 * 60_000L

    private fun session(id: Long, startMs: Long, durationMin: Long = 60) =
        Session(id = id, startEpochMs = startMs, endEpochMs = startMs + durationMin * 60_000L)

    private fun snore(sessionId: Long, startMs: Long, durationMs: Long) = SoundEvent(
        id = sessionId * 1000 + startMs,
        sessionId = sessionId,
        startEpochMs = startMs,
        endEpochMs = startMs + durationMs,
        durationMs = durationMs,
        dbPeak = -30.0,
        dbAvg = -35.0,
        category = "snoring",
    )

    private fun log(sessionId: Long, vararg tags: NightTag) = NightLog(
        sessionId = sessionId,
        updatedEpochMs = 0,
        tagsCsv = NightLog.csvOf(tags.toList()),
    )

    @Test
    fun `excluye noches demasiado cortas y ordena cronologicamente`() {
        val sessions = listOf(
            session(id = 2, startMs = 5 * hour, durationMin = 60),
            session(id = 1, startMs = 1 * hour, durationMin = 60),
            session(id = 3, startMs = 9 * hour, durationMin = 1), // prueba de 1 min
        )
        val metrics = TrendsAnalyzer.metrics(sessions, emptyList(), emptyList(), emptyList())
        assertEquals(listOf(1L, 2L), metrics.map { it.sessionId }) // la de 1 min fuera, ordenadas asc
    }

    @Test
    fun `promedios del resumen`() {
        val sessions = listOf(session(1, 1 * hour), session(2, 5 * hour))
        val events = listOf(
            snore(1, 1 * hour, 60_000),      // 1 min noche 1
            snore(2, 5 * hour, 180_000),     // 3 min noche 2
        )
        val metrics = TrendsAnalyzer.metrics(sessions, events, emptyList(), emptyList())
        val overview = TrendsAnalyzer.overview(metrics)
        assertEquals(2, overview.nights)
        assertEquals(2.0, overview.avgSnoringMinutes, 0.001) // (1 + 3) / 2
    }

    @Test
    fun `correlacion con bitacora compara con y sin la etiqueta`() {
        val sessions = listOf(session(1, 1 * hour), session(2, 5 * hour), session(3, 9 * hour))
        val events = listOf(
            snore(1, 1 * hour, 240_000),  // 4 min, noche con alcohol
            snore(2, 5 * hour, 60_000),   // 1 min, sin alcohol
            snore(3, 9 * hour, 120_000),  // 2 min, sin alcohol
        )
        val logs = listOf(log(1, NightTag.ALCOHOL))
        val effects = TrendsAnalyzer.correlations(
            TrendsAnalyzer.metrics(sessions, events, emptyList(), logs)
        )
        val alcohol = effects.first { it.tag == NightTag.ALCOHOL }
        assertEquals(1, alcohol.nightsWith)
        assertEquals(2, alcohol.nightsWithout)
        assertEquals(4.0, alcohol.avgSnoringWith, 0.001)
        assertEquals(1.5, alcohol.avgSnoringWithout, 0.001) // (1 + 2) / 2
        assertEquals(2.5, alcohol.deltaMinutes, 0.001)
    }

    @Test
    fun `sin noches a ambos lados no hay correlacion`() {
        val sessions = listOf(session(1, 1 * hour))
        val logs = listOf(log(1, NightTag.ALCOHOL)) // única noche, todas con la etiqueta
        val effects = TrendsAnalyzer.correlations(
            TrendsAnalyzer.metrics(sessions, emptyList(), emptyList(), logs)
        )
        assertEquals(emptyList<Any>(), effects)
    }
}
