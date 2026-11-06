package com.sesolibre.somnia.export

import android.content.Context
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.db.NightLog
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.QuestionnaireResult
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SleepCompanion
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.data.db.UserProfile
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.stats.Highlight
import com.sesolibre.somnia.stats.NightAnalyzer
import com.sesolibre.somnia.stats.NightSummary
import com.sesolibre.somnia.stats.SleepTip
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Todo lo que el reporte de una noche necesita (lo arma el ViewModel). */
data class NightReportData(
    val session: Session,
    val summary: NightSummary,
    val samples: List<NoiseSample>,
    val events: List<SoundEvent>,
    val highlights: List<Highlight>,
    val tips: List<SleepTip>,
    val nightLog: NightLog?,
    val companions: List<SleepCompanion>,
    val profile: UserProfile?,
    val epworth: QuestionnaireResult?,
    val stopBang: QuestionnaireResult?,
)

/**
 * Reporte PDF de una noche, pensado para llevarlo al médico: resumen en
 * tarjetas, gráfica de ruido con los eventos, ronquido por hora, desglose por
 * categoría, lo más relevante, pausas, bitácora, recomendaciones, perfil y
 * cuestionarios, y la tabla de eventos. Pagina solo.
 */
object PdfReport {

    /** Columnas de la tabla de eventos (x relativa al margen). */
    private val EVENT_COLS = floatArrayOf(0f, 50f, 130f, 180f, 230f, 268f, 420f, 480f)
    private const val MAX_PAUSES_LISTED = 40

