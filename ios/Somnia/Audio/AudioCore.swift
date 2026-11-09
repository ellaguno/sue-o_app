import Foundation

// Lógica pura de audio (sin AVFoundation), portada de la app Android y
// probada en SomniaTests. Las muestras son Float en [-1, 1] a 16 kHz mono.

/// Nivel de sonido en dBFS (siempre <= 0) y su conversión aproximada a dB SPL.
enum DbMeter {
    static let floorDbfs = -96.0
    /// Offset dBFS -> dB SPL aproximado (micrófono de teléfono sin calibrar).
    static let calibrationOffset = 90.0

    static func dbfs(sumOfSquares: Double, count: Int) -> Double {
        guard count > 0 else { return floorDbfs }
        let rms = (sumOfSquares / Double(count)).squareRoot()
        guard rms > 1e-9 else { return floorDbfs }
        return min(0, max(floorDbfs, 20 * log10(rms)))
    }
}

/// Detector de eventos con histéresis sobre el nivel de cada frame (~100 ms).
/// Mantiene una estimación del ruido de fondo y abre un evento cuando un frame
/// supera el fondo por `openMarginDb`; lo cierra tras `holdFrames` frames bajo
/// fondo + `closeMarginDb`. Igual que EventDetector.kt.
final class EventDetector {

    struct Config {
        var openMarginDb = 12.0
        var closeMarginDb = 6.0
        var holdFrames = 30        // 3 s de silencio para cerrar
        var minEventFrames = 5     // < 0.5 s se descarta
        var maxEventFrames = 600   // 60 s: cierre forzado
        var floorInitDbfs = -60.0
        var floorAlpha = 0.05
        var inEventFloorAlpha = 0.005
        var floorMinDbfs = -90.0
        var floorMaxDbfs = -20.0
    }

    struct EventStats: Equatable {
        let startFrame: Int
        let endFrame: Int
        let peakDbfs: Double
        let avgDbfs: Double
    }

    enum Signal: Equatable {
        case start
        case end(EventStats)
        case discard
    }

    private let config: Config
    private(set) var noiseFloorDbfs: Double
    private var inEvent = false
    private var frameIndex = -1
    private var eventStartFrame = 0
    private var silentFrames = 0
    private var peak = 0.0
    private var sum = 0.0
    private var count = 0

    init(config: Config = Config()) {
        self.config = config
        self.noiseFloorDbfs = config.floorInitDbfs
    }

    func process(_ dbfs: Double) -> Signal? {
        frameIndex += 1
        return inEvent ? inEventFrame(dbfs) : idleFrame(dbfs)
    }

    private func clampFloor(_ v: Double) -> Double {
        min(config.floorMaxDbfs, max(config.floorMinDbfs, v))
    }

    private func idleFrame(_ dbfs: Double) -> Signal? {
        if dbfs > noiseFloorDbfs + config.openMarginDb {
            inEvent = true
            eventStartFrame = frameIndex
            silentFrames = 0
            peak = dbfs
            sum = dbfs
            count = 1
            return .start
        }
        noiseFloorDbfs = clampFloor(noiseFloorDbfs * (1 - config.floorAlpha) + dbfs * config.floorAlpha)
        return nil
    }

    private func inEventFrame(_ dbfs: Double) -> Signal? {
        peak = max(peak, dbfs)
        sum += dbfs
        count += 1
        noiseFloorDbfs = clampFloor(noiseFloorDbfs * (1 - config.inEventFloorAlpha) + dbfs * config.inEventFloorAlpha)
        silentFrames = dbfs < noiseFloorDbfs + config.closeMarginDb ? silentFrames + 1 : 0

        let forcedClose = count >= config.maxEventFrames
        if silentFrames < config.holdFrames && !forcedClose { return nil }

        inEvent = false
        if count - silentFrames < config.minEventFrames { return .discard }
        return .end(EventStats(startFrame: eventStartFrame, endFrame: frameIndex,
                               peakDbfs: peak, avgDbfs: sum / Double(count)))
    }
}

/// Estadísticas de un minuto de lecturas de 1 s.
struct MinuteStats: Equatable {
    let minuteIndex: Int
    let dbMin: Double
    let dbAvg: Double
    let dbMax: Double
}

/// Agrega lecturas de 1 s en minutos. Igual que NoiseAggregator.kt.
final class NoiseAggregator {
    private var readings: [Double] = []
    private var minuteIndex = 0
    private let perMinute: Int

