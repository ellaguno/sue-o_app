package com.sesolibre.somnia

import com.sesolibre.somnia.export.ClipExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClipExporterTest {

    @Test
    fun `amplifica un clip bajo sin pasar del tope`() {
        val quiet = ShortArray(1600) { if (it % 2 == 0) 180 else -180 } // ~-45 dBFS
        val out = ClipExporter.amplify(quiet, gainDb = 36.0)
        val peak = out.maxOf { abs(it.toInt()) }
        assertTrue("pico $peak", peak in 10_000..29_000)
    }

    @Test
    fun `el tope evita saturar aunque la ganancia pedida sea enorme`() {
        val pcm = ShortArray(100) { 5_000 }
        val out = ClipExporter.amplify(pcm, gainDb = 45.0)
        assertTrue(out.all { it.toInt() <= 29_000 })
    }

    @Test
    fun `sin ganancia devuelve el mismo arreglo`() {
        val pcm = ShortArray(10) { 100 }
        assertSame(pcm, ClipExporter.amplify(pcm, gainDb = 0.0))
    }

    @Test
    fun `nombres de archivo sin acentos ni espacios`() {
        assertEquals("respiracion", ClipExporter.slug("Respiración"))
        assertEquals("sin-clasificar", ClipExporter.slug("Sin clasificar"))
    }
}
