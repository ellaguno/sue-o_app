package com.sesolibre.somnia.ui.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ui.components.NoiseSparkline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

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

    val zone = remember { ZoneId.systemDefault() }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.getDefault()) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()) }

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
                    onTogglePlay = { viewModel.togglePlay(event) },
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

@Composable
private fun EventRow(
    event: SoundEvent,
    playing: Boolean,
    timeText: String,
    calibrationOffset: Double,
    onTogglePlay: () -> Unit,
) {
    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
