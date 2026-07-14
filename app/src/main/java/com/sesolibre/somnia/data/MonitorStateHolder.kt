package com.sesolibre.somnia.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Estado en vivo del monitoreo, compartido entre el servicio y la UI. */
data class MonitorState(
    val running: Boolean = false,
    val sessionId: Long? = null,
    val startedAtMs: Long? = null,
    val currentDbfs: Double? = null,
    val minutesSaved: Int = 0,
    val eventsDetected: Int = 0,
)

@Singleton
class MonitorStateHolder @Inject constructor() {
    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    fun update(transform: (MonitorState) -> MonitorState) = _state.update(transform)
}
