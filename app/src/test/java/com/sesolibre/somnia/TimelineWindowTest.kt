package com.sesolibre.somnia

import com.sesolibre.somnia.ui.components.TimelineWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineWindowTest {

    @Test
    fun `acercar deja fijo el punto bajo el dedo`() {
        val w = TimelineWindow.FULL.zoom(factor = 4f, anchorScreen = 0.5f, minWidth = 0.01f)
        assertEquals(0.25f, w.width, 1e-6f)
        assertEquals(0.5f, w.fromScreen(0.5f), 1e-6f) // el centro sigue en el centro
        assertTrue(w.isZoomed)
    }

    @Test
    fun `cerca del borde el tramo no se sale de la noche`() {
        val w = TimelineWindow.FULL.zoom(factor = 4f, anchorScreen = 0.02f, minWidth = 0.01f)
        assertEquals(0.02f, w.fromScreen(0.02f), 1e-6f) // el punto bajo el dedo no se mueve
        val start = w.pan(10f) // arrastrar mucho a la derecha: tope al inicio
        assertEquals(0f, start.start, 1e-6f)
        assertEquals(0.25f, start.width, 1e-6f)
        val panned = w.pan(-10f) // arrastrar mucho a la izquierda: tope al final
        assertEquals(1f, panned.end, 1e-6f)
        assertEquals(0.25f, panned.width, 1e-6f)
    }

    @Test
    fun `no acerca mas alla del minimo ni aleja mas alla de la noche`() {
        val tight = TimelineWindow.FULL.zoom(1000f, 0.5f, minWidth = 0.05f)
        assertEquals(0.05f, tight.width, 1e-6f)
        val back = tight.zoom(0.0001f, 0.5f, minWidth = 0.05f)
        assertEquals(TimelineWindow.FULL, back)
        assertFalse(back.isZoomed)
    }

    @Test
    fun `convierte entre pantalla y noche`() {
        val w = TimelineWindow(0.2f, 0.6f)
        assertEquals(0.5f, w.toScreen(0.4f), 1e-6f)
        assertEquals(0.4f, w.fromScreen(0.5f), 1e-6f)
    }

    @Test
    fun `marcas del eje segun lo que se ve`() {
        val min = 60_000L
        assertEquals(60, TimelineWindow.tickStepMinutes(7 * 60 * min))   // noche completa: cada hora
        assertEquals(30, TimelineWindow.tickStepMinutes(2 * 60 * min))
        assertEquals(15, TimelineWindow.tickStepMinutes(60 * min))
        assertEquals(5, TimelineWindow.tickStepMinutes(20 * min))
        assertEquals(1, TimelineWindow.tickStepMinutes(10 * min))
    }
}
