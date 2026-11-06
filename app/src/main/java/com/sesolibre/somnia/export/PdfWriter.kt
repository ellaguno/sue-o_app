package com.sesolibre.somnia.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Escritor de PDF A4 con paginación automática: lleva el cursor vertical,
 * abre página nueva cuando algo no cabe y pone el pie en cada página.
 * Compartido por el reporte de la noche y el de tendencias.
 */
internal class PdfWriter(private val footer: String) {

    val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var pageNumber = 0

    /** Cursor vertical (línea base del siguiente texto). */
    var y = 0f

    val canvas: Canvas get() = page!!.canvas
    val contentWidth: Float get() = PAGE_W - 2 * MARGIN
    val left: Float get() = MARGIN

    val title = paint(20f, bold = true, color = INK)
    val heading = paint(13f, bold = true, color = ACCENT)
    val body = paint(10.5f, color = INK)
    val bold = paint(10.5f, bold = true, color = INK)
    val small = paint(8.5f, color = MUTED)
    val smallBold = paint(8.5f, bold = true, color = INK)

    init {
        newPage()
    }

    fun newPage() {
        page?.let { finishPage(it) }
        pageNumber++
        page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        y = MARGIN + 10f
    }

    /** Abre página nueva si no caben [height] puntos más. */
    fun ensureSpace(height: Float) {
        if (y + height > PAGE_H - MARGIN - FOOTER_H) newPage()
    }

    fun text(text: String, paint: Paint = body, x: Float = MARGIN, lineGap: Float = 4f) {
        ensureSpace(paint.textSize + lineGap)
        canvas.drawText(text, x, y, paint)
        y += paint.textSize + lineGap
    }

    /** Texto ajustado al ancho, palabra por palabra. */
    fun wrapped(text: String, paint: Paint = body, x: Float = MARGIN, width: Float = contentWidth) {
        var line = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > width && line.isNotEmpty()) {
                text(line.toString(), paint, x)
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) text(line.toString(), paint, x)
    }

    /** Título de sección con una línea fina debajo. */
    fun section(title: String) {
        ensureSpace(heading.textSize + 40f) // que el título no quede huérfano al pie
        y += 8f
        canvas.drawText(title, MARGIN, y, heading)
        y += 5f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, rule)
        y += heading.textSize + 2f
    }

    /**
     * Fila de "tarjetas" con un dato grande y su etiqueta (Duración, Eventos…).
     * Se reparten parejas en el ancho de la página.
     */
    fun tiles(items: List<Pair<String, String>>) {
        if (items.isEmpty()) return
        val h = 44f
        ensureSpace(h + 8f)
        val gap = 8f
        val w = (contentWidth - gap * (items.size - 1)) / items.size
        val top = y - 10f
        items.forEachIndexed { i, (value, label) ->
            val x = MARGIN + i * (w + gap)
            canvas.drawRoundRect(RectF(x, top, x + w, top + h), 6f, 6f, tileFill)
            canvas.drawText(value, x + 8f, top + 21f, tileValue)
            canvas.drawText(ellipsize(label, small, w - 12f), x + 8f, top + 36f, small)
        }
        y = top + h + 18f
    }

    /** Reserva un bloque de [height] para una gráfica y devuelve su rectángulo. */
    fun block(height: Float): RectF {
        ensureSpace(height + 6f)
        val rect = RectF(MARGIN, y - 6f, PAGE_W - MARGIN, y - 6f + height)
        y += height + 6f
        return rect
    }

    /** Fila de tabla: columnas en posiciones x relativas al margen. */
    fun row(cells: List<String>, columns: FloatArray, paint: Paint = body, shaded: Boolean = false) {
        val rowH = paint.textSize + 5f
        ensureSpace(rowH)
        if (shaded) {
            canvas.drawRect(MARGIN - 2f, y - paint.textSize - 1f, PAGE_W - MARGIN + 2f, y + 4f, tileFill)
        }
        cells.forEachIndexed { i, cell ->
            val x = MARGIN + columns[i]
            val maxW = (if (i + 1 < columns.size) columns[i + 1] else contentWidth) - columns[i] - 4f
            canvas.drawText(ellipsize(cell, paint, maxW), x, y, paint)
        }
        y += rowH
    }

    fun space(points: Float) { y += points }

    fun writeTo(outFile: File): File {
        page?.let { finishPage(it) }
        page = null
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    private fun finishPage(p: PdfDocument.Page) {
        val c = p.canvas
        val fy = PAGE_H - MARGIN + 12f
        c.drawLine(MARGIN, fy - 12f, PAGE_W - MARGIN, fy - 12f, rule)
        c.drawText(footer, MARGIN, fy, small)
        val pageText = "$pageNumber"
        c.drawText(pageText, PAGE_W - MARGIN - small.measureText(pageText), fy, small)
        doc.finishPage(p)
    }

    companion object {
        const val PAGE_W = 595 // A4 a 72 dpi
        const val PAGE_H = 842
        const val MARGIN = 40f
        private const val FOOTER_H = 14f

        const val INK = 0xFF1F2233.toInt()
        const val MUTED = 0xFF6B7080.toInt()
        const val ACCENT = 0xFF3B4C9B.toInt()
        const val GRID = 0xFFE3E5EE.toInt()

        fun paint(size: Float, bold: Boolean = false, color: Int = INK) = Paint().apply {
            textSize = size
            isFakeBoldText = bold
            isAntiAlias = true
            this.color = color
        }

        private val rule = Paint().apply { color = GRID; strokeWidth = 1f }
        private val tileFill = Paint().apply { color = 0xFFF1F2F8.toInt(); style = Paint.Style.FILL }
        private val tileValue = paint(15f, bold = true, color = ACCENT)

        fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
            if (maxWidth <= 0f || paint.measureText(text) <= maxWidth) return text
            var end = text.length
            while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
            return text.substring(0, end) + "…"
        }
    }
}
