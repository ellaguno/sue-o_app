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

    private val HEADER = listOf(
        "fecha", "hora", "categoria", "duracion_s", "pico_db", "confianza", "transcripcion",
    )

    fun build(
        session: Session,
        events: List<SoundEvent>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append('\n')
        for (event in events.sortedBy { it.startEpochMs }) {
            val instant = Instant.ofEpochMilli(event.startEpochMs)
            val row = listOf(
                dateFmt.format(instant),
                timeFmt.format(instant),
                categoryName(ApneaHeuristic.effectiveCategory(event)),
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

    private fun categoryName(key: String?): String = when (SomniaCategory.fromKey(key)) {
        SomniaCategory.SNORING -> "ronquido"
        SomniaCategory.BREATHING -> "respiracion"
        SomniaCategory.COUGH -> "tos"
        SomniaCategory.SPEECH -> "habla"
        SomniaCategory.MOVEMENT -> "movimiento"
        SomniaCategory.ENVIRONMENT -> "ambiente"
        SomniaCategory.OTHER -> "otro"
        null -> "sin_clasificar"
    }
}
