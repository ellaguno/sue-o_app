package com.sesolibre.somnia.export

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.stats.NightSummary
import com.sesolibre.somnia.ui.components.categoryColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Gráficas de los reportes PDF, dibujadas directo en el [Canvas] de la página
 * (no hay Compose al exportar). Usan los mismos colores por categoría que la app.
 */
internal object PdfCharts {

    private val axisText = PdfWriter.paint(7.5f, color = PdfWriter.MUTED)
    private val valueText = PdfWriter.paint(7.5f, bold = true, color = PdfWriter.INK)
    private val labelText = PdfWriter.paint(8.5f, color = PdfWriter.INK)
    private val grid = Paint().apply { color = PdfWriter.GRID; strokeWidth = 0.7f }
    private val curve = Paint().apply {
        color = PdfWriter.ACCENT; strokeWidth = 1.4f; style = Paint.Style.STROKE
        isAntiAlias = true; strokeJoin = Paint.Join.ROUND
    }
    private val band = Paint().apply { color = 0x333B4C9B; style = Paint.Style.FILL; isAntiAlias = true }
    private val fill = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

    fun argb(category: SomniaCategory?): Int = categoryColor(category).toArgb()

    /**
     * Curva de ruido de la noche (promedio por minuto, con la banda mín–máx) y,
     * encima, una franja con cada evento como un punto de su color. El eje x son
     * horas de reloj; el y, dB aproximados.
     */
    fun noiseTimeline(
        canvas: Canvas,
        rect: RectF,
        samples: List<NoiseSample>,
        events: List<SoundEvent>,
        startEpochMs: Long,
        endEpochMs: Long,
        calibrationOffset: Double,
        zone: ZoneId,
    ) {
        val span = (endEpochMs - startEpochMs).coerceAtLeast(60_000L).toFloat()
        val axisW = 26f
        val laneH = 16f
        val plot = RectF(rect.left + axisW, rect.top + laneH + 4f, rect.right, rect.bottom - 12f)
        fun xAt(epochMs: Long) = plot.left + ((epochMs - startEpochMs) / span).coerceIn(0f, 1f) * plot.width()

        // Eje y: dB redondeados a decenas.
        val values = samples.flatMap { listOf(it.dbMin, it.dbMax) }.map { it + calibrationOffset }
        val lo = values.minOrNull()?.let { floor(it / 10) * 10 } ?: 20.0
        val hi = (values.maxOrNull()?.let { ceil(it / 10) * 10 } ?: 60.0).coerceAtLeast(lo + 10)
        fun yAt(db: Double) = plot.bottom - ((db - lo) / (hi - lo)).toFloat() * plot.height()
        var tick = lo
        while (tick <= hi + 0.1) {
            val y = yAt(tick)
            canvas.drawLine(plot.left, y, plot.right, y, grid)
            canvas.drawText("${tick.roundToInt()}", rect.left, y + 3f, axisText)
            tick += if (hi - lo > 40) 20 else 10
        }
        canvas.drawText("dB", rect.left, plot.top - 2f, axisText)

        // Eje x: una marca por hora en punto.
        val hourFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
        val hourMs = 3_600_000L
        val offsetMs = zone.rules.getOffset(Instant.ofEpochMilli(startEpochMs)).totalSeconds * 1000L
        var h = Math.floorDiv(startEpochMs + offsetMs, hourMs) * hourMs - offsetMs + hourMs
        val every = if (span / hourMs > 10) 2 else 1
        var i = 0
        while (h < endEpochMs) {
            if (i % every == 0) {
                val x = xAt(h)
                canvas.drawLine(x, plot.top, x, plot.bottom, grid)
                val label = hourFmt.format(Instant.ofEpochMilli(h))
                canvas.drawText(label, x - axisText.measureText(label) / 2, rect.bottom, axisText)
            }
            h += hourMs; i++
        }

        // Banda mín–máx y curva del promedio.
        if (samples.size >= 2) {
            val sorted = samples.sortedBy { it.minuteIndex }
            val bandPath = Path()
            sorted.forEachIndexed { idx, s ->
                val x = xAt(startEpochMs + s.minuteIndex * 60_000L)
                val y = yAt(s.dbMax + calibrationOffset)
                if (idx == 0) bandPath.moveTo(x, y) else bandPath.lineTo(x, y)
            }
            sorted.asReversed().forEach { s ->
                bandPath.lineTo(xAt(startEpochMs + s.minuteIndex * 60_000L), yAt(s.dbMin + calibrationOffset))
            }
            bandPath.close()
            canvas.drawPath(bandPath, band)
            val line = Path()
            sorted.forEachIndexed { idx, s ->
                val x = xAt(startEpochMs + s.minuteIndex * 60_000L)
                val y = yAt(s.dbAvg + calibrationOffset)
                if (idx == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            canvas.drawPath(line, curve)
        }

        // Franja de eventos: primero lo tenue (ambiente/otro), encima lo del cuerpo.
        val laneY = rect.top + laneH / 2
        canvas.drawLine(plot.left, laneY, plot.right, laneY, grid)
        val (body, rest) = events.partition {
            SomniaCategory.fromKey(ApneaHeuristic.effectiveCategory(it)) in NightSummary.BODY_CATEGORIES
        }
        rest.forEach { e ->
            fill.color = argb(SomniaCategory.fromKey(ApneaHeuristic.effectiveCategory(e)))
            canvas.drawCircle(xAt(e.startEpochMs), laneY, 1.6f, fill)
        }
        body.forEach { e ->
            fill.color = argb(SomniaCategory.fromKey(ApneaHeuristic.effectiveCategory(e)))
            canvas.drawCircle(xAt(e.startEpochMs), laneY, 2.8f, fill)
        }
    }

    /** Leyenda horizontal de categorías (punto de color + nombre). Devuelve el alto usado. */
    fun legend(canvas: Canvas, x: Float, y: Float, maxX: Float, items: List<Pair<String, Int>>): Float {
        var cx = x
        var cy = y
        items.forEach { (label, color) ->
            val w = 10f + labelText.measureText(label) + 12f
            if (cx + w > maxX) { cx = x; cy += 12f }
            fill.color = color
            canvas.drawCircle(cx + 3f, cy - 3f, 3f, fill)
            canvas.drawText(label, cx + 10f, cy, labelText)
            cx += w
        }
        return cy - y + 12f
    }

    /** Barras horizontales (p. ej. eventos por categoría), con el valor al final. */
    fun horizontalBars(canvas: Canvas, rect: RectF, items: List<Triple<String, Double, Int>>, format: (Double) -> String) {
        if (items.isEmpty()) return
        val labelW = items.maxOf { labelText.measureText(it.first) } + 8f
        val barH = (rect.height() / items.size).coerceAtMost(16f)
        val max = items.maxOf { it.second }.coerceAtLeast(1e-9)
        val barMaxW = rect.width() - labelW - 34f
        items.forEachIndexed { i, (label, value, color) ->
            val top = rect.top + i * barH
            canvas.drawText(label, rect.left, top + barH * 0.7f, labelText)
            val w = (value / max).toFloat() * barMaxW
            fill.color = color
            canvas.drawRoundRect(RectF(rect.left + labelW, top + 2f, rect.left + labelW + w.coerceAtLeast(1.5f), top + barH - 2f), 2f, 2f, fill)
            canvas.drawText(format(value), rect.left + labelW + w + 4f, top + barH * 0.7f, valueText)
        }
    }

    /**
     * Barras verticales con etiqueta bajo cada una (horas de la noche, noches
     * de tendencias). Si hay muchas, solo se rotulan algunas para que quepan.
     */
    fun verticalBars(
        canvas: Canvas,
        rect: RectF,
        bars: List<Pair<String, Double>>,
        color: Int,
        format: (Double) -> String,
    ) {
        if (bars.isEmpty()) return
        val plot = RectF(rect.left, rect.top + 10f, rect.right, rect.bottom - 12f)
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, grid)
        val max = bars.maxOf { it.second }
        val slot = plot.width() / bars.size
        val barW = (slot * 0.7f).coerceAtMost(28f)
        val labelEvery = ceil(bars.size / (plot.width() / 34f)).toInt().coerceAtLeast(1)
        fill.color = color
        bars.forEachIndexed { i, (label, value) ->
            val cx = plot.left + slot * (i + 0.5f)
            val h = if (max > 0) (value / max).toFloat() * plot.height() else 0f
            if (h > 0f) {
                canvas.drawRoundRect(RectF(cx - barW / 2, plot.bottom - h, cx + barW / 2, plot.bottom), 2f, 2f, fill)
            }
            if (value > 0 && bars.size <= 24) {
                val v = format(value)
                canvas.drawText(v, cx - valueText.measureText(v) / 2, plot.bottom - h - 2f, valueText)
            }
            if (i % labelEvery == 0) {
                canvas.drawText(label, cx - axisText.measureText(label) / 2, rect.bottom, axisText)
            }
        }
    }
}
