import AVFoundation
import Foundation
import SoundAnalysis

/// Captura continua del micrófono, convertida a 16 kHz mono Float. Usa el modo
/// `.measurement` (sin control automático de ganancia) para que los dB sean
/// comparables entre noches. Con el modo de fondo "audio" sigue grabando con
/// la pantalla apagada. Se re-arma sola tras interrupciones (llamadas, Siri).
final class AudioCapture {

    var onSamples: (([Float]) -> Void)?
    var onFailure: ((Error) -> Void)?

    private let engine = AVAudioEngine()
    private var converter: AVAudioConverter?
    private let target = AVAudioFormat(commonFormat: .pcmFormatFloat32,
                                       sampleRate: Double(AudioPipeline.sampleRate),
                                       channels: 1, interleaved: false)!
    private var observers: [NSObjectProtocol] = []
    private(set) var isRunning = false

    func start() throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: [])
        try session.setActive(true)
        try startEngine()
        isRunning = true
        observeInterruptions()
    }

    func stop() {
        isRunning = false
        observers.forEach { NotificationCenter.default.removeObserver($0) }
        observers.removeAll()
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    /// Reabre el micrófono (tras una interrupción o si el watchdog detecta silencio).
    func restart() {
        guard isRunning else { return }
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        do {
            try AVAudioSession.sharedInstance().setActive(true)
            try startEngine()
        } catch {
            onFailure?(error)
        }
    }

    private func startEngine() throws {
        let input = engine.inputNode
        let inFormat = input.outputFormat(forBus: 0)
        converter = AVAudioConverter(from: inFormat, to: target)
        input.installTap(onBus: 0, bufferSize: 4096, format: inFormat) { [weak self] buffer, _ in
            self?.convert(buffer)
        }
        engine.prepare()
        try engine.start()
    }

    private func convert(_ buffer: AVAudioPCMBuffer) {
        guard let converter else { return }
        let ratio = target.sampleRate / buffer.format.sampleRate
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio) + 64
        guard let out = AVAudioPCMBuffer(pcmFormat: target, frameCapacity: capacity) else { return }
        var delivered = false
        var error: NSError?
        converter.convert(to: out, error: &error) { _, status in
            if delivered {
                status.pointee = .noDataNow
                return nil
            }
            delivered = true
            status.pointee = .haveData
            return buffer
        }
        guard error == nil, let channel = out.floatChannelData, out.frameLength > 0 else { return }
        onSamples?(Array(UnsafeBufferPointer(start: channel[0], count: Int(out.frameLength))))
    }

    private func observeInterruptions() {
        let center = NotificationCenter.default
        observers.append(center.addObserver(forName: AVAudioSession.interruptionNotification,
                                            object: nil, queue: .main) { [weak self] note in
            guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  AVAudioSession.InterruptionType(rawValue: raw) == .ended else { return }
            self?.restart()
        })
        observers.append(center.addObserver(forName: AVAudioSession.mediaServicesWereResetNotification,
                                            object: nil, queue: .main) { [weak self] _ in
            self?.restart()
        })
        observers.append(center.addObserver(forName: .AVAudioEngineConfigurationChange,
                                            object: engine, queue: .main) { [weak self] _ in
            self?.restart()
        })
    }
}

/// Clasificación on-device con el clasificador de sonidos integrado de Apple
/// (SoundAnalysis, ~300 clases). Toma el máximo por clase entre ventanas y
/// decide con ScoreDecoder. Nada sale del teléfono.
final class SoundClassifier {

    struct Result {
        let label: String
        let score: Double
        let category: SomniaCategory
    }

    private final class Collector: NSObject, SNResultsObserving {
        var maxScores: [String: Double] = [:]
        func request(_ request: SNRequest, didProduce result: SNResult) {
            guard let r = result as? SNClassificationResult else { return }
            for c in r.classifications {
                maxScores[c.identifier] = max(maxScores[c.identifier] ?? 0, c.confidence)
            }
        }
    }

    func classify(_ pcm: [Float], sampleRate: Double = Double(AudioPipeline.sampleRate)) -> Result? {
        guard !pcm.isEmpty,
              let request = try? SNClassifySoundRequest(classifierIdentifier: .version1),
              let format = AVAudioFormat(standardFormatWithSampleRate: sampleRate, channels: 1)
        else { return nil }
        request.overlapFactor = 0.5

        // Un evento más corto que la ventana del modelo se rellena con silencio.
        var samples = pcm
        let minFrames = Int(request.windowDuration.seconds * sampleRate) + 1
        if samples.count < minFrames {
            samples += [Float](repeating: 0, count: minFrames - samples.count)
        }
        guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(samples.count)),
              let channel = buffer.floatChannelData else { return nil }
        buffer.frameLength = AVAudioFrameCount(samples.count)
        samples.withUnsafeBufferPointer { channel[0].update(from: $0.baseAddress!, count: samples.count) }

        let analyzer = SNAudioStreamAnalyzer(format: format)
        let collector = Collector()
        do {
            try analyzer.add(request, withObserver: collector)
        } catch {
            return nil
        }
        analyzer.analyze(buffer, atAudioFramePosition: 0)
        analyzer.completeAnalysis()

        let d = ScoreDecoder.decide(collector.maxScores)
        return Result(label: d.label, score: d.score, category: d.category)
    }
}

/// Archivos de clips: `Application Support/clips/<sesión>/<evento>.m4a`.
/// La carpeta se excluye de los respaldos de iCloud: el audio no sale del teléfono.
enum ClipStore {

    static var root: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        var dir = base.appendingPathComponent("clips", isDirectory: true)
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            try? dir.setResourceValues(values)
        }
        return dir
    }

    static func url(for file: String) -> URL { root.appendingPathComponent(file) }

    static func deleteSession(_ id: UUID) {
        try? FileManager.default.removeItem(at: root.appendingPathComponent(id.uuidString, isDirectory: true))
    }

    /// Pico objetivo de los clips guardados. Se graban muy bajos (~-45 dBFS);
    /// se normalizan al guardar para que se escuchen bien al reproducirlos o
    /// enviarlos. El nivel real queda en `dbPeak` del evento.
    static let targetPeak: Float = 0.7
    static let maxGain: Float = 100 // +40 dB

    /// Guarda el PCM como AAC/M4A (16 kHz mono). Devuelve el nombre relativo.
    static func write(_ pcm: [Float], session: UUID, name: String) -> String? {
        let relative = "\(session.uuidString)/\(name).m4a"
        let url = url(for: relative)
        try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(),
                                                 withIntermediateDirectories: true)
        let peak = pcm.map(abs).max() ?? 0
        let gain = peak > 0 ? min(maxGain, targetPeak / peak) : 1
        let settings: [String: Any] = [
            AVFormatIDKey: kAudioFormatMPEG4AAC,
            AVSampleRateKey: AudioPipeline.sampleRate,
            AVNumberOfChannelsKey: 1,
            AVEncoderBitRateKey: 32_000,
        ]
        do {
            let file = try AVAudioFile(forWriting: url, settings: settings,
                                       commonFormat: .pcmFormatFloat32, interleaved: false)
            guard let buffer = AVAudioPCMBuffer(pcmFormat: file.processingFormat,
                                                frameCapacity: AVAudioFrameCount(pcm.count)),
                  let channel = buffer.floatChannelData else { return nil }
            buffer.frameLength = AVAudioFrameCount(pcm.count)
            for i in 0..<pcm.count { channel[0][i] = pcm[i] * gain }
            try file.write(from: buffer)
            return relative
        } catch {
            try? FileManager.default.removeItem(at: url)
            return nil
        }
    }
}
