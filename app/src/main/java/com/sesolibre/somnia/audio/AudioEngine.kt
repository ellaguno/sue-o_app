package com.sesolibre.somnia.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * Captura continua del micrófono a 16 kHz mono PCM 16-bit y entrega las
 * muestras crudas a [onSamples] (normalmente un [AudioPipeline]). Corre en su
 * propio hilo; diseñado para vivir dentro del servicio en primer plano
 * durante toda la noche.
 *
 * Usa la fuente UNPROCESSED cuando el dispositivo la soporta (sin AGC ni
 * supresión de ruido, necesario para que los dB sean comparables); si no,
 * VOICE_RECOGNITION, que en la mayoría de dispositivos desactiva el AGC.
 *
 * La captura se **auto-repara**: si el micrófono se cae a media noche (otra app
 * lo toma, el HAL de audio se reinicia, `read()` devuelve un código de error),
 * se cierra el [AudioRecord] y se abre otro. Solo tras
 * [MAX_CONSECUTIVE_FAILURES] intentos seguidos sin lograr grabar se avisa por
 * [onUnrecoverableError] para que el servicio cierre la noche ordenadamente en
 * vez de quedarse vivo sin grabar nada.
 */
class AudioEngine(
    private val onSamples: (samples: ShortArray, length: Int) -> Unit,
    /** Se invoca desde el hilo de audio cuando la captura no pudo recuperarse. */
    private val onUnrecoverableError: () -> Unit = {},
) {

    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun start(context: Context) {
        if (running) return
        running = true
        thread = Thread({ captureLoop(context) }, "somnia-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null
    }

    /** Abre el micrófono y lo vuelve a abrir mientras la captura siga fallando. */
    private fun captureLoop(context: Context) {
        var failures = 0
        while (running) {
            val record = openRecord(context)
            if (record == null) {
                failures++
            } else {
                val capturedMs = pump(record)
                if (!running) return
                // Una racha larga de audio cuenta como recuperación completa.
                failures = if (capturedMs >= HEALTHY_RUN_MS) 0 else failures + 1
                Log.w(TAG, "la captura se interrumpió tras ${capturedMs / 1000} s")
            }
            if (!running) return
            if (failures >= MAX_CONSECUTIVE_FAILURES) {
                Log.e(TAG, "captura irrecuperable tras $failures intentos")
                running = false
                onUnrecoverableError()
                return
            }
            sleepQuietly(RETRY_DELAY_MS)
        }
    }

    @SuppressLint("MissingPermission") // el servicio verifica RECORD_AUDIO antes de iniciar
    private fun openRecord(context: Context): AudioRecord? {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val unprocessedSupported =
                audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val source = if (unprocessedSupported) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }

            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferSize = maxOf(minBuffer, SAMPLE_RATE * 2) // >= 1 s de audio

            val record = AudioRecord(
                source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord no inicializado (source=$source)")
                release(record)
                return null
            }
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord no arrancó (source=$source)")
                release(record)
                return null
            }
            return record
        } catch (t: Throwable) {
            Log.e(TAG, "no se pudo abrir el micrófono", t)
            return null
        }
    }

    /**
     * Lee hasta que se pide parar o la captura falla. Devuelve los milisegundos
     * de audio entregados, que sirven para saber si la sesión fue sana.
     */
    private fun pump(record: AudioRecord): Long {
        val chunk = ShortArray(CHUNK_SAMPLES)
        var samples = 0L
        try {
            while (running) {
                val read = record.read(chunk, 0, chunk.size)
                if (read < 0) {
                    // ERROR_DEAD_OBJECT, ERROR_INVALID_OPERATION…: hay que reabrir.
                    Log.w(TAG, "AudioRecord.read devolvió $read")
                    break
                }
                if (read == 0) {
                    sleepQuietly(EMPTY_READ_DELAY_MS)
                    continue
                }
                samples += read
                try {
                    onSamples(chunk, read)
                } catch (t: Throwable) {
                    // Un fallo procesando un frame no puede tumbar la noche entera:
                    // sin este catch, la excepción mataría el hilo (y el proceso).
                    Log.e(TAG, "fallo procesando audio", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "fallo leyendo del micrófono", t)
        } finally {
            release(record)
        }
        return samples * 1000L / SAMPLE_RATE
    }

    private fun release(record: AudioRecord) {
        runCatching { if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop() }
        runCatching { record.release() }
    }

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            running = false
        }
    }

    companion object {
        private const val TAG = "AudioEngine"
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_SAMPLES = 1_600 // 100 ms
        private const val JOIN_TIMEOUT_MS = 2_000L
        private const val RETRY_DELAY_MS = 2_000L
        private const val EMPTY_READ_DELAY_MS = 20L
        /** Grabar esto seguido borra la cuenta de fallos: el micrófono se recuperó. */
        private const val HEALTHY_RUN_MS = 60_000L
        private const val MAX_CONSECUTIVE_FAILURES = 5
    }
}
