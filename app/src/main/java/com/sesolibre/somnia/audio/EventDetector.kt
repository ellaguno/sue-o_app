package com.sesolibre.somnia.audio

/**
 * Detector de eventos de sonido sobre niveles de dBFS por frame (~100 ms).
 *
 * Mantiene una estimación del ruido de fondo (media móvil exponencial que
 * solo se actualiza en silencio) y abre un evento cuando un frame supera el
 * fondo por [Config.openMarginDb]. Cierra con histéresis: el nivel debe caer
 * por debajo de fondo + [Config.closeMarginDb] durante [Config.holdFrames]
 * frames seguidos. Eventos más cortos que [Config.minEventFrames] se
 * descartan; al llegar a [Config.maxEventFrames] se fuerza el cierre.
 *
 * Lógica pura, sin dependencias de Android.
 */
class EventDetector(private val config: Config = Config()) {

    data class Config(
        val openMarginDb: Double = 12.0,
        val closeMarginDb: Double = 6.0,
        val holdFrames: Int = 30,        // 3 s de silencio para cerrar
        val minEventFrames: Int = 5,     // eventos < 0.5 s se descartan
        val maxEventFrames: Int = 600,   // 60 s: cierre forzado
        val floorInitDbfs: Double = -60.0,
        val floorAlpha: Double = 0.05,   // suavizado del fondo (~2 s a 100 ms/frame)
        /**
         * Adaptación del fondo DURANTE un evento, mucho más lenta. Sin esto,
         * un cambio sostenido de ambiente (ej. encender un ventilador) dejaría
         * al detector disparado indefinidamente.
         */
        val inEventFloorAlpha: Double = 0.005,
        val floorMinDbfs: Double = -90.0,
        val floorMaxDbfs: Double = -20.0,
    )

    /** Estadísticas del evento cerrado, en frames relativos al inicio del monitoreo. */
    data class EventStats(
        val startFrame: Int,
        val endFrame: Int,
        val peakDbfs: Double,
        val avgDbfs: Double,
    ) {
        val frameCount: Int get() = endFrame - startFrame + 1
    }

    sealed interface Signal {
        /** Se abrió un evento en este frame (capturar pre-roll ahora). */
        data object Start : Signal

        /** El evento terminó y es válido. */
        data class End(val stats: EventStats) : Signal

        /** El evento terminó pero era demasiado corto: descartar lo capturado. */
        data object Discard : Signal
    }

    var noiseFloorDbfs: Double = config.floorInitDbfs
        private set

    private var inEvent = false
    private var frameIndex = -1
    private var eventStartFrame = 0
    private var silentFrames = 0
    private var peak = 0.0
    private var sum = 0.0
    private var count = 0

    /** Procesa el nivel de un frame; devuelve una señal si algo cambió. */
    fun process(dbfs: Double): Signal? {
        frameIndex++
        return if (inEvent) inEventFrame(dbfs) else idleFrame(dbfs)
    }

    private fun idleFrame(dbfs: Double): Signal? {
        if (dbfs > noiseFloorDbfs + config.openMarginDb) {
            inEvent = true
            eventStartFrame = frameIndex
            silentFrames = 0
            peak = dbfs
            sum = dbfs
            count = 1
            return Signal.Start
        }
        noiseFloorDbfs = (noiseFloorDbfs * (1 - config.floorAlpha) + dbfs * config.floorAlpha)
            .coerceIn(config.floorMinDbfs, config.floorMaxDbfs)
        return null
    }

    private fun inEventFrame(dbfs: Double): Signal? {
        peak = maxOf(peak, dbfs)
        sum += dbfs
        count++
        noiseFloorDbfs = (noiseFloorDbfs * (1 - config.inEventFloorAlpha) + dbfs * config.inEventFloorAlpha)
            .coerceIn(config.floorMinDbfs, config.floorMaxDbfs)
        silentFrames = if (dbfs < noiseFloorDbfs + config.closeMarginDb) silentFrames + 1 else 0

        val forcedClose = count >= config.maxEventFrames
        if (silentFrames < config.holdFrames && !forcedClose) return null

        inEvent = false
        // Sin la cola de silencio, ¿el evento tuvo contenido suficiente?
        val activeFrames = count - silentFrames
        if (activeFrames < config.minEventFrames) return Signal.Discard
        return Signal.End(
            EventStats(
                startFrame = eventStartFrame,
                endFrame = frameIndex,
                peakDbfs = peak,
                avgDbfs = sum / count,
            )
        )
    }
}
