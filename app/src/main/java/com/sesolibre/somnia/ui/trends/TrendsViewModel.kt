package com.sesolibre.somnia.ui.trends

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

    private fun exportsDir(): File = File(appContext.cacheDir, "exports").apply { mkdirs() }

    fun exportCsv() {
        val current = metrics.value
        if (current.isEmpty()) return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                File(exportsDir(), "somnia-tendencias.csv").apply {
                    writeText(TrendsCsvReport.build(current))
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
                    outFile = File(exportsDir(), "somnia-tendencias.pdf"),
                    metrics = current,
                    overview = overview.value,
                )
            }
            _shareRequest.value = ShareRequest(file, "application/pdf")
        }
    }
}
