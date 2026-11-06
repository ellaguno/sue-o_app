package com.sesolibre.somnia.export

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Correo listo para abrir en la app de correo del usuario. */
data class EmailDraft(
    val to: String?,
    val subject: String,
    val body: String,
    val attachments: List<File>,
)

/**
 * Arma el correo con los reportes y grabaciones adjuntos. Nada se envía desde
 * Somnia: se abre la app de correo del usuario con todo prellenado y él decide
 * si lo manda. Los adjuntos viven en `cache/exports` y se comparten vía
 * [FileProvider] con permiso de solo lectura.
 */
object ReportMailer {

    fun intent(context: Context, draft: EmailDraft): Intent {
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList<Uri>(draft.attachments.map { FileProvider.getUriForFile(context, authority, it) })
        val send = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = if (uris.isEmpty()) "text/plain" else "*/*"
            draft.to?.takeIf { it.isNotBlank() }?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
            putExtra(Intent.EXTRA_SUBJECT, draft.subject)
            putExtra(Intent.EXTRA_TEXT, draft.body)
            when {
                uris.size > 1 -> putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                uris.size == 1 -> putExtra(Intent.EXTRA_STREAM, uris.first())
            }
            if (uris.isNotEmpty()) {
                // ClipData lleva el permiso de lectura a TODOS los adjuntos.
                clipData = ClipData.newUri(context.contentResolver, draft.attachments.first().name, uris.first())
                    .apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.email_chooser_title))
        // Solo apps de correo en el selector (no WhatsApp, Drive…): se excluyen
        // las que reciben archivos pero no manejan "mailto:". Si no hay ninguna
        // app de correo, se deja el selector completo para no bloquear el envío.
        // (Intent.selector con mailto: dejaba el selector vacío en Android 16+.)
        val mailApps = packagesFor(context, Intent(Intent.ACTION_SENDTO, "mailto:".toUri()))
        if (mailApps.isNotEmpty()) {
            val pm = context.packageManager
            val excluded = pm.queryIntentActivities(send, 0)
                .map { it.activityInfo }
                .filter { it.packageName !in mailApps }
                .map { ComponentName(it.packageName, it.name) }
            if (excluded.isNotEmpty()) {
                chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded.toTypedArray())
            }
        }
        return chooser
    }

    private fun packagesFor(context: Context, intent: Intent): Set<String> =
        context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .toSet()

    /** Texto del correo de una noche: resumen y la lista de grabaciones adjuntas. */
    fun nightBody(
        context: Context,
        data: NightReportData,
        clips: List<ClipExporter.Exported>,
        includesPdf: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val es = Locale("es")
        val dateFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", es).withZone(zone)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
        val s = data.summary
        val offset = data.session.calibrationDbOffset
        return buildString {
            appendLine(context.getString(R.string.email_intro, dateFmt.format(Instant.ofEpochMilli(data.session.startEpochMs))))
            appendLine()
            appendLine(context.getString(R.string.email_line_duration, ReportLabels.duration(s.durationMinutes)))
            appendLine(context.getString(R.string.email_line_body, s.bodyEventCount, s.totalEvents))
            appendLine(context.getString(R.string.email_line_snoring, s.snoringEpisodes, "%.1f".format(s.snoringMinutes)))
            if (s.pausePatternCount > 0) appendLine(context.getString(R.string.email_line_pauses, s.pausePatternCount))
            s.peakDb?.let { appendLine(context.getString(R.string.night_peak, it)) }
            data.nightLog?.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                appendLine(context.getString(R.string.email_line_log, tags.joinToString(", ") { ReportLabels.tag(context, it) }))
            }
            if (clips.isNotEmpty()) {
                appendLine()
                appendLine(context.getString(R.string.email_clips_header))
                clips.forEach { (event, file) ->
                    appendLine("• " + clipLine(context, event, timeFmt, offset) + " — " + file.name)
                }
                appendLine(context.getString(R.string.email_clips_note))
            }
            if (includesPdf) {
                appendLine()
                appendLine(context.getString(R.string.email_pdf_note))
            }
            appendLine()
            appendLine(context.getString(R.string.email_disclaimer))
        }
    }

    private fun clipLine(context: Context, e: SoundEvent, timeFmt: DateTimeFormatter, offset: Double) =
        context.getString(
            R.string.email_clip_line,
            timeFmt.format(Instant.ofEpochMilli(e.startEpochMs)),
            ReportLabels.category(context, ApneaHeuristic.effectiveCategory(e)),
            "%.1f".format(e.durationMs / 1000.0),
            (e.dbPeak + offset).roundToInt(),
        )
}
