package com.sesolibre.somnia.ui.night

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.db.SleepCompanion
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.stats.Highlight
import com.sesolibre.somnia.stats.HighlightReason
import com.sesolibre.somnia.stats.NightSummary
import com.sesolibre.somnia.stats.SleepTip
import com.sesolibre.somnia.ui.components.NightLogDialog
import com.sesolibre.somnia.ui.components.NightTimeline
import com.sesolibre.somnia.ui.components.categoryColor
import com.sesolibre.somnia.ui.components.nightTagLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun categoryLabel(key: String?): String = when (SomniaCategory.fromKey(key)) {
    SomniaCategory.SNORING -> stringResource(R.string.cat_snoring)
    SomniaCategory.BREATHING -> stringResource(R.string.cat_breathing)
    SomniaCategory.COUGH -> stringResource(R.string.cat_cough)
    SomniaCategory.SPEECH -> stringResource(R.string.cat_speech)
    SomniaCategory.MOVEMENT -> stringResource(R.string.cat_movement)
    SomniaCategory.ENVIRONMENT -> stringResource(R.string.cat_environment)
    SomniaCategory.OTHER -> stringResource(R.string.cat_other)
    null -> stringResource(R.string.cat_unknown)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NightScreen(
    onBack: () -> Unit,
    viewModel: NightViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val playingId by viewModel.playingEventId.collectAsStateWithLifecycle()
    val pausePatterns by viewModel.pausePatternCount.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val companions by viewModel.companions.collectAsStateWithLifecycle()
    val sleepsAlone by viewModel.sleepsAlone.collectAsStateWithLifecycle()
    val nightLog by viewModel.nightLog.collectAsStateWithLifecycle()
    val transcriptionEnabled by viewModel.transcriptionEnabled.collectAsStateWithLifecycle()
    val transcribingId by viewModel.transcribingId.collectAsStateWithLifecycle()
    val transcribeOutcome by viewModel.transcribeOutcome.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val shareRequest by viewModel.shareRequest.collectAsStateWithLifecycle()

    val context = LocalContext.current
    LaunchedEffect(shareRequest) {
        shareRequest?.let { request ->
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", request.file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = request.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                // ClipData da permiso de lectura también al preview del selector.
                clipData = ClipData.newUri(context.contentResolver, request.file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.export_share)),
            )
            viewModel.clearShareRequest()
        }
    }
    LaunchedEffect(transcribeOutcome) {
        transcribeOutcome?.let { outcome ->
            val msg = when (outcome) {
                NightViewModel.TranscribeOutcome.EMPTY -> R.string.transcription_empty
                NightViewModel.TranscribeOutcome.UNAVAILABLE -> R.string.transcription_unavailable
                NightViewModel.TranscribeOutcome.LANGUAGE_MISSING -> R.string.transcription_language_missing
                NightViewModel.TranscribeOutcome.FAILED -> R.string.transcription_failed
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearTranscribeOutcome()
        }
    }

    var relabeling by remember { mutableStateOf<SoundEvent?>(null) }
    var attributing by remember { mutableStateOf<SoundEvent?>(null) }
    var editingLog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.session_delete_confirm_title)) },
            text = { Text(stringResource(R.string.session_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.deleteSession(onDeleted = onBack)
                }) { Text(stringResource(R.string.session_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val zone = remember { ZoneId.systemDefault() }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.getDefault()) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()) }

    relabeling?.let { event ->
        RelabelDialog(
            current = SomniaCategory.fromKey(ApneaHeuristic.effectiveCategory(event)),
            onSelect = { category ->
                viewModel.relabel(event, category)
                relabeling = null
            },
            onDismiss = { relabeling = null },
        )
    }

    attributing?.let { event ->
        AttributionDialog(
            companions = companions,
            currentCompanionId = event.attributedToCompanionId,
            onSelect = { companionId ->
                viewModel.attribute(event, companionId)
                attributing = null
            },
            onDismiss = { attributing = null },
        )
    }

    if (editingLog) {
        NightLogDialog(
            existing = nightLog,
            onSave = { tags, note ->
                viewModel.saveNightLog(tags, note)
                editingLog = false
            },
            onDismiss = { editingLog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        session?.let {
                            dateFmt.format(Instant.ofEpochMilli(it.startEpochMs).atZone(zone))
                        } ?: stringResource(R.string.night_title),
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
                actions = {
                    if (session != null) {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_options),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_csv)) },
                                    onClick = { menuExpanded = false; viewModel.exportCsv() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.export_pdf)) },
                                    onClick = { menuExpanded = false; viewModel.exportPdf() },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.session_delete)) },
                                    onClick = { menuExpanded = false; confirmingDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val s = session ?: return@Scaffold
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val durationMin = summary?.durationMinutes
                            ?: s.endEpochMs?.let { (it - s.startEpochMs) / 60_000 }
                        Text(
                            stringResource(
                                R.string.night_summary,
                                durationMin?.let { "%dh %02dm".format(it / 60, it % 60) }
                                    ?: stringResource(R.string.session_in_progress),
                                events.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        summary?.let { sum ->
                            if (sum.snoringEpisodes > 0) {
                                Text(
                                    stringResource(
                                        R.string.night_snore_summary,
                                        sum.snoringEpisodes,
                                        "%.1f".format(sum.snoringMinutes),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            sum.peakDb?.let { peak ->
                                Text(
                                    stringResource(R.string.night_peak, peak),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (s.batteryStartPct != null && s.batteryEndPct != null) {
                            Text(
                                stringResource(
                                    R.string.night_battery,
                                    s.batteryStartPct, s.batteryEndPct,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        NightTimeline(
                            samples = samples,
                            events = events,
                            startEpochMs = s.startEpochMs,
                            endEpochMs = s.endEpochMs,
                            calibrationOffset = s.calibrationDbOffset,
                        )
                    }
                }
            }

            summary?.let { sum ->
                item { CategoryBreakdown(sum) }
            }

            if (highlights.isNotEmpty()) {
                item {
                    HighlightsCard(
                        highlights = highlights,
                        playingId = playingId,
                        timeFor = { timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) },
                        onTogglePlay = { viewModel.togglePlay(it) },
                    )
                }
            }

            if (recommendations.isNotEmpty()) {
                item { RecommendationsCard(recommendations) }
            }

            if (!sleepsAlone) {
                item {
                    Text(
                        stringResource(R.string.multi_person_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            stringResource(R.string.night_log_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        val log = nightLog
                        if (log != null && (log.tags.isNotEmpty() || log.note != null)) {
                            val tagText = log.tags.map { nightTagLabel(it) }.joinToString(" · ")
                            if (tagText.isNotEmpty()) {
                                Text(tagText, style = MaterialTheme.typography.bodySmall)
                            }
                            log.note?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.night_log_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { editingLog = true }) {
                            Text(
                                stringResource(
                                    if (nightLog == null) R.string.night_log_add
                                    else R.string.night_log_edit
                                )
                            )
                        }
                    }
                }
            }

            if (pausePatterns > 0) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.pause_pattern_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.pause_pattern_body, pausePatterns),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.events_header, events.size),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (events.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.events_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(events, key = { it.id }) { event ->
                EventRow(
                    event = event,
                    playing = playingId == event.id,
                    timeText = timeFmt.format(Instant.ofEpochMilli(event.startEpochMs).atZone(zone)),
                    calibrationOffset = s.calibrationDbOffset,
                    attributionText = event.attributedToCompanionId?.let { id ->
                        companions.firstOrNull { it.id == id }?.name
                    },
                    canAttribute = companions.isNotEmpty(),
                    isSpeech = ApneaHeuristic.effectiveCategory(event) == SomniaCategory.SPEECH.key,
                    canTranscribe = transcriptionEnabled,
                    transcribing = transcribingId == event.id,
                    onTogglePlay = { viewModel.togglePlay(event) },
                    onRelabel = { relabeling = event },
                    onAttribute = { attributing = event },
                    onTranscribe = { viewModel.transcribe(event) },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryBreakdown(summary: NightSummary) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.night_breakdown_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (summary.bodyEventCount == 0) {
                Text(
                    stringResource(R.string.night_breakdown_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NightSummary.BODY_CATEGORIES.forEach { category ->
                        val n = summary.count(category)
                        if (n > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Spacer(
                                    Modifier
                                        .size(10.dp)
                                        .background(categoryColor(category), CircleShape),
                                )
                                Text(
                                    stringResource(
                                        R.string.night_chip_count,
                                        categoryLabel(category.key),
                                        n,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
            if (summary.environmentCount > 0 || summary.otherCount > 0) {
                Text(
                    stringResource(
                        R.string.night_breakdown_environment,
                        summary.environmentCount,
                        summary.otherCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventRow(
    event: SoundEvent,
    playing: Boolean,
    timeText: String,
    calibrationOffset: Double,
    attributionText: String?,
    canAttribute: Boolean,
    isSpeech: Boolean,
    canTranscribe: Boolean,
    transcribing: Boolean,
    onTogglePlay: () -> Unit,
    onRelabel: () -> Unit,
    onAttribute: () -> Unit,
    onTranscribe: () -> Unit,
) {
    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(timeText, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        R.string.event_detail,
                        "%.1f".format(event.durationMs / 1000.0),
                        (event.dbPeak + calibrationOffset).roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                        onClick = onRelabel,
                        label = {
                            val text = categoryLabel(ApneaHeuristic.effectiveCategory(event))
                            val suffix = when {
                                event.manualLabel != null -> " ✎"
                                event.confidence != null ->
                                    " · ${(event.confidence * 100).roundToInt()}%"
                                else -> ""
                            }
                            Text(text + suffix)
                        },
                    )
                    if (canAttribute || attributionText != null) {
                        AssistChip(
                            onClick = onAttribute,
                            label = {
                                Text(
                                    attributionText?.let {
                                        stringResource(R.string.attribution_of, it)
                                    } ?: stringResource(R.string.attribution_assign)
                                )
                            },
                        )
                    }
                }
                if (isSpeech) {
                    TranscriptBlock(
                        transcript = event.transcript,
                        canTranscribe = canTranscribe && event.clipPath != null,
                        transcribing = transcribing,
                        onTranscribe = onTranscribe,
                    )
                }
            }
            if (event.clipPath != null) {
                FilledTonalButton(onClick = onTogglePlay) {
                    Text(
                        if (playing) stringResource(R.string.event_stop)
                        else stringResource(R.string.event_play),
                    )
                }
            } else {
                Text(
                    stringResource(R.string.event_no_clip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun highlightReasonLabel(reason: HighlightReason): String = when (reason) {
    HighlightReason.PAUSE_PATTERN -> stringResource(R.string.highlight_pause_pattern)
    HighlightReason.LOUDEST_SNORE -> stringResource(R.string.highlight_loud_snore)
    HighlightReason.SPEECH -> stringResource(R.string.highlight_speech)
    HighlightReason.COUGH -> stringResource(R.string.highlight_cough)
    HighlightReason.LOUDEST -> stringResource(R.string.highlight_loudest)
}

@Composable
private fun sleepTipText(tip: SleepTip): String = when (tip) {
    SleepTip.PAUSE_PATTERN -> stringResource(R.string.tip_pause_pattern)
    SleepTip.ALCOHOL -> stringResource(R.string.tip_alcohol)
    SleepTip.REDUCE_SNORING -> stringResource(R.string.tip_reduce_snoring)
    SleepTip.LATE_DINNER -> stringResource(R.string.tip_late_dinner)
    SleepTip.CAFFEINE -> stringResource(R.string.tip_caffeine)
    SleepTip.SCREEN_LATE -> stringResource(R.string.tip_screen_late)
    SleepTip.STRESS -> stringResource(R.string.tip_stress)
    SleepTip.SHORT_SLEEP -> stringResource(R.string.tip_short_sleep)
}

@Composable
private fun HighlightsCard(
    highlights: List<Highlight>,
    playingId: Long?,
    timeFor: (Long) -> String,
    onTogglePlay: (SoundEvent) -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.highlights_title),
                style = MaterialTheme.typography.titleSmall,
            )
            highlights.forEach { highlight ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            highlightReasonLabel(highlight.reason),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            timeFor(highlight.event.startEpochMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { onTogglePlay(highlight.event) }) {
                        Text(
                            if (playingId == highlight.event.id) stringResource(R.string.event_stop)
                            else stringResource(R.string.event_play),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationsCard(tips: List<SleepTip>) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.recommendations_title),
                style = MaterialTheme.typography.titleSmall,
            )
            tips.forEach { tip ->
                Text(
                    stringResource(R.string.recommendation_bullet, sleepTipText(tip)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.recommendations_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TranscriptBlock(
    transcript: String?,
    canTranscribe: Boolean,
    transcribing: Boolean,
    onTranscribe: () -> Unit,
) {
    when {
        transcript != null -> Text(
            stringResource(R.string.transcription_quote, transcript),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.primary,
        )
        transcribing -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
            Text(
                stringResource(R.string.transcription_running),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        canTranscribe -> TextButton(
            onClick = onTranscribe,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(stringResource(R.string.transcription_action))
        }
    }
}

@Composable
private fun AttributionDialog(
    companions: List<SleepCompanion>,
    currentCompanionId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attribution_title)) },
        text = {
            Column {
                TextButton(
                    onClick = { onSelect(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.attribution_me) +
                            if (currentCompanionId == null) " ✓" else "",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                companions.forEach { companion ->
                    TextButton(
                        onClick = { onSelect(companion.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            companion.name +
                                if (companion.id == currentCompanionId) " ✓" else "",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun RelabelDialog(
    current: SomniaCategory?,
    onSelect: (SomniaCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.relabel_title)) },
        text = {
            Column {
                SomniaCategory.entries.forEach { category ->
                    TextButton(
                        onClick = { onSelect(category) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            categoryLabel(category.key) +
                                if (category == current) " ✓" else "",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
