package com.sesolibre.somnia.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

/**
 * Captura continua del micrófono a 16 kHz mono PCM 16-bit y emite una lectura
 * de dBFS por segundo. Corre en su propio hilo; diseñado para vivir dentro del
 * servicio en primer plano durante toda la noche.
 *
 * Usa la fuente UNPROCESSED cuando el dispositivo la soporta (sin AGC ni
 * supresión de ruido, necesario para que los dB sean comparables); si no,
 * VOICE_RECOGNITION, que en la mayoría de dispositivos desactiva el AGC.
 */
class AudioEngine(private val onSecondReading: (dbfs: Double) -> Unit) {

    @Volatile
    private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // el servicio verifica RECORD_AUDIO antes de iniciar
    fun start(context: Context) {
        if (running) return
        running = true

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
            record.release()
            running = false
            return
        }

        thread = Thread({
            val chunk = ShortArray(CHUNK_SAMPLES)
            var sumOfSquares = 0.0
            var count = 0
            record.startRecording()
            try {
                while (running) {
                    val read = record.read(chunk, 0, chunk.size)
                    if (read <= 0) continue
                    for (i in 0 until read) {
                        val s = chunk[i].toDouble()
                        sumOfSquares += s * s
                    }
                    count += read
                    if (count >= SAMPLE_RATE) { // ventana de 1 segundo
                        onSecondReading(DbMeter.dbfsFromSumOfSquares(sumOfSquares, count))
                        sumOfSquares = 0.0
                        count = 0
                    }
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }, "somnia-audio").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        thread?.join(2000)
        thread = null
    }

    companion object {
        private const val TAG = "AudioEngine"
        const val SAMPLE_RATE = 16_000
        private const val CHUNK_SAMPLES = 1_600 // 100 ms
    }
}
