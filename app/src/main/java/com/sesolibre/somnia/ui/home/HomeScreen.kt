package com.sesolibre.somnia.ui.home

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesolibre.somnia.R
import com.sesolibre.somnia.audio.DbMeter
import com.sesolibre.somnia.data.db.SessionWithStats
import com.sesolibre.somnia.ui.components.NightLogDialog
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onRequestStart: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrends: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val monitor by viewModel.monitor.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentNightLog by viewModel.currentNightLog.collectAsStateWithLifecycle()

    var editingLog by remember { mutableStateOf(false) }
    if (editingLog) {
        NightLogDialog(
            existing = currentNightLog,
            onSave = { tags, note ->
                viewModel.saveCurrentNightLog(tags, note)
                editingLog = false
            },
            onDismiss = { editingLog = false },
        )
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.somnia_header),
                            contentDescription = stringResource(R.string.app_name),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            // weight(1f) da TODO el ancho libre de la fila: sin él
                            // el ancho se queda en el intrínseco del PNG (72 dp) y
                            // Fit encogería el logo a 21 dp de alto.
                            modifier = Modifier
                                .weight(1f)
                                .height(72.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onOpenProfile) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = stringResource(R.string.profile_title),
                                )
                            }
                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = stringResource(R.string.settings_title),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Una sola línea a todo el ancho; el tamaño de letra se ajusta
                    // solo para llenar el ancho disponible sin partirse.
                    BasicText(
                        text = stringResource(R.string.home_tagline),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 11.sp,
                            maxFontSize = 18.sp,
                            stepSize = 0.25.sp,
                        ),
                    )
                }
            }

            item {
                MonitorCard(
                    running = monitor.running,
                    startedAtMs = monitor.startedAtMs,
                    currentDbfs = monitor.currentDbfs,
                    minutesSaved = monitor.minutesSaved,
                    eventsDetected = monitor.eventsDetected,
                    onStart = onRequestStart,
                    onStop = viewModel::stopMonitoring,
                    onEditNightLog = { editingLog = true },
                )
            }

            item { BatteryOptimizationCard() }

            if (sessions.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = onOpenTrends,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.home_open_trends))
                    }
                }
                item {
                    Text(
                        stringResource(R.string.sessions_header),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(sessions, key = { it.session.id }) { s ->
                    SessionCard(item = s, onClick = { onOpenSession(s.session.id) })
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
    eventsDetected: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEditNightLog: () -> Unit,
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
                    stringResource(R.string.monitor_live_stats, minutesSaved, eventsDetected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onStop) { Text(stringResource(R.string.stop_monitoring)) }
                    TextButton(onClick = onEditNightLog) {
                        Text(stringResource(R.string.night_log_title))
                    }
                }
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
private fun SessionCard(item: SessionWithStats, onClick: () -> Unit) {
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
        }
    }
}