    init(readingsPerMinute: Int = 60) { perMinute = readingsPerMinute }

    func add(_ db: Double) -> MinuteStats? {
        readings.append(db)
        return readings.count >= perMinute ? flush() : nil
    }

    /// Tras un hueco de captura, continúa en el minuto del tiempo real.
    func resume(at minute: Int) -> MinuteStats? {
        let pending = flush()
        if minute > minuteIndex { minuteIndex = minute }
        return pending
    }

    func flush() -> MinuteStats? {
        guard !readings.isEmpty else { return nil }
        let stats = MinuteStats(minuteIndex: minuteIndex,
                                dbMin: readings.min()!,
                                dbAvg: readings.reduce(0, +) / Double(readings.count),
                                dbMax: readings.max()!)
        readings.removeAll(keepingCapacity: true)
        minuteIndex += 1
        return stats
    }
}

/// Buffer circular de muestras para el pre-roll de cada evento.
struct SampleRing {
    private var buffer: [Float]
    private var write = 0
    private var filled = 0

    init(capacity: Int) { buffer = [Float](repeating: 0, count: capacity) }

    mutating func append(_ samples: ArraySlice<Float>) {
        for s in samples {
            buffer[write] = s
            write = (write + 1) % buffer.count
            filled = min(filled + 1, buffer.count)
        }
    }

    /// Las últimas `n` muestras, en orden.
    func last(_ n: Int) -> [Float] {
        let count = min(n, filled)
        var out = [Float]()
        out.reserveCapacity(count)
        var idx = (write - count + buffer.count) % buffer.count
        for _ in 0..<count {
            out.append(buffer[idx])
            idx = (idx + 1) % buffer.count
        }
        return out
    }
}

/// Evento detectado con su audio (pre-roll incluido).
struct EventCapture {
    let pcm: [Float]
    /// Inicio del evento (sin pre-roll), en ms desde que arrancó el pipeline.
    let startOffsetMs: Int
    let endOffsetMs: Int
    let peakDbfs: Double
    let avgDbfs: Double
}

/// Frames de 100 ms -> dB -> detector -> captura del PCM de cada evento, más
/// una lectura por segundo para la serie de ruido. Igual que AudioPipeline.kt.
final class AudioPipeline {
    static let sampleRate = 16_000
    static let frameMs = 100

    private let detector: EventDetector
    private let onSecond: (Double) -> Void
    private let onEvent: (EventCapture) -> Void

    private let frameSamples = AudioPipeline.sampleRate / 10
    private var frame: [Float] = []
    private var ring = SampleRing(capacity: AudioPipeline.sampleRate * 2)
    private var secondSum = 0.0
    private var secondCount = 0
    private var eventPcm: [Float]?

    init(detector: EventDetector, onSecond: @escaping (Double) -> Void, onEvent: @escaping (EventCapture) -> Void) {
        self.detector = detector
        self.onSecond = onSecond
        self.onEvent = onEvent
        frame.reserveCapacity(frameSamples)
    }

    func feed(_ samples: [Float]) {
        for s in samples {
            frame.append(s)
            if frame.count == frameSamples {
                processFrame()
                frame.removeAll(keepingCapacity: true)
            }
        }
    }

    private func processFrame() {
        var sumSquares = 0.0
        for s in frame { sumSquares += Double(s) * Double(s) }
        let frameDb = DbMeter.dbfs(sumOfSquares: sumSquares, count: frame.count)
        secondSum += sumSquares
        secondCount += frame.count
        if secondCount >= AudioPipeline.sampleRate {
            onSecond(DbMeter.dbfs(sumOfSquares: secondSum, count: secondCount))
            secondSum = 0
            secondCount = 0
        }

        // El evento abierto acumula este frame ANTES de escribirlo al ring.
        eventPcm?.append(contentsOf: frame)

        switch detector.process(frameDb) {
        case .start:
            eventPcm = ring.last(AudioPipeline.sampleRate) + frame // 1 s de pre-roll
        case .end(let stats):
            if let pcm = eventPcm {
                onEvent(EventCapture(pcm: pcm,
                                     startOffsetMs: stats.startFrame * AudioPipeline.frameMs,
                                     endOffsetMs: (stats.endFrame + 1) * AudioPipeline.frameMs,
                                     peakDbfs: stats.peakDbfs,
                                     avgDbfs: stats.avgDbfs))
            }
            eventPcm = nil
        case .discard:
            eventPcm = nil
        case nil:
            break
        }
        ring.append(frame[...])
    }
}
