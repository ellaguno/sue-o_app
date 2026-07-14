package com.sesolibre.somnia

import com.sesolibre.somnia.audio.EventDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventDetectorTest {

    private fun detector(
        holdFrames: Int = 5,
        minEventFrames: Int = 3,
        maxEventFrames: Int = 100,
    ) = EventDetector(
        EventDetector.Config(
            openMarginDb = 12.0,
            closeMarginDb = 6.0,
            holdFrames = holdFrames,
            minEventFrames = minEventFrames,
            maxEventFrames = maxEventFrames,
            floorInitDbfs = -60.0,
        )
    )

    @Test
    fun `silencio continuo no genera eventos`() {
        val d = detector()
        repeat(1000) { assertNull(d.process(-60.0)) }
    }

    @Test
    fun `ruido fuerte abre y cierra un evento con histeresis`() {
        val d = detector()
        // fondo estable
        repeat(50) { d.process(-60.0) }
        // ronquido: 20 frames a -30 dB (30 dB sobre el fondo)
        assertEquals(EventDetector.Signal.Start, d.process(-30.0))
        repeat(19) { assertNull(d.process(-30.0)) }
        // silencio: el evento cierra tras holdFrames
        var end: EventDetector.Signal.End? = null
        for (i in 0 until 10) {
            val s = d.process(-60.0)
            if (s is EventDetector.Signal.End) {
                end = s
                break
            }
        }
        assertEquals(-30.0, end!!.stats.peakDbfs, 0.001)
        assertTrue(end.stats.frameCount >= 20)
    }

    @Test
    fun `un pico de un frame se descarta por corto`() {
        val d = detector(minEventFrames = 3)
        repeat(50) { d.process(-60.0) }
        assertEquals(EventDetector.Signal.Start, d.process(-30.0)) // 1 solo frame fuerte
        var discarded = false
        for (i in 0 until 10) {
            if (d.process(-60.0) == EventDetector.Signal.Discard) {
                discarded = true
                break
            }
        }
        assertTrue(discarded)
    }

    @Test
    fun `evento largo se cierra al tope maximo`() {
        val d = detector(maxEventFrames = 50)
        repeat(50) { d.process(-60.0) }
        assertEquals(EventDetector.Signal.Start, d.process(-30.0))
        var end: EventDetector.Signal.End? = null
        var frames = 1
        while (end == null && frames < 200) {
            val s = d.process(-30.0)
            frames++
            if (s is EventDetector.Signal.End) end = s
        }
        assertEquals(50, end!!.stats.frameCount)
    }

    @Test
    fun `el fondo se adapta a un ambiente mas ruidoso`() {
        val d = detector()
        // ambiente sube lentamente a -45 dB (ventilador): el fondo lo sigue
        repeat(500) { d.process(-45.0) }
        assertTrue(d.noiseFloorDbfs > -47.0)
        // ahora -40 dB ya no dispara (está a solo 5 dB del fondo)
        assertNull(d.process(-40.0))
        // pero -30 dB sí (15 dB sobre el fondo)
        assertEquals(EventDetector.Signal.Start, d.process(-30.0))
    }

    @Test
    fun `durante un evento el fondo se adapta mucho mas lento`() {
        val d = detector(holdFrames = 5, maxEventFrames = 1000)
        repeat(100) { d.process(-60.0) }
        d.process(-25.0) // abre
        repeat(50) { d.process(-25.0) } // ruido sostenido, 5 s
        // Con alpha en reposo (0.05) el fondo ya estaría en ~-28 dB;
        // con la adaptación lenta (0.005) apenas se movió unos ~8 dB.
        assertTrue("fondo=${d.noiseFloorDbfs}", d.noiseFloorDbfs < -50.0)
        assertTrue("fondo=${d.noiseFloorDbfs}", d.noiseFloorDbfs > -60.0)
    }
}
