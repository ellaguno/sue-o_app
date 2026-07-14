package com.sesolibre.somnia.ui.home

import android.content.Intent
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesolibre.somnia.R
import com.sesolibre.somnia.audio.DbMeter
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.SessionWithStats
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onRequestStart: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val monitor by viewModel.monitor.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val expandedId by viewModel.expandedSessionId.collectAsStateWithLifecycle()
    val expandedSamples by viewModel.expandedSamples.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(top = 16.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        stringResource(R.string.home_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                MonitorCard(
                    running = monitor.running,
                    startedAtMs = monitor.startedAtMs,
                    currentDbfs = monitor.currentDbfs,
                    minutesSaved = monitor.minutesSaved,
                    onStart = onRequestStart,
                    onStop = viewModel::stopMonitoring,
                )
            }

            item { BatteryOptimizationCard() }

            if (sessions.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.sessions_header),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(sessions, key = { it.session.id }) { s ->
                    SessionCard(
                        item = s,
                        expanded = expandedId == s.session.id,
                        samples = if (expandedId == s.session.id) expandedSamples else emptyList(),
                        onClick = { viewModel.toggleExpanded(s.session.id) },
                        onDelete = { viewModel.deleteSession(s.session.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MonitorCard(
    running: Boolean,
    startedAtMs: Long?,
    currentDbfs: Double?,
    minutesSaved: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (running && startedAtMs != null) {
                var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        nowMs = System.currentTimeMillis()
                        delay(1000)
                    }
                }
                val elapsedSec = (nowMs - startedAtMs) / 1000
                Text(
                    stringResource(R.string.monitoring_active),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "%02d:%02d:%02d".format(elapsedSec / 3600, (elapsedSec % 3600) / 60, elapsedSec % 60),
                    style = MaterialTheme.typography.displaySmall,
                )
                val dbText = currentDbfs?.let {
                    stringResource(
                        R.string.db_approx_format,
                        (it + DbMeter.DEFAULT_CALIBRATION_OFFSET_DB).roundToInt(),
                    )
                } ?: "—"
                Text(dbText, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.minutes_saved, minutesSaved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onStop) { Text(stringResource(R.string.stop_monitoring)) }
            } else {
                Text(
                    stringResource(R.string.monitoring_idle),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.monitoring_idle_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onStart) { Text(stringResource(R.string.start_monitoring)) }
            }
        }
    }
}

@Composable
private fun BatteryOptimizationCard() {
    val context = LocalContext.current
    var ignoring by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            ignoring = isIgnoringBatteryOptimizations(context)
        }
    }
    if (ignoring) return

    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.battery_card_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.battery_card_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) { Text(stringResource(R.string.battery_card_action)) }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(context.packageName)

@Composable
private fun SessionCard(
    item: SessionWithStats,
    expanded: Boolean,
    samples: List<NoiseSample>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = item.session
    val zone = remember { ZoneId.systemDefault() }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault()) }
    val start = Instant.ofEpochMilli(s.startEpochMs).atZone(zone)
    val durationMin = s.endEpochMs?.let { (it - s.startEpochMs) / 60_000 }

    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(dateFmt.format(start), style = MaterialTheme.typography.titleSmall)
                Text(
                    durationMin?.let { "%dh %02dm".format(it / 60, it % 60) }
                        ?: stringResource(R.string.session_in_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.dbAvg != null) {
                val offset = s.calibrationDbOffset
                Text(
                    stringResource(
                        R.string.session_db_summary,
                        (item.dbMin!! + offset).roundToInt(),
                        (item.dbAvg + offset).roundToInt(),
                        (item.dbMax!! + offset).roundToInt(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                if (samples.size >= 2) {
                    NoiseSparkline(samples, calibrationOffset = s.calibrationDbOffset)
                } else {
                    Text(
                        stringResource(R.string.session_no_samples),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.session_delete)) }
            }
        }
    }
}

/** Curva simple de dB promedio por minuto (gráficas completas llegan en Etapa 5). */
@Composable
private fun NoiseSparkline(samples: List<NoiseSample>, calibrationOffset: Double) {
    val color = MaterialTheme.colorScheme.primary
    val values = samples.map { (it.dbAvg + calibrationOffset).toFloat() }
    val minV = values.min()
    val maxV = (values.max()).coerceAtLeast(minV + 1f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(vertical = 4.dp),
    ) {
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height * (1f - (v - minV) / (maxV - minV))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = color, style = Stroke(width = 3f))
    }
}
