package com.sesolibre.somnia.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.data.ProfileRepository
import com.sesolibre.somnia.data.db.QuestionnaireResult
import com.sesolibre.somnia.data.db.SleepCompanion
import com.sesolibre.somnia.data.db.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    val profile: StateFlow<UserProfile?> = repository.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val companions: StateFlow<List<SleepCompanion>> = repository.observeCompanions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val latestEpworth: StateFlow<QuestionnaireResult?> =
        repository.observeLatestResult(QuestionnaireResult.TYPE_EPWORTH)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestStopBang: StateFlow<QuestionnaireResult?> =
        repository.observeLatestResult(QuestionnaireResult.TYPE_STOP_BANG)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch { repository.saveProfile(profile) }
    }

    fun addCompanion(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.addCompanion(name, consentAckEpochMs = System.currentTimeMillis())
        }
    }

    fun removeCompanion(id: Long) {
        viewModelScope.launch { repository.removeCompanion(id) }
    }
}
