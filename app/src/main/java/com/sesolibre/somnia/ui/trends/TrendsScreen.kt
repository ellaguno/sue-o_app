package com.sesolibre.somnia.ui.trends

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.sesolibre.somnia.R
import com.sesolibre.somnia.stats.NightMetrics
import com.sesolibre.somnia.stats.TagEffect
import com.sesolibre.somnia.stats.TrendsOverview
import com.sesolibre.somnia.ui.components.TrendBars
import com.sesolibre.somnia.ui.components.nightTagLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    onBack: () -> Unit,
    viewModel: TrendsViewModel = hiltViewModel(),
) {
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val correlations by viewModel.correlations.collectAsStateWithLifecycle()
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

    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trends_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
                actions = {
                    if (metrics.isNotEmpty()) {
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
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (metrics.isEmpty()) {
            Text(
                stringResource(R.string.trends_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OverviewCard(overview) }
            item { SnoringChartCard(metrics) }
            item { NightBreakdownCard(metrics) }
            item { CorrelationsCard(correlations) }
        }
    }
}

@Composable
private fun OverviewCard(overview: TrendsOverview) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.trends_overview_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.trends_nights, overview.nights),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.trends_avg_snoring, "%.1f".format(overview.avgSnoringMinutes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.trends_avg_body, "%.1f".format(overview.avgBodyEvents)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val h = (overview.avgDurationMinutes / 60).toInt()
            val m = (overview.avgDurationMinutes % 60).toInt()
            Text(
                stringResource(R.string.trends_avg_duration, "%dh %02dm".format(h, m)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (overview.nightsWithPausePattern > 0) {
                Text(
                    stringResource(
                        R.string.trends_pause_nights,
                        overview.nightsWithPausePattern,
                        overview.nights,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SnoringChartCard(metrics: List<NightMetrics>) {
    val dateFormat = stringResource(R.string.trends_bar_date_format)
    val formatter = remember(dateFormat) {
        DateTimeFormatter.ofPattern(dateFormat, Locale.getDefault())
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.trends_snoring_chart),
                style = MaterialTheme.typography.titleSmall,
            )
            TrendBars(
                values = metrics.map { it.snoringMinutes.toFloat() },
                labels = metrics.map {
                    Instant.ofEpochMilli(it.startEpochMs)
                        .atZone(ZoneId.systemDefault())
                        .format(formatter)
                },
            )
            Text(
                stringResource(R.string.trends_chart_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NightBreakdownCard(metrics: List<NightMetrics>) {
    val dateFormat = stringResource(R.string.trends_bar_date_format)
    val formatter = remember(dateFormat) {
        DateTimeFormatter.ofPattern(dateFormat, Locale.getDefault())
    }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.trends_breakdown_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.trends_breakdown_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Suma de todas las noches, destacada antes del detalle por día.
            Text(
                stringResource(R.string.trends_total_label, metrics.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                listOf(
                    stringResource(R.string.trends_count_snoring, metrics.sumOf { it.snoringCount }),
                    stringResource(R.string.trends_count_cough, metrics.sumOf { it.coughCount }),
                    stringResource(R.string.trends_count_speech, metrics.sumOf { it.speechCount }),
                    stringResource(R.string.trends_count_other, metrics.sumOf { it.otherCount }),
                    stringResource(
                        R.string.trends_total_snoring_min,
                        "%.1f".format(metrics.sumOf { it.snoringMinutes }),
                    ),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider()
            // Más reciente arriba.
            metrics.sortedByDescending { it.startEpochMs }.forEach { m ->
                val date = Instant.ofEpochMilli(m.startEpochMs)
                    .atZone(ZoneId.systemDefault())
                    .format(formatter)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.trends_breakdown_row_date, date),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        listOf(
                            stringResource(R.string.trends_count_snoring, m.snoringCount),
                            stringResource(R.string.trends_count_cough, m.coughCount),
                            stringResource(R.string.trends_count_speech, m.speechCount),
                            stringResource(R.string.trends_count_other, m.otherCount),
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CorrelationsCard(correlations: List<TagEffect>) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.trends_corr_title),
                style = MaterialTheme.typography.titleSmall,
            )
            if (correlations.isEmpty()) {
                Text(
                    stringResource(R.string.trends_corr_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                correlations.forEach { effect ->
                    val delta = "%+.1f".format(effect.deltaMinutes)
                    Text(
                        stringResource(
                            R.string.trends_corr_line,
                            nightTagLabel(effect.tag),
                            "%.1f".format(effect.avgSnoringWith),
                            "%.1f".format(effect.avgSnoringWithout),
                            delta,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
