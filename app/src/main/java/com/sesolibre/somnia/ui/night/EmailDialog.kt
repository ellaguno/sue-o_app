package com.sesolibre.somnia.ui.night

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.export.ClipExporter
import com.sesolibre.somnia.ui.night.NightViewModel.AudioSelection

/** Tope práctico de adjuntos de la mayoría de los proveedores de correo. */
private const val EMAIL_SIZE_WARNING_BYTES = 20L * 1024 * 1024

/**
 * Elegir qué mandar por correo: el reporte PDF (con gráficas), el CSV y las
 * grabaciones (ninguna, lo más relevante, una categoría, sonidos del cuerpo,
 * todas, o un solo clip si se abrió desde un evento).
 */
@Composable
fun EmailDialog(
    singleEvent: SoundEvent?,
    filterCategoryKey: String?,
    clipsFor: (AudioSelection) -> List<SoundEvent>,
    onSend: (includePdf: Boolean, includeCsv: Boolean, audio: AudioSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    var includePdf by remember { mutableStateOf(singleEvent == null) }
    var includeCsv by remember { mutableStateOf(false) }

    // Opciones de audio con su conteo; se omiten las que no tendrían clips.
    val options: List<Pair<AudioSelection, String>> = buildList {
        add(AudioSelection.None to stringResource(R.string.email_audio_none))
        if (singleEvent != null) {
            add(AudioSelection.Single(singleEvent.id) to stringResource(R.string.email_audio_single))
        }
        add(AudioSelection.Highlights to stringResource(R.string.email_audio_highlights))
        filterCategoryKey?.let {
            add(AudioSelection.Category(it) to stringResource(R.string.email_audio_category, categoryLabel(it)))
        }
        add(AudioSelection.Body to stringResource(R.string.email_audio_body))
        add(AudioSelection.All to stringResource(R.string.email_audio_all))
    }
    val counts = options.associate { (sel, _) -> sel to clipsFor(sel) }
    val visible = options.filter { (sel, _) -> sel == AudioSelection.None || counts[sel].orEmpty().isNotEmpty() }
    var chosenAudio by remember {
        mutableStateOf<AudioSelection>(
            if (singleEvent != null) AudioSelection.Single(singleEvent.id) else AudioSelection.Highlights,
        )
    }
    // Si la opción elegida no tiene clips (p. ej. noche sin destacados), cae en "Ninguna".
    val audio = if (visible.any { it.first == chosenAudio }) chosenAudio else AudioSelection.None

    val selectedClips = counts[audio].orEmpty()
    val bytes = selectedClips.sumOf { ClipExporter.estimatedBytes(it) } +
        (if (includePdf) 150_000L else 0L) + (if (includeCsv) 20_000L else 0L)
    val nothing = !includePdf && !includeCsv && selectedClips.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.email_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CheckRow(stringResource(R.string.email_include_pdf), includePdf) { includePdf = it }
                CheckRow(stringResource(R.string.email_include_csv), includeCsv) { includeCsv = it }
                Text(
                    stringResource(R.string.email_audio_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                visible.forEach { (sel, label) ->
                    val n = counts[sel].orEmpty().size
                    RadioRow(
                        text = if (sel == AudioSelection.None) label else "$label ($n)",
                        selected = audio == sel,
                    ) { chosenAudio = sel }
                }
                if (selectedClips.isNotEmpty()) {
                    Text(
                        pluralStringResource(R.plurals.email_clip_count, selectedClips.size, selectedClips.size) +
                            " · " + stringResource(R.string.email_size, formatSize(bytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        stringResource(R.string.email_audio_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (bytes > EMAIL_SIZE_WARNING_BYTES) {
                    Text(
                        stringResource(R.string.email_size_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    stringResource(R.string.email_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !nothing,
                onClick = { onSend(includePdf, includeCsv, audio) },
            ) { Text(stringResource(R.string.email_prepare)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** "320 KB" o "1.4 MB": los clips sueltos pesan decenas de KB. */
private fun formatSize(bytes: Long): String =
    if (bytes < 1_048_576L) "${(bytes + 1023) / 1024} KB" else "%.1f MB".format(bytes / 1_048_576.0)

/** Mientras se generan el PDF y los clips amplificados. */
@Composable
fun EmailPreparingDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.email_preparing))
            }
        },
    )
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
    }
}
