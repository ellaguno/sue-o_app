package com.sesolibre.somnia.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.data.prefs.SettingsRepository
import com.sesolibre.somnia.schedule.SleepScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val sleepScheduler: SleepScheduler,
) : ViewModel() {

    val openMarginDb: StateFlow<Double> = settings.openMarginDb
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SettingsRepository.DEFAULT_OPEN_MARGIN_DB,
        )

    fun setOpenMarginDb(value: Double) {
        viewModelScope.launch { settings.setOpenMarginDb(value) }
    }

    fun resetOpenMarginDb() = setOpenMarginDb(SettingsRepository.DEFAULT_OPEN_MARGIN_DB)

    val transcribeSpeech: StateFlow<Boolean> = settings.transcribeSpeech
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setTranscribeSpeech(enabled: Boolean) {
        viewModelScope.launch { settings.setTranscribeSpeech(enabled) }
    }

    // --- Horario de sueño ---

    val scheduleEnabled: StateFlow<Boolean> = settings.scheduleEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val scheduleAutoStart: StateFlow<Boolean> = settings.scheduleAutoStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val bedtimeMinutes: StateFlow<Int> = settings.bedtimeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_BEDTIME_MIN)

    val wakeMinutes: StateFlow<Int> = settings.wakeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_WAKE_MIN)

    val scheduleAltEnabled: StateFlow<Boolean> = settings.scheduleAltEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val scheduleAltDays: StateFlow<Set<Int>> = settings.scheduleAltDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_ALT_DAYS)

    val altBedtimeMinutes: StateFlow<Int> = settings.altBedtimeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_ALT_BEDTIME_MIN)

    val altWakeMinutes: StateFlow<Int> = settings.altWakeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_ALT_WAKE_MIN)

    private val _exactAlarmAllowed = MutableStateFlow(sleepScheduler.canScheduleExactAlarms())

    /**
     * Permiso de alarmas exactas: sin él el inicio automático no se arma. Se
     * relee al volver a la pantalla, porque se concede en Ajustes del sistema.
     */
    val exactAlarmAllowed: StateFlow<Boolean> = _exactAlarmAllowed.asStateFlow()

    fun refreshExactAlarmPermission() {
        val allowed = sleepScheduler.canScheduleExactAlarms()
        if (_exactAlarmAllowed.value == allowed) return
        _exactAlarmAllowed.value = allowed
        // Si acaban de concederlo, arma ya la alarma de inicio.
        viewModelScope.launch { sleepScheduler.sync() }
    }

    fun setScheduleEnabled(enabled: Boolean) = withResync { settings.setScheduleEnabled(enabled) }
    fun setScheduleAutoStart(enabled: Boolean) = withResync { settings.setScheduleAutoStart(enabled) }
    fun setBedtime(minutes: Int) = withResync { settings.setBedtimeMinutes(minutes) }
    fun setWake(minutes: Int) = withResync { settings.setWakeMinutes(minutes) }
    fun setScheduleAltEnabled(enabled: Boolean) = withResync { settings.setScheduleAltEnabled(enabled) }
    fun setAltBedtime(minutes: Int) = withResync { settings.setAltBedtimeMinutes(minutes) }
    fun setAltWake(minutes: Int) = withResync { settings.setAltWakeMinutes(minutes) }

    /** Agrega o quita un día (ISO lunes=1…domingo=7) del horario alterno. */
    fun toggleAltDay(isoDay: Int) = withResync {
        val current = settings.scheduleAltDays.first()
        settings.setScheduleAltDays(
            if (isoDay in current) current - isoDay else current + isoDay
        )
    }

    /** Persiste el cambio y re-arma las alarmas del horario. */
    private inline fun withResync(crossinline persist: suspend () -> Unit) {
        viewModelScope.launch {
            persist()
            sleepScheduler.sync()
        }
    }
}
