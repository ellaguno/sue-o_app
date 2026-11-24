package com.sesolibre.somnia.sonidos

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import com.sesolibre.sonidos.motor.Nativo

/**
 * Toca la receta de SesoLibre Sonidos que acompaña los ronquidos
 * (`sonar.calma-ronquido`: ruido marrón muy bajo y cuerdas graves, 10 min).
 * El sonido se genera en el teléfono con el núcleo de Sonidos (libsonidos.so):
 * no hace falta tener instalada la app Sonidos, y funciona a las 3 a.m.
 * porque vive dentro del servicio en primer plano de Somnia.
 *
 * Un solo sonido a la vez. Para cortar antes de tiempo, [stop] baja el
 * volumen con suavidad (sin chasquido) y el hilo sale en cuanto calla.
 */
class NightSoundPlayer(private val context: Context) {

    /** Por qué terminó: para el registro de la noche. */
    enum class EndReason { FINISHED, STOPPED, FAILED }

    private var thread: AudioThread? = null

    val isPlaying: Boolean get() = thread?.isAlive == true

    /**
     * Empieza a sonar. [onEnded] llega desde el hilo de audio con la hora de
     * fin; también si no pudo empezar (sin librería, sin salida de audio…).
     */
    @Synchronized
    fun start(onEnded: (endEpochMs: Long, reason: EndReason) -> Unit): Boolean {
        if (isPlaying) return false
        thread = AudioThread(onEnded).also { it.start() }
        return true
    }

    /** Baja el volumen hasta el silencio y termina. No bloquea. */
    @Synchronized
    fun stop() {
        thread?.requestStop()
    }

    /** Detiene y espera a que el hilo suelte el audio (al cerrar la noche). */
    fun stopAndWait(timeoutMs: Long = 3_000) {
        val t = synchronized(this) { thread } ?: return
        t.requestStop()
        t.join(timeoutMs)
    }

    private inner class AudioThread(
        private val onEnded: (Long, EndReason) -> Unit,
    ) : Thread("somnia-sonido") {
        private val lock = Object()
        private var session = 0L
        @Volatile private var alive = true

        fun requestStop() = synchronized(lock) {
            alive = false
            if (session != 0L) Nativo.atenuar(session, true)
        }

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            var track: AudioTrack? = null
            var reason = EndReason.FAILED
            try {
                val created = createSession()
                synchronized(lock) {
                    session = created
                    if (!alive) Nativo.atenuar(created, true)
                }
                track = createTrack()
                val block = FloatArray(FRAMES * 2)
                track.play()
                while (true) {
                    val state = synchronized(lock) { Nativo.procesar(session, block, FRAMES) }
                    if (state < 0) throw IllegalStateException("el núcleo de Sonidos falló al procesar")
                    track.write(block, 0, block.size, AudioTrack.WRITE_BLOCKING)
                    if (state and Nativo.TERMINADO != 0) {
                        reason = EndReason.FINISHED
                        break
                    }
                    if (state and Nativo.EN_SILENCIO != 0 && !alive) {
                        reason = EndReason.STOPPED
                        break
                    }
                }
            } catch (t: Throwable) {
                // Sin librería nativa, receta inválida o sin salida de audio: la
                // noche sigue grabando, solo que sin sonido.
                Log.e(TAG, "no se pudo tocar el sonido", t)
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
                onEnded(System.currentTimeMillis(), reason)
            }
        }

        private fun createSession(): Long {
            val recipe = context.assets.open("$ASSETS/$RECIPE_ID.json").bufferedReader().use { it.readText() }
            val bank = Nativo.bancoNuevo()
            try {
                for (file in Nativo.archivos(recipe).split('\n')) {
                    if (file.isEmpty()) continue
                    val bytes = context.assets.open("$ASSETS/audio/$file").use { it.readBytes() }
                    Nativo.bancoAgregar(bank, file, bytes)
                }
                // Duración y semilla de la receta (10 min, siempre igual).
                return Nativo.crear(recipe, 0f, -1L, bank)
            } finally {
                Nativo.bancoLiberar(bank)
            }
        }

        private fun createTrack(): AudioTrack {
            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            val twoHundredMs = SAMPLE_RATE / 5 * 2 * 4
            return AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(min, twoHundredMs))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }
    }

    companion object {
        private const val TAG = "NightSoundPlayer"
        const val RECIPE_ID = "sonar.calma-ronquido"
        private const val ASSETS = "sonidos"
        private const val SAMPLE_RATE = 48_000
        private const val FRAMES = 1024
    }
}
