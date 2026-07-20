package com.sesolibre.somnia.ml

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Transcripción de habla 100% on-device con [SpeechRecognizer]. No usa el
 * micrófono: se le pasa el PCM del clip vía `EXTRA_AUDIO_SOURCE` (API 33+).
 * Nada de audio ni texto sale del teléfono.
 */
class SpeechTranscriber(private val context: Context) {

    sealed interface Result {
        data class Text(val value: String) : Result
        /** Reconocedor no disponible en este dispositivo/versión. */
        data object Unavailable : Result
        /** Se ejecutó pero no reconoció nada legible (silencio, ruido…). */
        data object Empty : Result
        /** Falta el paquete de idioma on-device (se dispara su descarga). */
        data object LanguageMissing : Result
        data class Failed(val errorCode: Int) : Result
    }

    fun isAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    /** Transcribe [pcm] (16-bit mono a [sampleRate]); main-safe. */
    suspend fun transcribe(pcm: ShortArray, sampleRate: Int): Result {
        if (!isAvailable() || pcm.isEmpty()) return Result.Unavailable
        // Los clips se graban muy bajos (~-45 dBFS); sin amplificar, el
        // reconocedor no encuentra habla. Normalizamos el pico antes de pasarlo.
        val pcmFile = writePcm(normalize(pcm)) ?: return Result.Failed(-1)
        return try {
            val result = withContext(Dispatchers.Main) { recognize(pcmFile, sampleRate) }
            if (result is Result.LanguageMissing) {
                withContext(Dispatchers.Main) { triggerLanguageDownload() }
            }
            result
        } finally {
            pcmFile.delete()
        }
    }

    /**
     * Pide a los Servicios de Voz que descarguen el paquete de idioma on-device
     * que falta, para que un intento posterior sí funcione. Best-effort.
     */
    private fun triggerLanguageDownload() {
        runCatching {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            }
            recognizer.triggerModelDownload(intent)
        }.onFailure { Log.w(TAG, "no se pudo disparar la descarga de idioma", it) }
    }

    /** Amplifica el PCM para que el pico quede cerca de escala completa (~-1 dBFS). */
    private fun normalize(pcm: ShortArray): ShortArray {
        var peak = 0
        for (sample in pcm) {
            val abs = kotlin.math.abs(sample.toInt())
            if (abs > peak) peak = abs
        }
        if (peak == 0 || peak >= TARGET_PEAK) return pcm
        val gain = TARGET_PEAK.toDouble() / peak
        return ShortArray(pcm.size) {
            (pcm[it] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun writePcm(pcm: ShortArray): File? = runCatching {
        val file = File.createTempFile("stt", ".pcm", context.cacheDir)
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { bytes.putShort(it) }
        file.writeBytes(bytes.array())
        file
    }.getOrNull()

    private suspend fun recognize(pcmFile: File, sampleRate: Int): Result =
        suspendCancellableCoroutine { cont ->
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            val pfd = ParcelFileDescriptor.open(
                pcmFile, ParcelFileDescriptor.MODE_READ_ONLY,
            )
            fun cleanup() {
                runCatching { recognizer.destroy() }
                runCatching { pfd.close() }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, sampleRate)
                putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val text = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    cleanup()
                    if (cont.isActive) {
                        cont.resume(
                            if (text.isNullOrBlank()) Result.Empty else Result.Text(text),
                        )
                    }
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "SpeechRecognizer error=$error")
                    cleanup()
                    if (cont.isActive) {
                        val result = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Result.Empty
                            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> Result.LanguageMissing
                            else -> Result.Failed(error)
                        }
                        cont.resume(result)
                    }
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            cont.invokeOnCancellation { cleanup() }
            runCatching { recognizer.startListening(intent) }
                .onFailure {
                    Log.w(TAG, "startListening falló", it)
                    cleanup()
                    if (cont.isActive) cont.resume(Result.Failed(-1))
                }
        }

    private companion object {
        const val TAG = "SpeechTranscriber"
        /** ~0.9 × 32767: pico objetivo tras normalizar, con algo de headroom. */
        const val TARGET_PEAK = 29491
    }
}
