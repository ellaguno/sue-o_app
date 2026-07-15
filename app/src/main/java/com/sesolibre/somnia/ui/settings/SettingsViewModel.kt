package com.sesolibre.somnia.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
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
}
