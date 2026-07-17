package com.sesolibre.somnia.ui.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.db.SleepCompanion
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.ui.components.NightLogDialog
import com.sesolibre.somnia.ui.components.NoiseSparkline
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
    val companions by viewModel.companions.collectAsStateWithLifecycle()
    val sleepsAlone by viewModel.sleepsAlone.collectAsStateWithLifecycle()
    val nightLog by viewModel.nightLog.collectAsStateWithLifecycle()

    var relabeling by remember { mutableStateOf<SoundEvent?>(null) }
    var attributing by remember { mutableStateOf<SoundEvent?>(null) }
    var editingLog by remember { mutableStateOf(false) }

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
                        val durationMin = s.endEpochMs?.let { (it - s.startEpochMs) / 60_000 }
                        Text(
                            stringResource(
                                R.string.night_summary,
                                durationMin?.let { "%dh %02dm".format(it / 60, it % 60) }
                                    ?: stringResource(R.string.session_in_progress),
                                events.size,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val snores = events.filter {
                            ApneaHeuristic.effectiveCategory(it) == SomniaCategory.SNORING.key
                        }
                        if (snores.isNotEmpty()) {
                            val snoreMin = snores.sumOf { it.durationMs } / 60_000.0
                            Text(
                                stringResource(
                                    R.string.night_snore_summary,
                                    snores.size,
                                    "%.1f".format(snoreMin),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
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
                        NoiseSparkline(samples, calibrationOffset = s.calibrationDbOffset)
                    }
                }
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
                    onTogglePlay = { viewModel.togglePlay(event) },
                    onRelabel = { relabeling = event },
                    onAttribute = { attributing = event },
                )
            }

            item {
                TextButton(onClick = { viewModel.deleteSession(onDeleted = onBack) }) {
                    Text(stringResource(R.string.session_delete))
                }
                Spacer(Modifier.height(24.dp))
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
    onTogglePlay: () -> Unit,
    onRelabel: () -> Unit,
    onAttribute: () -> Unit,
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
