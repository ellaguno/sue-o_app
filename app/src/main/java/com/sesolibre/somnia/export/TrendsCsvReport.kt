package com.sesolibre.somnia.export

import com.sesolibre.somnia.stats.NightMetrics
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV de tendencias: una fila por noche con los conteos por categoría y una
 * fila final TOTAL con las sumas. Lógica pura, testeable.
 */
object TrendsCsvReport {

    private val HEADER = listOf(
        "fecha", "duracion_min", "ronquidos_n", "ronquidos_min", "tos_n", "habla_n",
        "otros_n", "eventos_total", "pico_db", "patron_pausas_n", "etiquetas",
    )

    fun build(
        metrics: List<NightMetrics>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append('\n')
        val nights = metrics.sortedBy { it.startEpochMs }
        for (m in nights) {
            val row = listOf(
                dateFmt.format(Instant.ofEpochMilli(m.startEpochMs)),
                m.durationMinutes?.toString() ?: "",
                m.snoringCount.toString(),
                String.format(Locale.US, "%.1f", m.snoringMinutes),
                m.coughCount.toString(),
                m.speechCount.toString(),
                m.otherCount.toString(),
                m.totalEvents.toString(),
                m.peakDb?.toString() ?: "",
                m.pausePatternCount.toString(),
                m.tags.joinToString("|") { it.key },
            )
            sb.append(row.joinToString(",") { escape(it) }).append('\n')
        }
        val total = listOf(
            "TOTAL",
            nights.sumOf { it.durationMinutes ?: 0L }.toString(),
            nights.sumOf { it.snoringCount }.toString(),
            String.format(Locale.US, "%.1f", nights.sumOf { it.snoringMinutes }),
            nights.sumOf { it.coughCount }.toString(),
            nights.sumOf { it.speechCount }.toString(),
            nights.sumOf { it.otherCount }.toString(),
            nights.sumOf { it.totalEvents }.toString(),
            nights.mapNotNull { it.peakDb }.maxOrNull()?.toString() ?: "",
            nights.sumOf { it.pausePatternCount }.toString(),
            "",
        )
        sb.append(total.joinToString(",")).append('\n')
        return sb.toString()
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
