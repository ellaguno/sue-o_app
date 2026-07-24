package com.sesolibre.somnia.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.prefs.SettingsRepository
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val savedMargin by viewModel.openMarginDb.collectAsStateWithLifecycle()
    val transcribeSpeech by viewModel.transcribeSpeech.collectAsStateWithLifecycle()
    val scheduleEnabled by viewModel.scheduleEnabled.collectAsStateWithLifecycle()
    val scheduleAutoStart by viewModel.scheduleAutoStart.collectAsStateWithLifecycle()
    val bedtimeMinutes by viewModel.bedtimeMinutes.collectAsStateWithLifecycle()
    val wakeMinutes by viewModel.wakeMinutes.collectAsStateWithLifecycle()
    val altEnabled by viewModel.scheduleAltEnabled.collectAsStateWithLifecycle()
    val altDays by viewModel.scheduleAltDays.collectAsStateWithLifecycle()
    val altBedtimeMinutes by viewModel.altBedtimeMinutes.collectAsStateWithLifecycle()
    val altWakeMinutes by viewModel.altWakeMinutes.collectAsStateWithLifecycle()
    val exactAlarmAllowed by viewModel.exactAlarmAllowed.collectAsStateWithLifecycle()

    // El permiso se concede en Ajustes del sistema: hay que releerlo al volver.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshExactAlarmPermission() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScheduleCard(
                enabled = scheduleEnabled,
                autoStart = scheduleAutoStart,
                exactAlarmAllowed = exactAlarmAllowed,
                bedtimeMinutes = bedtimeMinutes,
                wakeMinutes = wakeMinutes,
                altEnabled = altEnabled,
                altDays = altDays,
                altBedtimeMinutes = altBedtimeMinutes,
                altWakeMinutes = altWakeMinutes,
                onToggleEnabled = viewModel::setScheduleEnabled,
                onToggleAutoStart = viewModel::setScheduleAutoStart,
                onSetBedtime = viewModel::setBedtime,
                onSetWake = viewModel::setWake,
                onToggleAltEnabled = viewModel::setScheduleAltEnabled,
                onToggleAltDay = viewModel::toggleAltDay,
                onSetAltBedtime = viewModel::setAltBedtime,
                onSetAltWake = viewModel::setAltWake,
            )
            SensitivityCard(
                savedMargin = savedMargin,
                onSave = viewModel::setOpenMarginDb,
                onReset = viewModel::resetOpenMarginDb,
            )
            TranscriptionCard(
                enabled = transcribeSpeech,
                onToggle = viewModel::setTranscribeSpeech,
            )
        }
    }
}

@Composable
private fun ScheduleCard(
    enabled: Boolean,
    autoStart: Boolean,
    exactAlarmAllowed: Boolean,
    bedtimeMinutes: Int,
    wakeMinutes: Int,
    altEnabled: Boolean,
    altDays: Set<Int>,
    altBedtimeMinutes: Int,
    altWakeMinutes: Int,
    onToggleEnabled: (Boolean) -> Unit,
    onToggleAutoStart: (Boolean) -> Unit,
    onSetBedtime: (Int) -> Unit,
    onSetWake: (Int) -> Unit,
    onToggleAltEnabled: (Boolean) -> Unit,
    onToggleAltDay: (Int) -> Unit,
    onSetAltBedtime: (Int) -> Unit,
    onSetAltWake: (Int) -> Unit,
) {
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.schedule_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(checked = enabled, onCheckedChange = onToggleEnabled)
            }
            Text(
                stringResource(R.string.schedule_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (enabled) {
                TimeRow(
                    label = stringResource(R.string.schedule_wake),
                    minutes = wakeMinutes,
                    onPick = onSetWake,
                )
                Text(
                    stringResource(R.string.schedule_stop_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.schedule_autostart),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(checked = autoStart, onCheckedChange = onToggleAutoStart)
                }
                if (autoStart) {
                    TimeRow(
                        label = stringResource(R.string.schedule_bedtime),
                        minutes = bedtimeMinutes,
                        onPick = onSetBedtime,
                    )
                    Text(
                        stringResource(R.string.schedule_autostart_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!exactAlarmAllowed) ExactAlarmWarning()
                }

                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.schedule_alt_toggle),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(checked = altEnabled, onCheckedChange = onToggleAltEnabled)
                }
                if (altEnabled) {
                    Text(
                        stringResource(R.string.schedule_alt_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AltDaysRow(selected = altDays, onToggle = onToggleAltDay)
                    TimeRow(
                        label = stringResource(R.string.schedule_alt_wake),
                        minutes = altWakeMinutes,
                        onPick = onSetAltWake,
                    )
                    if (autoStart) {
                        TimeRow(
                            label = stringResource(R.string.schedule_alt_bedtime),
                            minutes = altBedtimeMinutes,
                            onPick = onSetAltBedtime,
                        )
                    }
                }
            }
        }
    }
}

