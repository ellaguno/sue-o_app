package com.sesolibre.somnia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.db.NightLog
import com.sesolibre.somnia.data.db.NightTag

@Composable
fun nightTagLabel(tag: NightTag): String = when (tag) {
    NightTag.CAFFEINE -> stringResource(R.string.tag_caffeine)
    NightTag.ALCOHOL -> stringResource(R.string.tag_alcohol)
    NightTag.EXERCISE -> stringResource(R.string.tag_exercise)
    NightTag.LATE_DINNER -> stringResource(R.string.tag_late_dinner)
    NightTag.STRESS -> stringResource(R.string.tag_stress)
    NightTag.MEDICATION -> stringResource(R.string.tag_medication)
    NightTag.SICK -> stringResource(R.string.tag_sick)
    NightTag.SCREEN_LATE -> stringResource(R.string.tag_screen_late)
}

/** Bitácora rápida de la noche: chips de hábitos + nota opcional. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NightLogDialog(
    existing: NightLog?,
    onSave: (tags: Set<NightTag>, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected = remember(existing) {
        (existing?.tags ?: emptySet()).toMutableStateList()
    }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.night_log_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.night_log_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NightTag.entries.forEach { tag ->
                        FilterChip(
                            selected = tag in selected,
                            onClick = {
                                if (tag in selected) selected.remove(tag) else selected.add(tag)
                            },
                            label = { Text(nightTagLabel(tag)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.night_log_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected.toSet(), note) }) {
                Text(stringResource(R.string.night_log_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
