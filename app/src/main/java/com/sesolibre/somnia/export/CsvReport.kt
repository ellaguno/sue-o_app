package com.sesolibre.somnia.export

import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Genera el CSV de una noche (una fila por evento) para abrir en una hoja de
 * cálculo. Números con punto decimal y separador coma. Lógica pura, testeable.
 */
object CsvReport {

    /**
     * Encabezados y nombres de categoría del CSV en un idioma. Son claves de
     * datos (sin acentos ni espacios) para que la hoja de cálculo no batalle.
     */
    data class Labels(val header: List<String>, val categories: Map<SomniaCategory?, String>)

    val SPANISH = Labels(
        header = listOf("fecha", "hora", "categoria", "duracion_s", "pico_db", "confianza", "transcripcion"),
        categories = mapOf(
            SomniaCategory.SNORING to "ronquido",
            SomniaCategory.BREATHING to "respiracion",
            SomniaCategory.COUGH to "tos",
            SomniaCategory.SPEECH to "habla",
            SomniaCategory.MOVEMENT to "movimiento",
            SomniaCategory.ENVIRONMENT to "ambiente",
            SomniaCategory.OTHER to "otro",
            null to "sin_clasificar",
        ),
    )

    val ENGLISH = Labels(
        header = listOf("date", "time", "category", "duration_s", "peak_db", "confidence", "transcript"),
        categories = mapOf(
            SomniaCategory.SNORING to "snoring",
            SomniaCategory.BREATHING to "breathing",
            SomniaCategory.COUGH to "cough",
            SomniaCategory.SPEECH to "speech",
            SomniaCategory.MOVEMENT to "movement",
            SomniaCategory.ENVIRONMENT to "ambient",
            SomniaCategory.OTHER to "other",
            null to "unclassified",
        ),
    )

    /** Español para cualquier variante de español; inglés para todo lo demás (igual que la app). */
    fun labelsFor(locale: Locale): Labels = if (locale.language == "es") SPANISH else ENGLISH

    fun build(
        session: Session,
        events: List<SoundEvent>,
        zone: ZoneId = ZoneId.systemDefault(),
        labels: Labels = SPANISH,
    ): String {
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
        val sb = StringBuilder()
        sb.append(labels.header.joinToString(",")).append('\n')
        for (event in events.sortedBy { it.startEpochMs }) {
            val instant = Instant.ofEpochMilli(event.startEpochMs)
            val row = listOf(
                dateFmt.format(instant),
                timeFmt.format(instant),
                labels.categories.getValue(SomniaCategory.fromKey(ApneaHeuristic.effectiveCategory(event))),
                String.format(Locale.US, "%.1f", event.durationMs / 1000.0),
                (event.dbPeak + session.calibrationDbOffset).roundToInt().toString(),
                event.confidence?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                event.transcript ?: "",
            )
            sb.append(row.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
