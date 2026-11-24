package com.sesolibre.somnia

import com.sesolibre.somnia.audio.AudioPipeline
import com.sesolibre.somnia.audio.EventDetector
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.sonidos.SnoreSoundTrigger
import com.sesolibre.somnia.stats.TrendsAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SnoreSoundTest {

    private val min = 60_000L

    @Test
    fun `no dispara con el primer ronquido sino con 3 en 5 minutos`() {
        val t = SnoreSoundTrigger()
        assertFalse(t.onSnore(0))
        assertFalse(t.onSnore(1 * min))
        assertTrue(t.onSnore(2 * min))
    }

    @Test
    fun `ronquidos muy espaciados no disparan`() {
        val t = SnoreSoundTrigger()
        assertFalse(t.onSnore(0))
        assertFalse(t.onSnore(4 * min))
        assertFalse(t.onSnore(10 * min)) // los anteriores ya salieron de la ventana de 5 min
        assertFalse(t.onSnore(12 * min)) // solo 2 dentro de la ventana (10 y 12)
        assertTrue(t.onSnore(13 * min))
    }

    @Test
    fun `no vuelve a sonar mientras suena ni antes de 40 minutos despues`() {
        val t = SnoreSoundTrigger()
        t.onSnore(0); t.onSnore(1 * min); assertTrue(t.onSnore(2 * min))
        // Mientras suena, los ronquidos no cuentan.
        assertFalse(t.onSnore(3 * min))
        t.onPlaybackEnded(12 * min)
        // Dentro de la espera de 40 min.
        assertFalse(t.onSnore(20 * min)); assertFalse(t.onSnore(21 * min)); assertFalse(t.onSnore(22 * min))
        // Pasada la espera, hacen falta 3 ronquidos nuevos.
        assertFalse(t.onSnore(53 * min)); assertFalse(t.onSnore(54 * min))
        assertTrue(t.onSnore(55 * min))
    }

    @Test
    fun `detector silenciado mantiene el reloj y no deja eventos abiertos`() {
        val d = EventDetector()
        repeat(50) { d.process(-60.0) }
        assertEquals(EventDetector.Signal.Start, d.process(-20.0))
        assertTrue(d.skip()) // se descarta el evento abierto
        repeat(9) { assertFalse(d.skip()) }
        // Tras el silencio, un evento nuevo arranca en el frame correcto (61).
        assertEquals(EventDetector.Signal.Start, d.process(-20.0))
        repeat(9) { d.process(-20.0) }
        var end: EventDetector.Signal? = null
        repeat(40) { if (end == null) end = d.process(-60.0) }
        assertEquals(61, (end as EventDetector.Signal.End).stats.startFrame)
    }

    @Test
    fun `mientras suena el pipeline no detecta eventos pero sigue midiendo`() {
        val sr = 16_000
        fun quiet(s: Double) = ShortArray((sr * s).toInt()) { (20 * sin(2 * PI * 100.0 * it / sr)).toInt().toShort() }
        fun loud(s: Double) = ShortArray((sr * s).toInt()) { (8000 * sin(2 * PI * 300.0 * it / sr)).toInt().toShort() }
        val seconds = mutableListOf<Double>()
        val events = mutableListOf<AudioPipeline.EventCapture>()
        val p = AudioPipeline(sampleRate = sr, onSecondReading = { seconds += it }, onEventCaptured = { events += it })
        p.feed(quiet(10.0))
        p.eventsMuted = true
        p.feed(loud(3.0)) // el propio sonido de Somnia
        p.feed(quiet(4.0))
        p.eventsMuted = false
        p.feed(quiet(4.0))
        assertTrue(events.isEmpty())
        assertEquals(21, seconds.size)
        // Después de quitar el silencio, los eventos reales se vuelven a detectar.
        p.feed(loud(2.0)); p.feed(quiet(5.0))
        assertEquals(1, events.size)
        assertEquals(21_000.0, events[0].startOffsetMs.toDouble(), 200.0)
    }

    @Test
    fun `compara noches con el sonido activado y sin el`() {
        val h = 60 * min
        fun night(id: Long, on: Boolean) =
            Session(id = id, startEpochMs = id * 24 * h, endEpochMs = id * 24 * h + 6 * h, snoreSoundEnabled = on)
        fun snore(session: Long, minutes: Long) = SoundEvent(
            id = session, sessionId = session, startEpochMs = session * 24 * h + h,
            endEpochMs = session * 24 * h + h + minutes * min, durationMs = minutes * min,
            dbPeak = -30.0, dbAvg = -35.0, category = "snoring",
        )
        val sessions = listOf(night(1, true), night(2, true), night(3, false))
        val events = listOf(snore(1, 4), snore(2, 2), snore(3, 6))
        val metrics = TrendsAnalyzer.metricsWithPeaks(
            sessions, events, emptyMap(), emptyList(), playsBySession = mapOf(1L to 2),
        )
        val e = TrendsAnalyzer.soundExperiment(metrics)!!
        assertEquals(2, e.nightsOn)
        assertEquals(1, e.nightsOff)
        assertEquals(3.0, e.avgSnoringOn, 1e-9)
        assertEquals(6.0, e.avgSnoringOff, 1e-9)
        assertEquals(2, e.totalPlays)
        assertTrue(e.comparable)
        // Si nunca se activó, no hay experimento que mostrar.
        assertNull(TrendsAnalyzer.soundExperiment(metrics.filter { !it.snoreSoundEnabled }))
    }

    @Test
    fun `el pico de ruido ignora los minutos en que sonaba Somnia`() {
        val session = Session(id = 1, startEpochMs = 0, endEpochMs = 10 * min, calibrationDbOffset = 90.0)
        fun minute(i: Int, max: Double) =
            com.sesolibre.somnia.data.db.NoiseSample(sessionId = 1, minuteIndex = i, dbMin = -70.0, dbAvg = -65.0, dbMax = max)
        val samples = listOf(minute(0, -60.0), minute(3, -40.0), minute(4, -45.0), minute(8, -55.0))
        val sinFiltro = com.sesolibre.somnia.stats.NightAnalyzer.summarize(session, emptyList(), samples)
        assertEquals(50, sinFiltro.peakDb) // -40 + 90
        // Sonó de 3:00 a 4:30: los minutos 3 y 4 no cuentan para el pico.
        val conFiltro = com.sesolibre.somnia.stats.NightAnalyzer.summarize(
            session, emptyList(), samples, listOf((3 * min)..(4 * min + 30_000)),
        )
        assertEquals(35, conFiltro.peakDb) // -55 + 90
    }
}
