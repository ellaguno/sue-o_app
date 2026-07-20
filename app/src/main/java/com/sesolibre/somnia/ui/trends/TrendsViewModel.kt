package com.sesolibre.somnia.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.data.SessionRepository
import com.sesolibre.somnia.stats.NightMetrics
import com.sesolibre.somnia.stats.TagEffect
import com.sesolibre.somnia.stats.TrendsAnalyzer
import com.sesolibre.somnia.stats.TrendsOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TrendsViewModel @Inject constructor(
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
}
