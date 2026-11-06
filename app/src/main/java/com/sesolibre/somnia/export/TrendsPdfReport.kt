package com.sesolibre.somnia.export

import com.sesolibre.somnia.util.LocalDates
import android.content.Context
import com.sesolibre.somnia.R
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.stats.NightMetrics
import com.sesolibre.somnia.stats.TagEffect
import com.sesolibre.somnia.stats.TrendsOverview
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Reporte PDF de tendencias: resumen en tarjetas, gráficas por noche
 * (minutos roncando, sonidos del cuerpo), relación con la bitácora y una tabla
 * con una fila por noche. Pagina solo.
 */
object TrendsPdfReport {

    // Posiciones x de las columnas de la tabla.
    private val COLS = floatArrayOf(0f, 80f, 140f, 195f, 250f, 305f, 375f, 445f)

    fun render(
        context: Context,
        outFile: File,
        metrics: List<NightMetrics>,
        overview: TrendsOverview,
        correlations: List<TagEffect> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): File {
        // En el idioma de la app; la tabla usa fecha ISO, que no es ambigua en ningún idioma.
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
        val shortFmt = LocalDates.formatter(context, "dM", zone)
        val longFmt = LocalDates.formatter(context, "dMMMyyyy", zone)
        val nights = metrics.sortedBy { it.startEpochMs }
        fun at(ms: Long) = Instant.ofEpochMilli(ms)

        val pdf = PdfWriter(context.getString(R.string.pdf_footer))
        pdf.text(context.getString(R.string.pdf_trends_title), pdf.title, lineGap = 8f)
        if (nights.isNotEmpty()) {
            pdf.text(
                context.getString(
                    R.string.pdf_trends_range,
                    longFmt.format(at(nights.first().startEpochMs)),
                    longFmt.format(at(nights.last().startEpochMs)),
                ),
                pdf.bold,
            )
        }
        pdf.space(10f)

        val h = (overview.avgDurationMinutes / 60).toInt()
        val m = (overview.avgDurationMinutes % 60).toInt()
        pdf.tiles(
            listOf(
                overview.nights.toString() to context.getString(R.string.pdf_tile_nights),
                "%dh %02dm".format(h, m) to context.getString(R.string.pdf_tile_avg_duration),
                "%.1f".format(overview.avgSnoringMinutes) to context.getString(R.string.pdf_tile_avg_snoring),
                "%.1f".format(overview.avgBodyEvents) to context.getString(R.string.pdf_tile_avg_body),
                overview.nightsWithPausePattern.toString() to context.getString(R.string.pdf_tile_pause_nights),
            ),
        )

        pdf.section(context.getString(R.string.trends_snoring_chart))
        val rect = pdf.block(110f)
        PdfCharts.verticalBars(
            pdf.canvas, rect,
            nights.map { shortFmt.format(at(it.startEpochMs)) to it.snoringMinutes },
            PdfCharts.argb(SomniaCategory.SNORING),
        ) { if (it < 10) "%.1f".format(it) else "%.0f".format(it) }

        pdf.section(context.getString(R.string.pdf_trends_body_chart))
        val rect2 = pdf.block(110f)
        PdfCharts.verticalBars(
            pdf.canvas, rect2,
            nights.map { shortFmt.format(at(it.startEpochMs)) to it.bodyEventCount.toDouble() },
            PdfCharts.argb(SomniaCategory.BREATHING),
        ) { "%.0f".format(it) }

        if (correlations.isNotEmpty()) {
            pdf.section(context.getString(R.string.trends_corr_title))
            correlations.forEach { effect ->
                pdf.text(
                    context.getString(
                        R.string.trends_corr_line,
                        ReportLabels.tag(context, effect.tag),
                        "%.1f".format(effect.avgSnoringWith),
                        "%.1f".format(effect.avgSnoringWithout),
                        "%+.1f".format(effect.deltaMinutes),
                    ),
                )
            }
            pdf.wrapped(context.getString(R.string.pdf_corr_note), pdf.small)
        }

        // Tabla por noche
        pdf.section(context.getString(R.string.trends_breakdown_title))
        val header = listOf(
            context.getString(R.string.export_col_date),
            context.getString(R.string.pdf_col_duration),
            context.getString(R.string.cat_snoring),
            context.getString(R.string.cat_cough),
            context.getString(R.string.cat_speech),
            context.getString(R.string.cat_other),
            context.getString(R.string.export_col_snoring_min),
            context.getString(R.string.export_col_events),
        )
        pdf.row(header, COLS, pdf.smallBold)
        nights.forEachIndexed { i, night ->
            val before = pdf.y
            pdf.ensureSpace(pdf.body.textSize + 5f)
            if (pdf.y < before) pdf.row(header, COLS, pdf.smallBold)
            pdf.row(
                listOf(
                    dateFmt.format(at(night.startEpochMs)),
                    ReportLabels.duration(night.durationMinutes),
                    night.snoringCount.toString(),
                    night.coughCount.toString(),
                    night.speechCount.toString(),
                    night.otherCount.toString(),
                    "%.1f".format(night.snoringMinutes),
                    night.totalEvents.toString(),
                ),
                COLS,
                pdf.body,
                shaded = i % 2 == 1,
            )
        }
        pdf.space(2f)
        pdf.row(
            listOf(
                context.getString(R.string.export_totals),
                ReportLabels.duration(nights.sumOf { it.durationMinutes ?: 0L }),
                nights.sumOf { it.snoringCount }.toString(),
                nights.sumOf { it.coughCount }.toString(),
                nights.sumOf { it.speechCount }.toString(),
                nights.sumOf { it.otherCount }.toString(),
                "%.1f".format(nights.sumOf { it.snoringMinutes }),
                nights.sumOf { it.totalEvents }.toString(),
            ),
            COLS,
            pdf.bold,
        )
        pdf.space(6f)
        pdf.wrapped(context.getString(R.string.recommendations_disclaimer), pdf.small)

        return pdf.writeTo(outFile)
    }
}
