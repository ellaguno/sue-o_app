package com.sesolibre.somnia.ui.night

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sesolibre.somnia.audio.ClipPlaybackGain
import com.sesolibre.somnia.audio.PcmDecoder
import com.sesolibre.somnia.data.ProfileRepository
import com.sesolibre.somnia.data.SessionRepository
import com.sesolibre.somnia.data.prefs.SettingsRepository
import com.sesolibre.somnia.data.db.NightLog
import com.sesolibre.somnia.data.db.NightTag
import com.sesolibre.somnia.data.db.NoiseSample
import com.sesolibre.somnia.data.db.Session
import com.sesolibre.somnia.data.db.SleepCompanion
import com.sesolibre.somnia.data.db.SoundEvent
import com.sesolibre.somnia.ml.ApneaHeuristic
import com.sesolibre.somnia.ml.SomniaCategory
import com.sesolibre.somnia.ml.SpeechTranscriber
import com.sesolibre.somnia.stats.Highlight
import com.sesolibre.somnia.stats.Highlights
import com.sesolibre.somnia.stats.NightAnalyzer
import com.sesolibre.somnia.stats.NightSummary
import com.sesolibre.somnia.stats.SleepTip
import com.sesolibre.somnia.stats.SleepTips
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class NightViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext appContext: Context,
    private val repository: SessionRepository,
    profileRepository: ProfileRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    private val transcriber = SpeechTranscriber(appContext)

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

    /** "Lo más relevante": pocos clips destacados para escuchar. */
    val highlights: StateFlow<List<Highlight>> = repository.events(sessionId)
        .map { Highlights.topClips(it, ApneaHeuristic.detect(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    /** Recomendaciones de higiene del sueño (reglas sobre datos + bitácora). */
    val recommendations: StateFlow<List<SleepTip>> = combine(summary, nightLog) { s, log ->
        if (s == null) emptyList() else SleepTips.forNight(s, log?.tags ?: emptySet())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveNightLog(tags: Set<NightTag>, note: String?) {
        viewModelScope.launch { repository.saveNightLog(sessionId, tags, note) }
    }

    /** Atribuye el evento a un acompañante (null = del usuario). */
    fun attribute(event: SoundEvent, companionId: Long?) {
        viewModelScope.launch { repository.attributeEvent(event.id, companionId) }
    }

    // --- Transcripción de habla (opt-in, on-device) ---

    /** Si la transcripción está habilitada en Ajustes y disponible en el equipo. */
    val transcriptionEnabled: StateFlow<Boolean> = settings.transcribeSpeech
        .map { it && transcriber.isAvailable() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Evento que se está transcribiendo (para el spinner); null si ninguno. */
    private val _transcribingId = MutableStateFlow<Long?>(null)
    val transcribingId: StateFlow<Long?> = _transcribingId.asStateFlow()

    enum class TranscribeOutcome { EMPTY, FAILED, UNAVAILABLE, LANGUAGE_MISSING }

    /** Resultado no-texto del último intento (para avisar al usuario); se limpia al mostrarlo. */
    private val _transcribeOutcome = MutableStateFlow<TranscribeOutcome?>(null)
    val transcribeOutcome: StateFlow<TranscribeOutcome?> = _transcribeOutcome.asStateFlow()

    fun clearTranscribeOutcome() { _transcribeOutcome.value = null }

    fun transcribe(event: SoundEvent) {
        val path = event.clipPath ?: return
        if (_transcribingId.value != null) return
        _transcribingId.value = event.id
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) { PcmDecoder.decode(File(path)) }
            val result = if (decoded == null) {
                SpeechTranscriber.Result.Failed(-1)
            } else {
                transcriber.transcribe(decoded.pcm, decoded.sampleRate)
            }
            when (result) {
                is SpeechTranscriber.Result.Text -> repository.saveTranscript(event.id, result.value)
                SpeechTranscriber.Result.Empty -> _transcribeOutcome.value = TranscribeOutcome.EMPTY
                SpeechTranscriber.Result.Unavailable -> _transcribeOutcome.value = TranscribeOutcome.UNAVAILABLE
                SpeechTranscriber.Result.LanguageMissing -> _transcribeOutcome.value = TranscribeOutcome.LANGUAGE_MISSING
                is SpeechTranscriber.Result.Failed -> _transcribeOutcome.value = TranscribeOutcome.FAILED
            }
            _transcribingId.value = null
        }
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
