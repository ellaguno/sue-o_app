package com.sesolibre.somnia.export

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.sesolibre.somnia.R
import com.sesolibre.somnia.stats.NightMetrics
import com.sesolibre.somnia.stats.TrendsOverview
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reporte PDF de tendencias: resumen general, totales y una tabla con una fila
 * por noche. Pagina automáticamente si hay muchas noches.
 */
object TrendsPdfReport {

    private const val PAGE_W = 595 // A4 a 72 dpi
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val ROW_H = 15f

    // Posiciones x de las columnas de la tabla.
    private val COLS = floatArrayOf(0f, 110f, 200f, 260f, 320f, 380f, 440f)

    fun render(
        context: Context,
        outFile: File,
        metrics: List<NightMetrics>,
        overview: TrendsOverview,
        zone: ZoneId = ZoneId.systemDefault(),
    ): File {
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()).withZone(zone)

        val title = Paint().apply { textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        val heading = Paint().apply { textSize = 13f; isFakeBoldText = true; isAntiAlias = true }
        val body = Paint().apply { textSize = 11f; isAntiAlias = true }
        val bold = Paint().apply { textSize = 11f; isFakeBoldText = true; isAntiAlias = true }

        val doc = PdfDocument()
        var pageNumber = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNumber).create())
        var y = MARGIN + 8f

        fun newPageIfNeeded() {
            if (y > PAGE_H - MARGIN) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNumber).create())
                y = MARGIN + 8f
            }
        }

        page.canvas.drawText(
            "${context.getString(R.string.app_name)} — ${context.getString(R.string.trends_title)}",
            MARGIN, y, title,
        )
        y += 26f

        // Resumen general
        page.canvas.drawText(context.getString(R.string.trends_overview_title), MARGIN, y, heading)
        y += 16f
        val h = (overview.avgDurationMinutes / 60).toInt()
        val m = (overview.avgDurationMinutes % 60).toInt()
        listOf(
            context.getString(R.string.trends_nights, overview.nights),
            context.getString(R.string.trends_avg_snoring, "%.1f".format(overview.avgSnoringMinutes)),
            context.getString(R.string.trends_avg_body, "%.1f".format(overview.avgBodyEvents)),
            context.getString(R.string.trends_avg_duration, "%dh %02dm".format(h, m)),
        ).forEach { line ->
            page.canvas.drawText(line, MARGIN, y, body)
            y += ROW_H
        }
        y += 12f

        // Tabla por noche
        val headers = listOf(
            context.getString(R.string.export_col_date),
            context.getString(R.string.cat_snoring),
            context.getString(R.string.cat_cough),
            context.getString(R.string.cat_speech),
            context.getString(R.string.cat_other),
            context.getString(R.string.export_col_snoring_min),
            context.getString(R.string.export_col_events),
        )
        fun drawRow(cells: List<String>, paint: Paint) {
            newPageIfNeeded()
            cells.forEachIndexed { i, cell ->
                page.canvas.drawText(cell, MARGIN + COLS[i], y, paint)
            }
            y += ROW_H
        }
        drawRow(headers, bold)
        val nights = metrics.sortedBy { it.startEpochMs }
        nights.forEach { night ->
            drawRow(
                listOf(
                    dateFmt.format(Instant.ofEpochMilli(night.startEpochMs)),
                    night.snoringCount.toString(),
                    night.coughCount.toString(),
                    night.speechCount.toString(),
                    night.otherCount.toString(),
                    "%.1f".format(night.snoringMinutes),
                    night.totalEvents.toString(),
                ),
                body,
            )
        }
        y += 4f
        drawRow(
            listOf(
                context.getString(R.string.export_totals),
                nights.sumOf { it.snoringCount }.toString(),
                nights.sumOf { it.coughCount }.toString(),
                nights.sumOf { it.speechCount }.toString(),
                nights.sumOf { it.otherCount }.toString(),
                "%.1f".format(nights.sumOf { it.snoringMinutes }),
                nights.sumOf { it.totalEvents }.toString(),
            ),
            bold,
        )

        doc.finishPage(page)
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }
}
