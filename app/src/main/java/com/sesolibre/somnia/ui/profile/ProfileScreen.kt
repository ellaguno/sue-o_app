package com.sesolibre.somnia.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.db.QuestionnaireResult
import com.sesolibre.somnia.data.db.SleepCompanion
import com.sesolibre.somnia.data.db.UserProfile
import com.sesolibre.somnia.questionnaires.Epworth
import com.sesolibre.somnia.questionnaires.StopBang
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenEpworth: () -> Unit,
    onOpenStopBang: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val companions by viewModel.companions.collectAsStateWithLifecycle()
    val epworth by viewModel.latestEpworth.collectAsStateWithLifecycle()
    val stopBang by viewModel.latestStopBang.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
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
            ProfileDataCard(
                profile = profile ?: UserProfile(),
                onSave = viewModel::saveProfile,
            )
            CompanionsCard(
                sleepsAlone = (profile ?: UserProfile()).sleepsAlone,
                companions = companions,
                onSleepsAloneChange = { alone ->
                    viewModel.saveProfile((profile ?: UserProfile()).copy(sleepsAlone = alone))
                },
                onAdd = viewModel::addCompanion,
                onRemove = viewModel::removeCompanion,
            )
            QuestionnairesCard(
                epworth = epworth,
                stopBang = stopBang,
                onOpenEpworth = onOpenEpworth,
                onOpenStopBang = onOpenStopBang,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileDataCard(
    profile: UserProfile,
    onSave: (UserProfile) -> Unit,
) {
    // Campos de texto locales; se persisten con el botón Guardar.
    var name by remember(profile.id, profile.displayName) {
        mutableStateOf(profile.displayName ?: "")
    }
    var birthYear by remember(profile.birthYear) {
        mutableStateOf(profile.birthYear?.toString() ?: "")
    }
    var sex by remember(profile.sex) { mutableStateOf(profile.sex) }
    var heightCm by remember(profile.heightCm) {
        mutableStateOf(profile.heightCm?.toString() ?: "")
    }
    var weightKg by remember(profile.weightKg) {
        mutableStateOf(profile.weightKg?.let { "%.0f".format(it) } ?: "")
    }
    var neckCm by remember(profile.neckCm) {
        mutableStateOf(profile.neckCm?.toString() ?: "")
    }

    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.profile_data_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.profile_data_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.profile_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = birthYear,
                    onValueChange = { birthYear = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.profile_birth_year)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = heightCm,
                    onValueChange = { heightCm = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.profile_height_cm)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightKg,
                    onValueChange = { weightKg = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.profile_weight_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = neckCm,
                    onValueChange = { neckCm = it.filter(Char::isDigit).take(2) },
                    label = { Text(stringResource(R.string.profile_neck_cm)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.profile_sex), style = MaterialTheme.typography.bodyMedium)
                FilterChip(
                    selected = sex == UserProfile.SEX_FEMALE,
                    onClick = {
                        sex = if (sex == UserProfile.SEX_FEMALE) null else UserProfile.SEX_FEMALE
                    },
                    label = { Text(stringResource(R.string.profile_sex_female)) },
                )
                FilterChip(
                    selected = sex == UserProfile.SEX_MALE,
                    onClick = {
                        sex = if (sex == UserProfile.SEX_MALE) null else UserProfile.SEX_MALE
                    },
                    label = { Text(stringResource(R.string.profile_sex_male)) },
                )
            }
            Button(onClick = {
                onSave(
                    profile.copy(
                        displayName = name.trim().takeIf { it.isNotEmpty() },
                        birthYear = birthYear.toIntOrNull(),
                        sex = sex,
                        heightCm = heightCm.toIntOrNull(),
                        weightKg = weightKg.toDoubleOrNull(),
                        neckCm = neckCm.toIntOrNull(),
                    )
                )
            }) { Text(stringResource(R.string.profile_save)) }
        }
    }
}

@Composable
private fun CompanionsCard(
    sleepsAlone: Boolean,
    companions: List<SleepCompanion>,
    onSleepsAloneChange: (Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (Long) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }

    if (adding) {
        AddCompanionDialog(
            onConfirm = { name ->
                onAdd(name)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }

    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.companions_title), style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.companions_sleeps_accompanied),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = !sleepsAlone, onCheckedChange = { onSleepsAloneChange(!it) })
            }
            if (!sleepsAlone) {
                Text(
                    stringResource(R.string.companions_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                companions.forEach { companion ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(companion.name, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { onRemove(companion.id) }) {
                            Text(stringResource(R.string.companions_remove))
                        }
                    }
                }
                TextButton(onClick = { adding = true }) {
                    Text(stringResource(R.string.companions_add))
                }
            }
        }
    }
}

@Composable
private fun AddCompanionDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.companions_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.companions_name)) },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text(
                        stringResource(R.string.companions_consent),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() && consent,
            ) { Text(stringResource(R.string.companions_add_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun QuestionnairesCard(
    epworth: QuestionnaireResult?,
    stopBang: QuestionnaireResult?,
    onOpenEpworth: () -> Unit,
    onOpenStopBang: () -> Unit,
) {
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.questionnaires_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.questionnaires_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QuestionnaireRow(
                name = stringResource(R.string.epworth_name),
                result = epworth,
                levelText = epworth?.let { epworthLevelLabel(it.riskLevel) },
                maxScore = Epworth.QUESTION_COUNT * Epworth.MAX_ANSWER,
                onOpen = onOpenEpworth,
            )
            QuestionnaireRow(
                name = stringResource(R.string.stopbang_name),
                result = stopBang,
                levelText = stopBang?.let { stopBangLevelLabel(it.riskLevel) },
                maxScore = StopBang.QUESTION_COUNT,
                onOpen = onOpenStopBang,
            )
        }
    }
}

@Composable
private fun QuestionnaireRow(
    name: String,
    result: QuestionnaireResult?,
    levelText: String?,
    maxScore: Int,
    onOpen: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val dateFmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()) }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (result != null && levelText != null) {
                Text(
                    stringResource(
                        R.string.questionnaire_result_summary,
                        result.score, maxScore, levelText,
                        dateFmt.format(Instant.ofEpochMilli(result.answeredEpochMs).atZone(zone)),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.questionnaire_not_answered),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onOpen) {
            Text(
                stringResource(
                    if (result == null) R.string.questionnaire_answer
                    else R.string.questionnaire_answer_again
                )
            )
        }
    }
}

@Composable
fun epworthLevelLabel(level: String): String = when (level) {
    Epworth.LEVEL_NORMAL -> stringResource(R.string.epworth_level_normal)
    Epworth.LEVEL_MILD -> stringResource(R.string.epworth_level_mild)
    Epworth.LEVEL_MODERATE -> stringResource(R.string.epworth_level_moderate)
    else -> stringResource(R.string.epworth_level_severe)
}

@Composable
fun stopBangLevelLabel(level: String): String = when (level) {
    StopBang.LEVEL_LOW -> stringResource(R.string.stopbang_level_low)
    StopBang.LEVEL_INTERMEDIATE -> stringResource(R.string.stopbang_level_intermediate)
    else -> stringResource(R.string.stopbang_level_high)
}
