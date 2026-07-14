package com.sesolibre.somnia

import com.sesolibre.somnia.ml.AudioWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioWindowsTest {

    @Test
    fun `audio corto produce una ventana con padding`() {
        val windows = AudioWindows.windows(ShortArray(1000) { 100 })
        assertEquals(1, windows.size)
        assertEquals(AudioWindows.YAMNET_WINDOW, windows[0].size)
        // las primeras 1000 muestras normalizadas, el resto en cero
        assertEquals(100 / 32768f, windows[0][0], 1e-6f)
        assertEquals(0f, windows[0][1000], 1e-6f)
    }

    @Test
    fun `audio largo produce ventanas con traslape del 50 por ciento`() {
        // 3 s de audio a 16 kHz = 48000 muestras
        val windows = AudioWindows.windows(ShortArray(48_000))
        // inicios: 0, 7800, 15600, 23400, 31200, 39000 -> 6 ventanas (46800 < 48000 aún abre otra... )
        assertTrue("ventanas=${windows.size}", windows.size in 5..7)
        windows.forEach { assertEquals(AudioWindows.YAMNET_WINDOW, it.size) }
    }

    @Test
    fun `respeta el tope de ventanas`() {
        val windows = AudioWindows.windows(ShortArray(16_000 * 60), maxWindows = 16)
        assertEquals(16, windows.size)
    }

    @Test
    fun `normalizacion en rango -1 a 1`() {
        val pcm = shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE, 0)
        val w = AudioWindows.windows(pcm)[0]
        assertEquals(32767 / 32768f, w[0], 1e-6f)
        assertEquals(-1f, w[1], 1e-6f)
        assertEquals(0f, w[2], 1e-6f)
    }

    @Test
    fun `pcm vacio no produce ventanas`() {
        assertTrue(AudioWindows.windows(ShortArray(0)).isEmpty())
    }
}
