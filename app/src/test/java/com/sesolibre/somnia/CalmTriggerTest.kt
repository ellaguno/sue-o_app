package com.sesolibre.somnia

import com.sesolibre.somnia.calm.CalmTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalmTriggerTest {
    private val minute = 60_000L

    @Test
    fun `un ronquido suelto no dispara`() {
        val t = CalmTrigger()
        assertFalse(t.onSnore(0))
        assertFalse(t.onSnore(20 * minute))
    }

    @Test
    fun `seis ronquidos en cinco minutos disparan una vez`() {
        val t = CalmTrigger()
        val fired = (0 until 8).map { t.onSnore(it * 30_000L) }
        assertEquals(listOf(false, false, false, false, false, true, false, false), fired)
    }

    @Test
    fun `ronquidos espaciados no se acumulan`() {
        val t = CalmTrigger()
        assertTrue((0 until 20).none { t.onSnore(it * 2 * minute) })
    }

    @Test
    fun `respeta la pausa entre disparos y el tope por noche`() {
        val t = CalmTrigger()
        var fired = 0
        // Ronquido continuo cada 20 s durante 8 horas.
        var now = 0L
        val times = mutableListOf<Long>()
        while (now < 8 * 60 * minute) {
            if (t.onSnore(now)) {
                fired++
                times += now
            }
            now += 20_000L
        }
        assertEquals(4, fired)
        assertEquals(4, t.firedCount)
        times.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= 45 * minute) }
    }
}