/**
 * El inicio automático necesita alarmas exactas (Android 12+). Sin ese permiso
 * la alarma nunca se arma, así que se avisa aquí con el atajo para concederlo.
 */
@Composable
private fun ExactAlarmWarning() {
    val context = LocalContext.current
    Text(
        stringResource(R.string.schedule_exact_alarm_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    TextButton(
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.fromParts("package", context.packageName, null),
                )
                if (runCatching { context.startActivity(intent) }.isFailure) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                }
            }
        },
    ) {
        Text(stringResource(R.string.schedule_exact_alarm_action))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AltDaysRow(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    val locale = Locale.getDefault()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day.value in selected,
                onClick = { onToggle(day.value) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
            )
        }
    }
}

@Composable
private fun TimeRow(
    label: String,
    minutes: Int,
    onPick: (Int) -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        TextButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onPick(hour * 60 + minute) },
                    minutes / 60,
                    minutes % 60,
                    true, // formato 24 h
                ).show()
            },
        ) {
            Text(
                stringResource(R.string.schedule_time_value, minutes / 60, minutes % 60),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun TranscriptionCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.transcription_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            Text(
                stringResource(R.string.transcription_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val context = LocalContext.current
            TextButton(
                onClick = {
                    // El paquete de voz on-device se descarga solo al transcribir;
                    // este atajo lleva a Ajustes de idioma por si se quiere hacer a mano.
                    val targets = listOf(
                        Settings.ACTION_LOCALE_SETTINGS,
                        Settings.ACTION_VOICE_INPUT_SETTINGS,
                        Settings.ACTION_SETTINGS,
                    )
                    targets.firstOrNull { action ->
                        runCatching { context.startActivity(Intent(action)) }.isSuccess
                    }
                },
            ) {
                Text(stringResource(R.string.transcription_download_languages))
            }
        }
    }
}

@Composable
private fun SensitivityCard(
    savedMargin: Double,
    onSave: (Double) -> Unit,
    onReset: () -> Unit,
) {
    // El slider edita una copia local; se persiste al soltar el control.
    var sliderValue by rememberSaveable(savedMargin) { mutableFloatStateOf(savedMargin.toFloat()) }

    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.sensitivity_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.sensitivity_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.sensitivity_value, sliderValue.roundToInt()),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onSave(sliderValue.roundToInt().toDouble()) },
                valueRange = SettingsRepository.MIN_OPEN_MARGIN_DB.toFloat()..
                    SettingsRepository.MAX_OPEN_MARGIN_DB.toFloat(),
                steps = (SettingsRepository.MAX_OPEN_MARGIN_DB -
                    SettingsRepository.MIN_OPEN_MARGIN_DB).toInt() - 1,
            )
            Text(
                stringResource(R.string.sensitivity_scale_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.sensitivity_floor_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (savedMargin != SettingsRepository.DEFAULT_OPEN_MARGIN_DB) {
                TextButton(onClick = onReset) {
                    Text(
                        stringResource(
                            R.string.sensitivity_reset,
                            SettingsRepository.DEFAULT_OPEN_MARGIN_DB.roundToInt(),
                        )
                    )
                }
            }
        }
    }
}
