package com.sesolibre.somnia

import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.export.CsvReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CsvReportTest {

    private val utc = ZoneId.of("UTC")

    private fun event(
        id: Long,
        startMs: Long,
        durationMs: Long,
        category: String,
        dbPeak: Double,
        confidence: Double? = null,
        transcript: String? = null,
    ) = SoundEvent(
        id = id,
        sessionId = 1,
        startEpochMs = startMs,
        endEpochMs = startMs + durationMs,
        durationMs = durationMs,
        dbPeak = dbPeak,
        dbAvg = dbPeak - 5,
        category = category,
        confidence = confidence,
        transcript = transcript,
    )

    @Test
    fun `encabezado y una fila por evento, orden cronologico`() {
        val session = Session(id = 1, startEpochMs = 0, endEpochMs = 3_600_000, calibrationDbOffset = 100.0)
        val events = listOf(
            event(2, 20_000, 3_000, "cough", dbPeak = -40.0),
            event(1, 5_000, 4_000, "snoring", dbPeak = -30.0, confidence = 0.9),
        )
        val lines = CsvReport.build(session, events, utc).trim().split("\n")
        assertEquals("fecha,hora,categoria,duracion_s,pico_db,confianza,transcripcion", lines[0])
        // ordenado por hora: primero el evento 1 (5 s), luego el 2 (20 s)
        assertEquals("1970-01-01,00:00:05,ronquido,4.0,70,0.90,", lines[1])
        assertEquals("1970-01-01,00:00:20,tos,3.0,60,,", lines[2])
    }

    @Test
    fun `escapa comas y comillas en la transcripcion`() {
        val session = Session(id = 1, startEpochMs = 0, endEpochMs = 3_600_000, calibrationDbOffset = 100.0)
        val events = listOf(
            event(1, 1_000, 2_000, "speech", dbPeak = -20.0, transcript = "hola, dijo \"algo\""),
        )
        val csv = CsvReport.build(session, events, utc)
        assertTrue(csv.contains("\"hola, dijo \"\"algo\"\"\""))
    }
}
