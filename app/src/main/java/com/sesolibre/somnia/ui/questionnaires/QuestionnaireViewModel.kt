package com.sesolibre.somnia.ui.questionnaires

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.data.ProfileRepository
import com.sesolibre.somnia.data.db.QuestionnaireResult
import com.sesolibre.somnia.data.db.UserProfile
import com.sesolibre.somnia.questionnaires.Epworth
import com.sesolibre.somnia.questionnaires.StopBang
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    /** Para prellenar los ítems B/A/N/G de STOP-Bang. */
    val profile: StateFlow<UserProfile?> = repository.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveEpworth(answers: List<Int>, onSaved: () -> Unit) {
        val score = Epworth.score(answers)
        viewModelScope.launch {
            repository.saveQuestionnaireResult(
                QuestionnaireResult(
                    type = QuestionnaireResult.TYPE_EPWORTH,
                    answeredEpochMs = System.currentTimeMillis(),
                    score = score,
                    riskLevel = Epworth.riskLevel(score),
                    answersCsv = answers.joinToString(","),
                )
            )
            onSaved()
        }
    }

    fun saveStopBang(answers: List<Boolean>, onSaved: () -> Unit) {
        val score = StopBang.score(answers)
        viewModelScope.launch {
            repository.saveQuestionnaireResult(
                QuestionnaireResult(
                    type = QuestionnaireResult.TYPE_STOP_BANG,
                    answeredEpochMs = System.currentTimeMillis(),
                    score = score,
                    riskLevel = StopBang.riskLevel(score),
                    answersCsv = answers.joinToString(",") { if (it) "1" else "0" },
                )
            )
            onSaved()
        }
    }
}
