package com.sesolibre.somnia

import com.sesolibre.somnia.audio.ShortRingBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortRingBufferTest {

    @Test
    fun `snapshot devuelve lo escrito en orden`() {
        val rb = ShortRingBuffer(10)
        rb.write(shortArrayOf(1, 2, 3))
        assertArrayEquals(shortArrayOf(1, 2, 3), rb.snapshotLast(3))
    }

    @Test
    fun `snapshot de mas de lo disponible devuelve solo lo disponible`() {
        val rb = ShortRingBuffer(10)
        rb.write(shortArrayOf(1, 2))
        assertArrayEquals(shortArrayOf(1, 2), rb.snapshotLast(5))
        assertEquals(2, rb.size)
    }

    @Test
    fun `al dar la vuelta conserva las ultimas muestras`() {
        val rb = ShortRingBuffer(4)
        rb.write(shortArrayOf(1, 2, 3, 4, 5, 6)) // sobreescribe 1 y 2
        assertArrayEquals(shortArrayOf(3, 4, 5, 6), rb.snapshotLast(4))
    }

    @Test
    fun `multiples escrituras con vuelta`() {
        val rb = ShortRingBuffer(5)
        rb.write(shortArrayOf(1, 2, 3))
        rb.write(shortArrayOf(4, 5, 6, 7))
        assertArrayEquals(shortArrayOf(3, 4, 5, 6, 7), rb.snapshotLast(5))
        assertArrayEquals(shortArrayOf(6, 7), rb.snapshotLast(2))
    }
}
