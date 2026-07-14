package com.sesolibre.somnia

import com.sesolibre.somnia.audio.DbMeter
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DbMeterTest {

    @Test
    fun `silencio digital da el piso`() {
        assertEquals(DbMeter.FLOOR_DBFS, DbMeter.dbfs(ShortArray(16_000)), 0.001)
    }

    @Test
    fun `arreglo vacio da el piso`() {
        assertEquals(DbMeter.FLOOR_DBFS, DbMeter.dbfs(ShortArray(0)), 0.001)
    }

    @Test
    fun `senoidal a escala completa da -3 dBFS`() {
        val samples = sine(amplitude = 32767.0)
        // RMS de una senoidal = amplitud / sqrt(2) -> 20*log10(1/sqrt(2)) = -3.01 dBFS
        assertEquals(-3.01, DbMeter.dbfs(samples), 0.05)
    }

    @Test
    fun `senoidal a media escala da -9 dBFS`() {
        val samples = sine(amplitude = 32767.0 / 2)
        assertEquals(-9.03, DbMeter.dbfs(samples), 0.05)
    }

    @Test
    fun `suma de cuadrados por chunks equivale al calculo directo`() {
        val samples = sine(amplitude = 12_000.0)
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s.toDouble()
        assertEquals(
            DbMeter.dbfs(samples),
            DbMeter.dbfsFromSumOfSquares(sum, samples.size),
            0.0001,
        )
    }

    private fun sine(amplitude: Double, n: Int = 16_000, freq: Double = 440.0): ShortArray =
        ShortArray(n) { i ->
            (amplitude * sin(2 * PI * freq * i / 16_000.0)).toInt().toShort()
        }
}
