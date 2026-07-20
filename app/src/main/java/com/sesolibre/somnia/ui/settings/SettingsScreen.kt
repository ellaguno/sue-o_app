package com.sesolibre.somnia.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.prefs.SettingsRepository
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val savedMargin by viewModel.openMarginDb.collectAsStateWithLifecycle()
    val transcribeSpeech by viewModel.transcribeSpeech.collectAsStateWithLifecycle()

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
