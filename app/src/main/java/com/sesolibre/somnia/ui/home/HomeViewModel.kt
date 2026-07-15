package com.sesolibre.somnia.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.data.MonitorState
import com.sesolibre.somnia.data.MonitorStateHolder
import com.sesolibre.somnia.data.SessionRepository
import com.sesolibre.somnia.data.db.NightLog
import com.sesolibre.somnia.data.db.NightTag
import com.sesolibre.somnia.data.db.SessionWithStats
import com.sesolibre.somnia.service.MonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SessionRepository,
    stateHolder: MonitorStateHolder,
) : ViewModel() {

    val monitor: StateFlow<MonitorState> = stateHolder.state

    val sessions: StateFlow<List<SessionWithStats>> = repository.sessionsWithStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Bitácora de la sesión en curso (para capturarla al acostarse). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentNightLog: StateFlow<NightLog?> = stateHolder.state
        .flatMapLatest { state ->
            val id = state.sessionId
            if (state.running && id != null && id > 0) repository.nightLog(id) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveCurrentNightLog(tags: Set<NightTag>, note: String?) {
        val id = monitor.value.sessionId ?: return
        if (!monitor.value.running || id <= 0) return
        viewModelScope.launch { repository.saveNightLog(id, tags, note) }
    }

    fun startMonitoring() = MonitorService.start(context)

    fun stopMonitoring() = MonitorService.stop(context)
}
