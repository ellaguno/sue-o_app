package com.sesolibre.somnia.audio

/**
 * Orquesta el procesamiento del audio en vivo, frame por frame (~100 ms):
 *
 * ```
 * frames PCM ──> dB por frame ──> EventDetector ──> captura de PCM del evento
 *          └──> ring buffer (pre-roll)      └──> lecturas de 1 s para la serie de ruido
 * ```
 *
 * No guarda audio salvo: (a) el ring buffer de pre-roll en RAM y (b) el PCM
 * del evento abierto, que se entrega en [onEventCaptured] y se descarta después.
 * Lógica pura, testeable en JVM con PCM sintético.
 */
class AudioPipeline(
    private val sampleRate: Int = 16_000,
    private val detector: EventDetector = EventDetector(),
    /** Nivel promedio de cada segundo (para la serie por minuto y la UI). */
    private val onSecondReading: (dbfs: Double) -> Unit,
    /** Evento detectado y validado, con su PCM (pre-roll incluido). */
    private val onEventCaptured: (EventCapture) -> Unit,
) {

    data class EventCapture(
        /** PCM del evento, incluyendo [prerollMs] de contexto previo. */
        val pcm: ShortArray,
        /** Inicio del evento (sin pre-roll), en ms desde el arranque del pipeline. */
        val startOffsetMs: Long,
        val endOffsetMs: Long,
        val peakDbfs: Double,
        val avgDbfs: Double,
        val prerollMs: Long,
    )

    private val frameSamples = sampleRate / 10 // 100 ms
    private val prerollSamples = sampleRate    // 1 s de contexto previo
    private val ring = ShortRingBuffer(sampleRate * 2)

    private val frameBuf = ShortArray(frameSamples)
    private var frameFill = 0

    private var secondSumOfSquares = 0.0
    private var secondCount = 0

    private var frameIndex = 0L
    private var eventPcm: MutableList<ShortArray>? = null
    private var eventPrerollSamples = 0

    /** Alimenta muestras crudas del micrófono (cualquier longitud). */
    fun feed(samples: ShortArray, length: Int = samples.size) {
        var offset = 0
        while (offset < length) {
            val toCopy = minOf(frameSamples - frameFill, length - offset)
            System.arraycopy(samples, offset, frameBuf, frameFill, toCopy)
            frameFill += toCopy
            offset += toCopy
            if (frameFill == frameSamples) {
                processFrame(frameBuf)
                frameFill = 0
            }
        }
    }

    private fun processFrame(frame: ShortArray) {
        // Nivel del frame y acumulado del segundo
        var sumSquares = 0.0
        for (s in frame) {
            val d = s.toDouble()
            sumSquares += d * d
        }
        val frameDb = DbMeter.dbfsFromSumOfSquares(sumSquares, frame.size)
        secondSumOfSquares += sumSquares
        secondCount += frame.size
        if (secondCount >= sampleRate) {
            onSecondReading(DbMeter.dbfsFromSumOfSquares(secondSumOfSquares, secondCount))
            secondSumOfSquares = 0.0
            secondCount = 0
        }

        // El evento abierto acumula su PCM ANTES de escribir al ring,
        // para no duplicar este frame en el pre-roll.
        eventPcm?.add(frame.copyOf())

        when (val signal = detector.process(frameDb)) {
            is EventDetector.Signal.Start -> {
                val preroll = ring.snapshotLast(prerollSamples)
                eventPrerollSamples = preroll.size
                eventPcm = mutableListOf(preroll, frame.copyOf())
            }
            is EventDetector.Signal.End -> {
                val chunks = eventPcm
                eventPcm = null
                if (chunks != null) {
                    val total = chunks.sumOf { it.size }
                    val pcm = ShortArray(total)
                    var pos = 0
                    for (c in chunks) {
                        System.arraycopy(c, 0, pcm, pos, c.size)
                        pos += c.size
                    }
                    val stats = signal.stats
                    onEventCaptured(
                        EventCapture(
                            pcm = pcm,
                            startOffsetMs = stats.startFrame * FRAME_MS,
                            endOffsetMs = (stats.endFrame + 1) * FRAME_MS,
                            peakDbfs = stats.peakDbfs,
                            avgDbfs = stats.avgDbfs,
                            prerollMs = eventPrerollSamples * 1000L / sampleRate,
                        )
                    )
                }
            }
            is EventDetector.Signal.Discard -> eventPcm = null
            null -> Unit
        }

        ring.write(frame)
        frameIndex++
    }

    companion object {
        const val FRAME_MS = 100L
    }
}
