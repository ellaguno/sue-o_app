package com.sesolibre.somnia.util

import android.content.Context
import android.text.format.DateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatos de fecha y hora en el idioma de la app (el del teléfono, o el que
 * se eligió para Somnia en Android 13+). Se piden por "esqueleto" (qué campos
 * mostrar) y Android arma el orden y los conectores de cada idioma:
 * "jueves, 5 de noviembre" en español, "Thursday, November 5" en inglés.
 * La hora respeta la preferencia 12/24 h del teléfono.
 */
object LocalDates {

    fun locale(context: Context): Locale = context.resources.configuration.locales[0]

    /** Formateador para el esqueleto [skeleton] (p. ej. "EEEEdMMMM"). */
    fun formatter(
        context: Context,
        skeleton: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DateTimeFormatter {
        val locale = locale(context)
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
            .withZone(zone)
    }

    /** Hora (con o sin segundos) en 12 o 24 h según el ajuste del teléfono. */
    fun time(context: Context, seconds: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): DateTimeFormatter {
        val h = if (DateFormat.is24HourFormat(context)) "HH" else "h"
        return formatter(context, if (seconds) "${h}mmss" else "${h}mm", zone)
    }

    /** Solo la hora en punto para ejes de gráficas ("23" / "11 PM"). */
    fun hour(context: Context, zone: ZoneId = ZoneId.systemDefault()): DateTimeFormatter =
        if (DateFormat.is24HourFormat(context)) {
            formatter(context, "HHmm", zone)
        } else {
            formatter(context, "ha", zone)
        }
}
