package com.sesolibre.somnia.ui.night

import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.audio.ClipPlaybackGain
import com.sesolibre.somnia.data.ProfileRepository
import com.sesolibre.somnia.data.SessionRepository
import com.sesolibre.somnia.data.db.NightLog
import com.sesolibre.somnia.data.db.NightTag
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SleepCompanion
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.stats.NightAnalyzer
import com.sesolibre.somnia.stats.NightSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class NightViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SessionRepository,
    profileRepository: ProfileRepository,
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

    /** Resumen agregado de la noche (conteos, ronquidos, pico); null hasta cargar. */
    val summary: StateFlow<NightSummary?> = combine(
        repository.observeSession(sessionId),
        repository.events(sessionId),
        repository.samples(sessionId),
    ) { session, events, samples ->
        session?.let { NightAnalyzer.summarize(it, events, samples) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Reetiquetado manual de un evento (prioridad sobre el clasificador). */
    fun relabel(event: SoundEvent, category: SomniaCategory) {
        viewModelScope.launch {
            repository.updateEvent(event.copy(manualLabel = category.key))
        }
    }

    /** Acompañantes activos (para atribución y disclaimer multi-persona). */
    val companions: StateFlow<List<SleepCompanion>> = profileRepository.observeCompanions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sleepsAlone: StateFlow<Boolean> = profileRepository.observeProfile()
        .map { it?.sleepsAlone ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val nightLog: StateFlow<NightLog?> = repository.nightLog(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveNightLog(tags: Set<NightTag>, note: String?) {
        viewModelScope.launch { repository.saveNightLog(sessionId, tags, note) }
    }

    /** Atribuye el evento a un acompañante (null = del usuario). */
    fun attribute(event: SoundEvent, companionId: Long?) {
        viewModelScope.launch { repository.attributeEvent(event.id, companionId) }
    }

    private val _playingEventId = MutableStateFlow<Long?>(null)
    val playingEventId: StateFlow<Long?> = _playingEventId.asStateFlow()

    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null

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
            // Los clips se graban muy bajos (~-45 dBFS); los amplificamos por
            // clip según su pico para que sean audibles (ver ClipPlaybackGain).
            runCatching {
                enhancer = LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(ClipPlaybackGain.gainMillibels(event.dbPeak))
                    enabled = true
                }
            }.onFailure { Log.w(TAG, "LoudnessEnhancer no disponible", it) }
            start()
        }
        _playingEventId.value = event.id
    }

    private fun stopPlayback() {
        enhancer?.let {
            runCatching { it.release() }
        }
        enhancer = null
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

    private companion object {
        const val TAG = "NightViewModel"
    }
}
