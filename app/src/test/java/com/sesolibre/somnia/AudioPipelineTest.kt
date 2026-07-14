package com.sesolibre.somnia

import com.sesolibre.somnia.audio.AudioPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * Prueba de integración del pipeline con PCM sintético:
 * silencio -> ruido fuerte (senoidal) -> silencio, en un solo flujo como
 * lo entregaría el micrófono.
 */
class AudioPipelineTest {

    private val sampleRate = 16_000

    private fun quiet(seconds: Double): ShortArray =
        ShortArray((sampleRate * seconds).toInt()) { i ->
            // ruido de piso muy bajo (~-66 dBFS) para que el fondo sea realista
            (20 * sin(2 * PI * 100.0 * i / sampleRate)).toInt().toShort()
        }

    private fun loud(seconds: Double): ShortArray =
        ShortArray((sampleRate * seconds).toInt()) { i ->
            // ~-15 dBFS, muy por encima del fondo
            (8000 * sin(2 * PI * 300.0 * i / sampleRate)).toInt().toShort()
        }

    @Test
    fun `detecta un evento y captura su pcm con preroll`() {
        val seconds = mutableListOf<Double>()
        val events = mutableListOf<AudioPipeline.EventCapture>()
        val pipeline = AudioPipeline(
            sampleRate = sampleRate,
            onSecondReading = { seconds.add(it) },
            onEventCaptured = { events.add(it) },
        )

        pipeline.feed(quiet(10.0)) // establecer fondo
        pipeline.feed(loud(3.0))   // evento
        pipeline.feed(quiet(6.0))  // silencio: cierra (hold 3 s) y da margen

        assertEquals(1, events.size)
        val e = events[0]

        // El PCM incluye ~1 s de pre-roll + 3 s de evento + ~3 s de hold
        assertTrue("pcm muy corto: ${e.pcm.size}", e.pcm.size >= sampleRate * 4)
        assertEquals(1000L, e.prerollMs)

        // El evento empezó ~10 s después del arranque (tolerancia de 200 ms)
        assertEquals(10_000.0, e.startOffsetMs.toDouble(), 200.0)
        assertTrue("duración esperada >= 3 s", e.endOffsetMs - e.startOffsetMs >= 3000)

        // Nivel pico: senoidal de amplitud 8000 -> RMS 5657 -> ~-15.3 dBFS
        assertEquals(-15.3, e.peakDbfs, 1.0)

        // Lecturas por segundo: ~19 s de audio alimentado
        assertEquals(19, seconds.size)
    }

    @Test
    fun `silencio continuo no genera eventos ni clips`() {
        val events = mutableListOf<AudioPipeline.EventCapture>()
        val pipeline = AudioPipeline(
            sampleRate = sampleRate,
            onSecondReading = { },
            onEventCaptured = { events.add(it) },
        )
        pipeline.feed(quiet(30.0))
        assertEquals(0, events.size)
    }

    @Test
    fun `feed acepta trozos de cualquier tamano`() {
        val seconds = mutableListOf<Double>()
        val pipeline = AudioPipeline(
            sampleRate = sampleRate,
            onSecondReading = { seconds.add(it) },
            onEventCaptured = { },
        )
        val audio = quiet(2.0)
        // alimentar en trozos irregulares de 700 muestras
        var offset = 0
        while (offset < audio.size) {
            val len = minOf(700, audio.size - offset)
            pipeline.feed(audio.copyOfRange(offset, offset + len), len)
            offset += len
        }
        assertEquals(2, seconds.size)
    }
}
