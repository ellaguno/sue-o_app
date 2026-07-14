package com.sesolibre.somnia.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.data.MonitorState
import com.sesolibre.somnia.data.MonitorStateHolder
import com.sesolibre.somnia.data.SessionRepository
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.SessionWithStats
import com.sesolibre.somnia.service.MonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SessionRepository,
    stateHolder: MonitorStateHolder,
) : ViewModel() {

    val monitor: StateFlow<MonitorState> = stateHolder.state

    val sessions: StateFlow<List<SessionWithStats>> = repository.sessionsWithStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expandedSessionId = MutableStateFlow<Long?>(null)
    val expandedSessionId: StateFlow<Long?> = _expandedSessionId.asStateFlow()

    val expandedSamples: StateFlow<List<NoiseSample>> = _expandedSessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.samples(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleExpanded(sessionId: Long) {
        _expandedSessionId.value = if (_expandedSessionId.value == sessionId) null else sessionId
    }

    fun startMonitoring() = MonitorService.start(context)

    fun stopMonitoring() = MonitorService.stop(context)

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { repository.deleteSession(sessionId) }
    }
}
