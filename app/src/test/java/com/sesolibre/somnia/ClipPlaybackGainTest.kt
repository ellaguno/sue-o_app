package com.sesolibre.somnia

import com.sesolibre.somnia.audio.ClipPlaybackGain
import org.junit.Assert.assertEquals
import org.junit.Test

class ClipPlaybackGainTest {

    @Test
    fun `clip muy bajo se amplifica hacia el pico objetivo`() {
        // -45 dBFS -> objetivo -9 => 36 dB
        assertEquals(36.0, ClipPlaybackGain.gainDb(-45.0), 0.001)
        assertEquals(3600, ClipPlaybackGain.gainMillibels(-45.0))
    }

    @Test
    fun `clip ya fuerte no se amplifica`() {
        // -6 dBFS ya supera el objetivo -9 => 0 dB
        assertEquals(0.0, ClipPlaybackGain.gainDb(-6.0), 0.001)
        assertEquals(0, ClipPlaybackGain.gainMillibels(-6.0))
    }

    @Test
    fun `la ganancia se limita al tope para no amplificar silencio`() {
        // -80 dBFS pediría 71 dB, se corta en 45
        assertEquals(ClipPlaybackGain.MAX_GAIN_DB, ClipPlaybackGain.gainDb(-80.0), 0.001)
        assertEquals(4500, ClipPlaybackGain.gainMillibels(-80.0))
    }
}
