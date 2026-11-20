package com.sesolibre.somnia.calm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.sesolibre.sonidos.motor.Nativo

/**
 * Reproduce una vez el sonido de calma: la receta `assets/sonidos/receta-calma.json`,
 * generada en vivo por el núcleo de SesoLibre Sonidos (diez minutos, entra en 30 s, sale en
 * dos minutos, techo de −20 dBFS). Suena dentro del servicio de monitoreo, que ya está en
 * primer plano: no hay que despertar a otra app.
 *
 * No toca el volumen del sistema ni pide foco de audio; si ya suena otra cosa, no empieza.
 */
class CalmPlayer(private val context: Context) {

    @Volatile
    private var thread: Worker? = null

    val isPlaying: Boolean get() = thread?.isAlive == true

    /**
     * Empieza a sonar si no suena ya nada. [onEnd] se llama desde el hilo de audio al
     * terminar (true = sonó hasta el final o hasta [stop]; false = no pudo sonar).
     * Devuelve false si no empezó.
     */
    fun play(onEnd: (played: Boolean) -> Unit): Boolean {
        if (isPlaying) return false
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.isMusicActive) return false
        thread = Worker(onEnd).also { it.start() }
        return true
    }

    /** Baja el sonido (0,4 s) y termina. */
    fun stop() {
        thread?.requestStop()
    }

    private inner class Worker(private val onEnd: (Boolean) -> Unit) : Thread("somnia-calm") {
        private val lock = Any()
        private var session = 0L
        @Volatile private var alive = true

        fun requestStop() = synchronized(lock) {
            alive = false
            if (session != 0L) Nativo.atenuar(session, true)
        }

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            var track: AudioTrack? = null
            var played = false
            try {
                val recipe = context.assets.open(RECIPE).bufferedReader().use { it.readText() }
                val bank = Nativo.bancoNuevo()
                val created = try {
                    for (file in Nativo.archivos(recipe).split('\n')) {
                        if (file.isEmpty()) continue
                        Nativo.bancoAgregar(bank, file, context.assets.open("$AUDIO_ROOT/$file").use { it.readBytes() })
                    }
                    Nativo.crear(recipe, 0f, -1L, bank)
                } finally {
                    Nativo.bancoLiberar(bank)
                }
                synchronized(lock) {
                    session = created
                    if (!alive) Nativo.atenuar(created, true)
                }
                track = newTrack()
                val block = FloatArray(FRAMES * 2)
                track.play()
                played = true
                while (true) {
                    val state = synchronized(lock) { Nativo.procesar(session, block, FRAMES) }
                    if (state < 0) break
                    track.write(block, 0, block.size, AudioTrack.WRITE_BLOCKING)
                    if (state and (Nativo.TERMINADO or Nativo.EN_SILENCIO) != 0) break
                }
            } catch (t: Throwable) {
                // Incluye UnsatisfiedLinkError: sin la biblioteca nativa la noche sigue igual.
                Log.e(TAG, "el sonido de calma no pudo sonar", t)
                played = false
            } finally {
                runCatching {
                    track?.pause()
                    track?.flush()
                    track?.release()
                }
                synchronized(lock) {
                    val s = session
                    session = 0L
                    if (s != 0L) runCatching { Nativo.liberar(s) }
                }
                runCatching { onEnd(played) }
            }
        }

        private fun newTrack(): AudioTrack {
            val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            return AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(min, SAMPLE_RATE / 5 * 2 * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }
    }

    private companion object {
        const val TAG = "CalmPlayer"
        const val RECIPE = "sonidos/receta-calma.json"
        const val AUDIO_ROOT = "sonidos/audio"
        const val SAMPLE_RATE = 48_000
        const val FRAMES = 1024
    }
}