    fun render(
        context: Context,
        outFile: File,
        data: NightReportData,
        zone: ZoneId = ZoneId.systemDefault(),
    ): File {
        val es = Locale("es")
        val dateFmt = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", es).withZone(zone)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
        val secFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(zone)
        val stampFmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", es).withZone(zone)
        fun at(ms: Long) = Instant.ofEpochMilli(ms)

        val session = data.session
        val summary = data.summary
        val offset = session.calibrationDbOffset
        val endMs = session.endEpochMs
            ?: (session.startEpochMs + ((data.samples.maxOfOrNull { it.minuteIndex } ?: 0) + 1) * 60_000L)

        val pdf = PdfWriter(context.getString(R.string.pdf_footer))

        // --- Encabezado ---
        pdf.text(context.getString(R.string.pdf_night_title), pdf.title, lineGap = 8f)
        pdf.text(
            dateFmt.format(at(session.startEpochMs)).replaceFirstChar { it.titlecase(es) },
            pdf.bold,
        )
        pdf.text(
            context.getString(
                R.string.pdf_night_range,
                timeFmt.format(at(session.startEpochMs)),
                timeFmt.format(at(endMs)),
                stampFmt.format(at(System.currentTimeMillis())),
            ),
            pdf.small,
        )
        data.profile?.displayName?.takeIf { it.isNotBlank() }?.let {
            pdf.text(context.getString(R.string.pdf_person, it), pdf.small)
        }
        pdf.space(10f)

        // --- Resumen en tarjetas ---
        pdf.tiles(
            listOf(
                ReportLabels.duration(summary.durationMinutes) to context.getString(R.string.pdf_tile_duration),
                summary.bodyEventCount.toString() to context.getString(R.string.pdf_tile_body),
                "%.1f".format(summary.snoringMinutes) to context.getString(R.string.pdf_tile_snoring_min),
                summary.snoringEpisodes.toString() to context.getString(R.string.pdf_tile_snoring_events),
                summary.pausePatternCount.toString() to context.getString(R.string.pdf_tile_pauses),
                (summary.peakDb?.let { "~$it" } ?: "—") to context.getString(R.string.pdf_tile_peak),
            ),
        )

        // --- Gráfica de ruido + eventos ---
        pdf.section(context.getString(R.string.pdf_timeline_title))
        if (data.samples.size >= 2) {
            val rect = pdf.block(130f)
            PdfCharts.noiseTimeline(
                pdf.canvas, rect, data.samples, data.events,
                session.startEpochMs, endMs, offset, zone,
            )
            val legendItems = SomniaCategory.entries.map {
                ReportLabels.category(context, it) to PdfCharts.argb(it)
            }
            pdf.ensureSpace(36f)
            pdf.space(pdf.small.textSize)
            pdf.y += PdfCharts.legend(pdf.canvas, pdf.left, pdf.y, pdf.left + pdf.contentWidth, legendItems)
            pdf.wrapped(context.getString(R.string.pdf_timeline_caption), pdf.small)
        } else {
            pdf.text(context.getString(R.string.pdf_no_samples), pdf.small)
        }

        // --- Ronquido por hora ---
        pdf.section(context.getString(R.string.pdf_hourly_title))
        val zoneOffset = zone.rules.getOffset(at(session.startEpochMs)).totalSeconds * 1000L
        val buckets = NightAnalyzer.hourlyBuckets(session, data.events, zoneOffset)
        if (buckets.any { it.snoringMinutes > 0 }) {
            val rect = pdf.block(100f)
            PdfCharts.verticalBars(
                pdf.canvas, rect,
                buckets.map { timeFmt.format(at(it.hourStartEpochMs)) to it.snoringMinutes },
                PdfCharts.argb(SomniaCategory.SNORING),
            ) { "%.1f".format(it) }
            pdf.wrapped(context.getString(R.string.pdf_hourly_caption), pdf.small)
        } else {
            pdf.text(context.getString(R.string.pdf_no_snoring), pdf.small)
        }

        // --- Desglose por categoría ---
        pdf.section(context.getString(R.string.pdf_breakdown_title))
        val breakdown = SomniaCategory.entries
            .map { Triple(ReportLabels.category(context, it), summary.count(it).toDouble(), PdfCharts.argb(it)) }
            .plus(
                if (summary.unknownCount > 0) {
                    listOf(Triple(context.getString(R.string.cat_unknown), summary.unknownCount.toDouble(), PdfCharts.argb(null)))
                } else {
                    emptyList()
                },
            )
            .filter { it.second > 0 }
        if (breakdown.isNotEmpty()) {
            val rect = pdf.block(breakdown.size * 15f)
            PdfCharts.horizontalBars(pdf.canvas, rect, breakdown) { it.roundToInt().toString() }
        } else {
            pdf.text(context.getString(R.string.events_empty), pdf.small)
        }

        // --- Lo más relevante ---
        if (data.highlights.isNotEmpty()) {
            pdf.section(context.getString(R.string.highlights_title))
            data.highlights.forEach { h ->
                val e = h.event
                pdf.text(
                    context.getString(
                        R.string.pdf_highlight_line,
                        secFmt.format(at(e.startEpochMs)),
                        ReportLabels.reason(context, h.reason),
                        ReportLabels.category(context, ApneaHeuristic.effectiveCategory(e)),
                        "%.1f".format(e.durationMs / 1000.0),
                        (e.dbPeak + offset).roundToInt(),
                    ),
                )
                e.transcript?.let { pdf.wrapped(context.getString(R.string.transcription_quote, it), pdf.small, x = pdf.left + 12f) }
            }
        }

        // --- Patrón de pausas ---
        val patterns = ApneaHeuristic.detect(data.events)
        if (patterns.isNotEmpty()) {
            pdf.section(context.getString(R.string.pause_pattern_title))
            pdf.wrapped(
                context.resources.getQuantityString(R.plurals.pause_pattern_body, patterns.size, patterns.size),
                pdf.small,
            )
            patterns.take(MAX_PAUSES_LISTED).forEach { p ->
                pdf.text(
                    context.getString(
                        R.string.pdf_pause_line,
                        secFmt.format(at(p.snoreEndEpochMs)),
                        (p.pauseMs / 1000).toInt(),
                    ),
                )
            }
            if (patterns.size > MAX_PAUSES_LISTED) {
                pdf.text(context.getString(R.string.pdf_more_items, patterns.size - MAX_PAUSES_LISTED), pdf.small)
            }
        }

        // --- Bitácora ---
        pdf.section(context.getString(R.string.night_log_title))
        val log = data.nightLog
        if (log != null && (log.tags.isNotEmpty() || log.note != null)) {
            if (log.tags.isNotEmpty()) pdf.text(log.tags.joinToString(" · ") { ReportLabels.tag(context, it) })
            log.note?.let { pdf.wrapped(it, pdf.small) }
        } else {
            pdf.text(context.getString(R.string.night_log_empty), pdf.small)
        }

        // --- Recomendaciones ---
        if (data.tips.isNotEmpty()) {
            pdf.section(context.getString(R.string.recommendations_title))
            data.tips.forEach { pdf.wrapped("• " + ReportLabels.tip(context, it)) }
            pdf.wrapped(context.getString(R.string.recommendations_disclaimer), pdf.small)
        }

        // --- Perfil y cuestionarios ---
        profileSection(context, pdf, data, stampFmt)

        // --- Detalles técnicos ---
        pdf.section(context.getString(R.string.pdf_tech_title))
        if (session.batteryStartPct != null && session.batteryEndPct != null) {
            pdf.text(context.getString(R.string.night_battery, session.batteryStartPct, session.batteryEndPct))
        }
        val clips = data.events.count { it.clipPath != null }
        pdf.text(context.getString(R.string.pdf_clips_saved, clips, data.events.size))
        pdf.wrapped(context.getString(R.string.pdf_db_note), pdf.small)

        // --- Tabla de eventos ---
        eventTable(context, pdf, data, secFmt)

        return pdf.writeTo(outFile)
    }

