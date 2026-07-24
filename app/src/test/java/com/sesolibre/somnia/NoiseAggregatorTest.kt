package com.sesolibre.somnia

import com.sesolibre.somnia.audio.NoiseAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NoiseAggregatorTest {

    @Test
    fun `emite estadisticas al completar el minuto`() {
        val agg = NoiseAggregator(readingsPerMinute = 3)
        assertNull(agg.add(-50.0))
        assertNull(agg.add(-40.0))
        val stats = agg.add(-30.0)
        assertNotNull(stats)
        assertEquals(0, stats!!.minuteIndex)
        assertEquals(-50.0, stats.dbMin, 0.001)
        assertEquals(-40.0, stats.dbAvg, 0.001)
        assertEquals(-30.0, stats.dbMax, 0.001)
        assertEquals(3, stats.readingCount)
    }

    @Test
    fun `flush cierra un minuto incompleto`() {
        val agg = NoiseAggregator(readingsPerMinute = 60)
        agg.add(-45.0)
        agg.add(-35.0)
        val stats = agg.flush()
        assertNotNull(stats)
        assertEquals(2, stats!!.readingCount)
        assertEquals(-40.0, stats.dbAvg, 0.001)
    }

    @Test
    fun `flush sin lecturas devuelve null`() {
        assertNull(NoiseAggregator().flush())
    }

    @Test
    fun `resumeAt salta al minuto real tras un hueco de captura`() {
        val agg = NoiseAggregator(readingsPerMinute = 1)
        agg.add(-50.0) // minuto 0
        // El micrófono se cayó y vuelve 10 minutos después.
        assertNull(agg.resumeAt(10))
        assertEquals(10, agg.add(-40.0)!!.minuteIndex)
    }

    @Test
    fun `resumeAt devuelve el minuto parcial y nunca retrocede`() {
        val agg = NoiseAggregator(readingsPerMinute = 60)
        agg.add(-45.0)
        val pending = agg.resumeAt(0) // el hueco fue corto: sigue en el mismo minuto
        assertNotNull(pending)
        assertEquals(0, pending!!.minuteIndex)
        // flush ya avanzó a 1; resumeAt(0) no puede devolverlo a 0.
        agg.add(-30.0)
        assertEquals(1, agg.flush()!!.minuteIndex)
    }

    @Test
    fun `el indice de minuto avanza`() {
        val agg = NoiseAggregator(readingsPerMinute = 1)
        assertEquals(0, agg.add(-50.0)!!.minuteIndex)
        assertEquals(1, agg.add(-50.0)!!.minuteIndex)
        assertEquals(2, agg.add(-50.0)!!.minuteIndex)
    }
}
