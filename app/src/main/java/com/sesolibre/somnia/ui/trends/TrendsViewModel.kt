package com.sesolibre.somnia.ui.trends

import com.sesolibre.somnia.util.LocalDates
import com.sesolibre.somnia.R
import com.sesolibre.somnia.data.prefs.SettingsRepository
import com.sesolibre.somnia.export.EmailDraft
import kotlinx.coroutines.flow.first
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.data.SessionRepository
import com.sesolibre.somnia.export.TrendsCsvReport
import com.sesolibre.somnia.export.TrendsPdfReport
import com.sesolibre.somnia.stats.NightMetrics
import com.sesolibre.somnia.stats.TagEffect
import com.sesolibre.somnia.stats.TrendsAnalyzer
import com.sesolibre.somnia.stats.TrendsOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class TrendsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    repository: SessionRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val metrics: StateFlow<List<NightMetrics>> = repository.nightlyMetrics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val overview: StateFlow<TrendsOverview> = metrics
        .map { TrendsAnalyzer.overview(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TrendsOverview(0, 0.0, 0.0, 0.0, 0))

    val correlations: StateFlow<List<TagEffect>> = metrics
        .map { TrendsAnalyzer.correlations(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Exportar tendencias (CSV / PDF) ---

    data class ShareRequest(val file: File, val mime: String)

    private val _shareRequest = MutableStateFlow<ShareRequest?>(null)
    val shareRequest: StateFlow<ShareRequest?> = _shareRequest.asStateFlow()

    fun clearShareRequest() { _shareRequest.value = null }

    private fun trendsFileBase(): String = appContext.getString(R.string.export_file_trends)

    private fun csvHeader(): List<String> = TrendsCsvReport.headerFor(LocalDates.locale(appContext))

    private fun exportsDir(): File = File(appContext.cacheDir, "exports").apply { mkdirs() }

    fun exportCsv() {
        val current = metrics.value
        if (current.isEmpty()) return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                File(exportsDir(), "${trendsFileBase()}.csv").apply {
                    writeText(TrendsCsvReport.build(current, header = csvHeader()))
                }
            }
            _shareRequest.value = ShareRequest(file, "text/csv")
        }
    }

    fun exportPdf() {
        val current = metrics.value
        if (current.isEmpty()) return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                TrendsPdfReport.render(
                    context = appContext,
                    outFile = File(exportsDir(), "${trendsFileBase()}.pdf"),
                    metrics = current,
                    overview = overview.value,
                    correlations = correlations.value,
                )
            }
            _shareRequest.value = ShareRequest(file, "application/pdf")
        }
    }

    private val _emailDraft = MutableStateFlow<EmailDraft?>(null)
    val emailDraft: StateFlow<EmailDraft?> = _emailDraft.asStateFlow()

    fun clearEmailDraft() { _emailDraft.value = null }

    /** Correo con el PDF (gráficas) y el CSV de tendencias adjuntos. */
    fun prepareEmail() {
        val current = metrics.value
        if (current.isEmpty()) return
        viewModelScope.launch {
            _emailDraft.value = withContext(Dispatchers.IO) {
                val dir = File(exportsDir(), "email").apply { deleteRecursively(); mkdirs() }
                val pdf = TrendsPdfReport.render(
                    appContext, File(dir, "${trendsFileBase()}.pdf"),
                    current, overview.value, correlations.value,
                )
                val csv = File(dir, "${trendsFileBase()}.csv").apply { writeText(TrendsCsvReport.build(current, header = csvHeader())) }
                val o = overview.value
                EmailDraft(
                    to = settings.reportEmail.first(),
                    subject = appContext.getString(R.string.email_subject_trends, o.nights),
                    body = appContext.getString(
                        R.string.email_trends_body,
                        o.nights,
                        "%.1f".format(o.avgSnoringMinutes),
                        "%.1f".format(o.avgBodyEvents),
                        o.nightsWithPausePattern,
                    ) + "\n\n" + appContext.getString(R.string.email_disclaimer),
                    attachments = listOf(pdf, csv),
                )
            }
        }
    }
}
