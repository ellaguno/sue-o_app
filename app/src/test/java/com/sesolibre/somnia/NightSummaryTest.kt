package com.sesolibre.somnia

import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.stats.NightAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NightSummaryTest {

    private fun session(
        startMs: Long = 0,
        endMs: Long? = 8 * 60 * 60_000L, // 8 h
        calibration: Double = 100.0,
    ) = Session(
        id = 1,
        startEpochMs = startMs,
        endEpochMs = endMs,
        calibrationDbOffset = calibration,
    )

    private fun event(
        startMs: Long,
        durationMs: Long,
        category: String,
        dbPeak: Double = -30.0,
        manualLabel: String? = null,
    ) = SoundEvent(
        id = startMs,
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
    fun `cuenta eventos por categoria efectiva`() {
        val events = listOf(
            event(0, 3_000, "snoring"),
            event(10_000, 3_000, "snoring"),
            event(20_000, 2_000, "cough"),
            event(30_000, 4_000, "environment"),
            event(40_000, 2_000, "unknown"),
        )
        val s = NightAnalyzer.summarize(session(), events, emptyList())
        assertEquals(5, s.totalEvents)
        assertEquals(2, s.count(SomniaCategory.SNORING))
        assertEquals(1, s.count(SomniaCategory.COUGH))
        assertEquals(1, s.environmentCount)
        assertEquals(1, s.unknownCount)
    }

    @Test
    fun `etiqueta manual tiene prioridad sobre el clasificador`() {
        val events = listOf(
            event(0, 3_000, category = "other", manualLabel = "snoring"),
        )
        val s = NightAnalyzer.summarize(session(), events, emptyList())
        assertEquals(1, s.count(SomniaCategory.SNORING))
        assertEquals(0, s.count(SomniaCategory.OTHER))
    }

    @Test
    fun `minutos de ronquido suman la duracion de los ronquidos`() {
        val events = listOf(
            event(0, 30_000, "snoring"),
            event(60_000, 90_000, "snoring"),
            event(200_000, 5_000, "cough"),
        )
        val s = NightAnalyzer.summarize(session(), events, emptyList())
        assertEquals(2, s.snoringEpisodes)
        assertEquals(2.0, s.snoringMinutes, 0.001) // 120 s
    }

    @Test
    fun `ruido ambiente y no clasificado quedan fuera de los sonidos del cuerpo`() {
        val events = listOf(
            event(0, 3_000, "snoring"),
            event(10_000, 3_000, "breathing"),
            event(20_000, 3_000, "environment"),
            event(30_000, 3_000, "other"),
            event(40_000, 3_000, "unknown"),
        )
        val s = NightAnalyzer.summarize(session(), events, emptyList())
        assertEquals(2, s.bodyEventCount) // snoring + breathing
        assertEquals(1, s.environmentCount)
        assertEquals(2, s.otherCount) // other + unknown
    }

    @Test
    fun `pico usa dbMax de la muestra mas ruidosa mas la calibracion`() {
        val samples = listOf(
            NoiseSample(sessionId = 1, minuteIndex = 0, dbMin = -60.0, dbAvg = -50.0, dbMax = -40.0),
            NoiseSample(sessionId = 1, minuteIndex = 1, dbMin = -58.0, dbAvg = -48.0, dbMax = -22.0),
        )
        val s = NightAnalyzer.summarize(session(calibration = 100.0), emptyList(), samples)
        assertEquals(78, s.peakDb) // -22 + 100
    }

    @Test
    fun `sin muestras el pico es nulo y sesion en curso no tiene duracion`() {
        val s = NightAnalyzer.summarize(session(endMs = null), emptyList(), emptyList())
        assertNull(s.peakDb)
        assertNull(s.durationMinutes)
        assertEquals(0, s.totalEvents)
    }
}
