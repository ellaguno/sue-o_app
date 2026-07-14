package com.sesolibre.somnia.ui.night

import android.media.MediaPlayer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.data.SessionRepository
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class NightViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SessionRepository,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    val session: StateFlow<Session?> = repository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val samples: StateFlow<List<NoiseSample>> = repository.samples(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<SoundEvent>> = repository.events(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Ocurrencias del patrón ronquido→pausa→reanudación (ver [ApneaHeuristic]). */
    val pausePatternCount: StateFlow<Int> = repository.events(sessionId)
        .map { ApneaHeuristic.detect(it).size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Reetiquetado manual de un evento (prioridad sobre el clasificador). */
    fun relabel(event: SoundEvent, category: SomniaCategory) {
        viewModelScope.launch {
            repository.updateEvent(event.copy(manualLabel = category.key))
        }
    }

    private val _playingEventId = MutableStateFlow<Long?>(null)
    val playingEventId: StateFlow<Long?> = _playingEventId.asStateFlow()

    private var player: MediaPlayer? = null

    fun togglePlay(event: SoundEvent) {
        if (_playingEventId.value == event.id) {
            stopPlayback()
            return
        }
        val path = event.clipPath ?: return
        if (!File(path).exists()) return
        stopPlayback()
        player = MediaPlayer().apply {
            setDataSource(path)
            setOnCompletionListener { stopPlayback() }
            prepare()
            start()
        }
        _playingEventId.value = event.id
    }

    private fun stopPlayback() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        _playingEventId.value = null
    }

    fun deleteSession(onDeleted: () -> Unit) {
        stopPlayback()
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            onDeleted()
        }
    }

    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
