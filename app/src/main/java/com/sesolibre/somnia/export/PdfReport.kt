package com.sesolibre.somnia.export

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.stats.Highlight
import com.sesolibre.somnia.stats.HighlightReason
import com.sesolibre.somnia.stats.NightSummary
import com.sesolibre.somnia.stats.SleepTip
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reporte legible de una noche en PDF (una página). Resume la noche, el
 * desglose por categoría, lo más relevante y las recomendaciones.
 */
object PdfReport {

    private const val PAGE_W = 595 // A4 a 72 dpi
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    fun render(
        context: Context,
        outFile: File,
        session: Session,
        summary: NightSummary,
        highlights: List<Highlight>,
        tips: List<SleepTip>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): File {
        val dateFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", Locale("es")).withZone(zone)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)

        val title = Paint().apply { textSize = 20f; isFakeBoldText = true; isAntiAlias = true }
        val heading = Paint().apply { textSize = 13f; isFakeBoldText = true; isAntiAlias = true }
        val body = Paint().apply { textSize = 11f; isAntiAlias = true }
        val muted = Paint().apply { textSize = 10f; color = 0xFF666666.toInt(); isAntiAlias = true }

        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        val canvas = page.canvas
        var y = MARGIN + 8f

        canvas.drawText(context.getString(R.string.app_name), MARGIN, y, title)
        y += 22f
        canvas.drawText(dateFmt.format(Instant.ofEpochMilli(session.startEpochMs)), MARGIN, y, body)
        y += 24f

        // Resumen
        canvas.drawText(context.getString(R.string.export_pdf_summary), MARGIN, y, heading); y += 16f
        val duration = summary.durationMinutes?.let { "%dh %02dm".format(it / 60, it % 60) } ?: "—"
        canvas.drawText(
            context.getString(R.string.export_pdf_duration, duration, summary.totalEvents), MARGIN, y, body,
        ); y += 15f
        if (summary.snoringEpisodes > 0) {
            canvas.drawText(
                context.getString(
                    R.string.night_snore_summary,
                    summary.snoringEpisodes, "%.1f".format(summary.snoringMinutes),
                ),
                MARGIN, y, body,
            ); y += 15f
        }
        summary.peakDb?.let {
            canvas.drawText(context.getString(R.string.night_peak, it), MARGIN, y, body); y += 15f
        }
        // Desglose por categoría (una línea)
        val breakdown = NightSummary.BODY_CATEGORIES
            .filter { summary.count(it) > 0 }
            .joinToString("   ") { "${categoryLabel(context, it)}: ${summary.count(it)}" }
        if (breakdown.isNotEmpty()) { canvas.drawText(breakdown, MARGIN, y, body); y += 15f }
        y += 10f

        // Lo más relevante
        if (highlights.isNotEmpty()) {
            canvas.drawText(context.getString(R.string.highlights_title), MARGIN, y, heading); y += 16f
            highlights.forEach { h ->
                val line = "• ${reasonLabel(context, h.reason)} — " +
                    timeFmt.format(Instant.ofEpochMilli(h.event.startEpochMs))
                canvas.drawText(line, MARGIN, y, body); y += 15f
            }
            y += 10f
        }

        // Recomendaciones
        if (tips.isNotEmpty()) {
            canvas.drawText(context.getString(R.string.recommendations_title), MARGIN, y, heading); y += 16f
            tips.forEach { tip ->
                y = drawWrapped(canvas, "• " + tipText(context, tip), MARGIN, y, PAGE_W - 2 * MARGIN, body)
                y += 4f
            }
            y += 8f
            y = drawWrapped(
                canvas, context.getString(R.string.recommendations_disclaimer),
                MARGIN, y, PAGE_W - 2 * MARGIN, muted,
            )
        }

        doc.finishPage(page)
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    /** Dibuja texto ajustado al ancho; devuelve la nueva y. */
    private fun drawWrapped(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint,
    ): Float {
        var y = startY
        val words = text.split(" ")
        var line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, y, paint)
                y += paint.textSize + 4f
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, y, paint)
            y += paint.textSize + 4f
        }
        return y
    }

    private fun categoryLabel(context: Context, category: SomniaCategory): String = context.getString(
        when (category) {
            SomniaCategory.SNORING -> R.string.cat_snoring
            SomniaCategory.BREATHING -> R.string.cat_breathing
            SomniaCategory.COUGH -> R.string.cat_cough
            SomniaCategory.SPEECH -> R.string.cat_speech
            SomniaCategory.MOVEMENT -> R.string.cat_movement
            SomniaCategory.ENVIRONMENT -> R.string.cat_environment
            SomniaCategory.OTHER -> R.string.cat_other
        },
    )

    private fun reasonLabel(context: Context, reason: HighlightReason): String = context.getString(
        when (reason) {
            HighlightReason.PAUSE_PATTERN -> R.string.highlight_pause_pattern
            HighlightReason.LOUDEST_SNORE -> R.string.highlight_loud_snore
            HighlightReason.SPEECH -> R.string.highlight_speech
            HighlightReason.COUGH -> R.string.highlight_cough
            HighlightReason.LOUDEST -> R.string.highlight_loudest
        },
    )

    private fun tipText(context: Context, tip: SleepTip): String = context.getString(
        when (tip) {
            SleepTip.PAUSE_PATTERN -> R.string.tip_pause_pattern
            SleepTip.ALCOHOL -> R.string.tip_alcohol
            SleepTip.REDUCE_SNORING -> R.string.tip_reduce_snoring
            SleepTip.LATE_DINNER -> R.string.tip_late_dinner
            SleepTip.CAFFEINE -> R.string.tip_caffeine
            SleepTip.SCREEN_LATE -> R.string.tip_screen_late
            SleepTip.STRESS -> R.string.tip_stress
            SleepTip.SHORT_SLEEP -> R.string.tip_short_sleep
        },
    )
}
