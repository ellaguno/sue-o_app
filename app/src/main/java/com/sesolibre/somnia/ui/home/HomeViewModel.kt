package com.sesolibre.somnia.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.data.MonitorState
import com.sesolibre.somnia.data.MonitorStateHolder
import com.sesolibre.somnia.data.SessionRepository
import com.sesolibre.somnia.data.db.SessionWithStats
import com.sesolibre.somnia.service.MonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    repository: SessionRepository,
    stateHolder: MonitorStateHolder,
) : ViewModel() {

    val monitor: StateFlow<MonitorState> = stateHolder.state

    val sessions: StateFlow<List<SessionWithStats>> = repository.sessionsWithStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startMonitoring() = MonitorService.start(context)

    fun stopMonitoring() = MonitorService.stop(context)
}