    private fun profileSection(
        context: Context,
        pdf: PdfWriter,
        data: NightReportData,
        stampFmt: DateTimeFormatter,
    ) {
        val profile = data.profile
        val facts = buildList {
            profile?.age?.let { add(context.getString(R.string.pdf_profile_age, it)) }
            profile?.bmi?.let { add(context.getString(R.string.pdf_profile_bmi, "%.1f".format(it))) }
            profile?.neckCm?.let { add(context.getString(R.string.pdf_profile_neck, it)) }
            profile?.sex?.let {
                add(
                    context.getString(
                        if (it == UserProfile.SEX_MALE) R.string.profile_sex_male else R.string.profile_sex_female,
                    ),
                )
            }
        }
        if (facts.isEmpty() && data.epworth == null && data.stopBang == null && profile?.sleepsAlone != false) return
        pdf.section(context.getString(R.string.pdf_profile_title))
        if (facts.isNotEmpty()) pdf.text(facts.joinToString(" · "))
        data.epworth?.let {
            pdf.text(
                context.getString(
                    R.string.pdf_questionnaire_line,
                    context.getString(R.string.epworth_name),
                    it.score, 24,
                    ReportLabels.epworthLevel(context, it.riskLevel),
                    stampFmt.format(Instant.ofEpochMilli(it.answeredEpochMs)),
                ),
            )
        }
        data.stopBang?.let {
            pdf.text(
                context.getString(
                    R.string.pdf_questionnaire_line,
                    context.getString(R.string.stopbang_name),
                    it.score, 8,
                    ReportLabels.stopBangLevel(context, it.riskLevel),
                    stampFmt.format(Instant.ofEpochMilli(it.answeredEpochMs)),
                ),
            )
        }
        if (data.epworth != null || data.stopBang != null) {
            pdf.wrapped(context.getString(R.string.questionnaires_disclaimer), pdf.small)
        }
        if (profile?.sleepsAlone == false) {
            pdf.wrapped(context.getString(R.string.multi_person_disclaimer), pdf.small)
        }
    }

    /** Tabla de eventos (sin los "Otro", que se cuentan en una nota). */
    private fun eventTable(
        context: Context,
        pdf: PdfWriter,
        data: NightReportData,
        secFmt: DateTimeFormatter,
    ) {
        val otherKey = SomniaCategory.OTHER.key
        val listed = data.events
            .filter { ApneaHeuristic.effectiveCategory(it) != otherKey }
            .sortedBy { it.startEpochMs }
        val omitted = data.events.size - listed.size

        pdf.newPage()
        pdf.section(context.getString(R.string.pdf_events_title))
        if (omitted > 0) pdf.text(context.getString(R.string.pdf_events_omitted, omitted), pdf.small)
        if (listed.isEmpty()) {
            pdf.text(context.getString(R.string.events_empty), pdf.small)
            return
        }
        val header = listOf(
            context.getString(R.string.pdf_col_time),
            context.getString(R.string.pdf_col_category),
            context.getString(R.string.pdf_col_duration),
            context.getString(R.string.pdf_col_peak),
            context.getString(R.string.pdf_col_conf),
            context.getString(R.string.pdf_col_label),
            context.getString(R.string.pdf_col_who),
            context.getString(R.string.pdf_col_clip),
        )
        pdf.row(header, EVENT_COLS, pdf.smallBold)
        val names = data.companions.associate { it.id to it.name }
        val offset = data.session.calibrationDbOffset
        listed.forEachIndexed { i, e ->
            val category = ApneaHeuristic.effectiveCategory(e)
            val label = ReportLabels.category(context, category) +
                if (e.manualLabel != null) " ✎" else ""
            // Al cambiar de página, repetir el encabezado.
            val before = pdf.y
            pdf.ensureSpace(pdf.small.textSize + 5f)
            if (pdf.y < before) pdf.row(header, EVENT_COLS, pdf.smallBold)
            pdf.row(
                listOf(
                    secFmt.format(Instant.ofEpochMilli(e.startEpochMs)),
                    label,
                    "%.1f s".format(e.durationMs / 1000.0),
                    "${(e.dbPeak + offset).roundToInt()}",
                    e.confidence?.let { "%.2f".format(it) } ?: "—",
                    e.rawLabel ?: "—",
                    e.attributedToCompanionId?.let { names[it] } ?: context.getString(R.string.attribution_me),
                    context.getString(if (e.clipPath != null) R.string.pdf_yes else R.string.pdf_no),
                ),
                EVENT_COLS,
                pdf.small,
                shaded = i % 2 == 1,
            )
        }
        pdf.wrapped(context.getString(R.string.pdf_events_legend), pdf.small)
    }
}
