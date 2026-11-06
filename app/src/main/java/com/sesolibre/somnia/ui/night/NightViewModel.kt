package com.sesolibre.somnia.ui.night

import com.sesolibre.somnia.util.LocalDates
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
import com.sesolibre.somnia.export.CsvReport
import com.sesolibre.somnia.export.PdfReport
import com.sesolibre.somnia.export.ClipExporter
import com.sesolibre.somnia.export.EmailDraft
import com.sesolibre.somnia.export.NightReportData
import com.sesolibre.somnia.export.ReportMailer
import com.sesolibre.somnia.data.db.QuestionnaireResult
import com.sesolibre.somnia.R
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class NightViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repository: SessionRepository,
    profileRepository: ProfileRepository,
    private val settings: SettingsRepository,
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

    // --- Exportar la noche (CSV / PDF) ---

    data class ShareRequest(val file: File, val mime: String)

    private val _shareRequest = MutableStateFlow<ShareRequest?>(null)
    val shareRequest: StateFlow<ShareRequest?> = _shareRequest.asStateFlow()

    fun clearShareRequest() { _shareRequest.value = null }

    private fun exportsDir(): File = File(appContext.cacheDir, "exports").apply { mkdirs() }

    fun exportCsv() {
        val currentSession = session.value ?: return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                File(exportsDir(), "${nightFileBase(currentSession.startEpochMs)}.csv").apply {
                    writeText(CsvReport.build(currentSession, events.value, labels = csvLabels()))
                }
            }
            _shareRequest.value = ShareRequest(file, "text/csv")
        }
    }

    fun exportPdf() {
        viewModelScope.launch {
            val data = reportData() ?: return@launch
            val file = withContext(Dispatchers.IO) { renderPdf(data) }
            _shareRequest.value = ShareRequest(file, "application/pdf")
        }
    }

    private val profile = profileRepository.observeProfile()
    private val epworth = profileRepository.observeLatestResult(QuestionnaireResult.TYPE_EPWORTH)
    private val stopBang = profileRepository.observeLatestResult(QuestionnaireResult.TYPE_STOP_BANG)

    /** Foto actual de todo lo que va en el reporte (se lee directo de la BD). */
    private suspend fun reportData(): NightReportData? {
        val s = repository.observeSession(sessionId).first() ?: return null
        val evts = repository.events(sessionId).first()
        val smp = repository.samples(sessionId).first()
        val sum = NightAnalyzer.summarize(s, evts, smp)
        val log = repository.nightLog(sessionId).first()
        return NightReportData(
            session = s,
            summary = sum,
            samples = smp,
            events = evts,
            highlights = Highlights.topClips(evts, ApneaHeuristic.detect(evts)),
            tips = SleepTips.forNight(sum, log?.tags ?: emptySet()),
            nightLog = log,
            companions = companions.value,
            profile = profile.first(),
            epworth = epworth.first(),
            stopBang = stopBang.first(),
        )
    }

    private fun renderPdf(data: NightReportData): File = PdfReport.render(
        context = appContext,
        outFile = File(exportsDir(), "${nightFileBase(data.session.startEpochMs)}.pdf"),
        data = data,
    )

    /** "somnia-noche-2026-11-05" / "somnia-night-2026-11-05", según el idioma. */
    private fun nightFileBase(epochMs: Long): String =
        "${appContext.getString(R.string.export_file_night)}-${fileDate(epochMs)}"

    private fun csvLabels(): CsvReport.Labels = CsvReport.labelsFor(LocalDates.locale(appContext))

    private fun fileDate(epochMs: Long): String =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(java.time.ZoneId.systemDefault())
            .format(java.time.Instant.ofEpochMilli(epochMs))

    // --- Enviar por correo (reporte + grabaciones) ---

    /** Qué grabaciones adjuntar al correo. */
    sealed interface AudioSelection {
        data object None : AudioSelection
        data object Highlights : AudioSelection
        data class Category(val key: String) : AudioSelection
        data object Body : AudioSelection
        data object All : AudioSelection
        data class Single(val eventId: Long) : AudioSelection
    }

    /** Eventos con clip que entran en [selection] (en orden cronológico). */
    fun clipsFor(selection: AudioSelection, allEvents: List<SoundEvent> = events.value): List<SoundEvent> {
        val withClip = allEvents.filter { it.clipPath != null }
        val bodyKeys = NightSummary.BODY_CATEGORIES.map { it.key }.toSet()
        return when (selection) {
            AudioSelection.None -> emptyList()
            AudioSelection.Highlights ->
                Highlights.topClips(allEvents, ApneaHeuristic.detect(allEvents)).map { it.event }
            is AudioSelection.Category -> withClip.filter { ApneaHeuristic.effectiveCategory(it) == selection.key }
            AudioSelection.Body -> withClip.filter { ApneaHeuristic.effectiveCategory(it) in bodyKeys }
            AudioSelection.All -> withClip
            is AudioSelection.Single -> withClip.filter { it.id == selection.eventId }
        }.sortedBy { it.startEpochMs }
    }

    private val _emailPreparing = MutableStateFlow(false)
    val emailPreparing: StateFlow<Boolean> = _emailPreparing.asStateFlow()

    private val _emailDraft = MutableStateFlow<EmailDraft?>(null)
    val emailDraft: StateFlow<EmailDraft?> = _emailDraft.asStateFlow()

    fun clearEmailDraft() { _emailDraft.value = null }

    fun prepareEmail(includePdf: Boolean, includeCsv: Boolean, audio: AudioSelection) {
        if (_emailPreparing.value) return
        _emailPreparing.value = true
        viewModelScope.launch {
            try {
                val data = reportData() ?: return@launch
                val draft = withContext(Dispatchers.IO) {
                    // Cada envío parte de una carpeta limpia: no se cuelan clips de otro.
                    val dir = File(exportsDir(), "email").apply { deleteRecursively(); mkdirs() }
                    val files = mutableListOf<File>()
                    if (includePdf) {
                        files += PdfReport.render(appContext, File(dir, "${nightFileBase(data.session.startEpochMs)}.pdf"), data)
                    }
                    if (includeCsv) {
                        files += File(dir, "${nightFileBase(data.session.startEpochMs)}.csv")
                            .apply { writeText(CsvReport.build(data.session, data.events, labels = csvLabels())) }
                    }
                    val clips = ClipExporter.export(appContext, clipsFor(audio, data.events), File(dir, "audio"))
                    files += clips.map { it.file }
                    val single = audio is AudioSelection.Single
                    EmailDraft(
                        to = settings.reportEmail.first(),
                        subject = appContext.getString(
                            if (single) R.string.email_subject_clip else R.string.email_subject_night,
                            fileDate(data.session.startEpochMs),
                        ),
                        body = ReportMailer.nightBody(appContext, data, clips, includePdf),
                        attachments = files,
                    )
                }
                _emailDraft.value = draft
            } catch (t: Throwable) {
                Log.w(TAG, "no se pudo preparar el correo", t)
                _emailFailed.value = true
            } finally {
                _emailPreparing.value = false
            }
        }
    }

    private val _emailFailed = MutableStateFlow(false)
    val emailFailed: StateFlow<Boolean> = _emailFailed.asStateFlow()

    fun clearEmailFailed() { _emailFailed.value = false }

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

    /** Un clip no se pudo reproducir (dañado, a medio escribir…); se avisa una vez. */
    private val _playbackFailed = MutableStateFlow(false)
    val playbackFailed: StateFlow<Boolean> = _playbackFailed.asStateFlow()

    fun clearPlaybackFailed() { _playbackFailed.value = false }

    fun togglePlay(event: SoundEvent) {
        if (_playingEventId.value == event.id) {
            stopPlayback()
            return
        }
        val path = event.clipPath ?: return
        stopPlayback()
        if (!File(path).exists()) {
            _playbackFailed.value = true
            return
        }
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setDataSource(path)
            mp.setOnCompletionListener { stopPlayback() }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "error reproduciendo clip ($what/$extra)")
                stopPlayback()
                _playbackFailed.value = true
                true
            }
            mp.prepare()
            // Los clips se graban muy bajos (~-45 dBFS); los amplificamos por
            // clip según su pico para que sean audibles (ver ClipPlaybackGain).
            runCatching {
                enhancer = LoudnessEnhancer(mp.audioSessionId).apply {
                    setTargetGain(ClipPlaybackGain.gainMillibels(event.dbPeak))
                    enabled = true
                }
            }.onFailure { Log.w(TAG, "LoudnessEnhancer no disponible", it) }
            mp.start()
            _playingEventId.value = event.id
        } catch (t: Throwable) {
            // Un clip dañado no puede tumbar la app.
            Log.w(TAG, "no se pudo reproducir $path", t)
            stopPlayback()
            _playbackFailed.value = true
        }
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
