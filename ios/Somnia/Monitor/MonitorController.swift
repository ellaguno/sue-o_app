import AVFoundation
import Foundation
import Observation
import SwiftData
import UIKit

/// Orquesta una noche de monitoreo: micrófono -> pipeline (en una cola de
/// audio) -> minutos y eventos guardados en SwiftData (en el hilo principal).
/// Los eventos se clasifican y codifican en una cola aparte, de uno en uno.
@MainActor
@Observable
final class MonitorController {

    private(set) var isRunning = false
    private(set) var startedAt: Date?
    private(set) var currentDbfs: Double?
    private(set) var minutesSaved = 0
    private(set) var eventsDetected = 0
    var permissionDenied = false
    var lastError: String?

    @ObservationIgnored private var capture: AudioCapture?
    @ObservationIgnored private var pipeline: AudioPipeline?
    @ObservationIgnored private var aggregator = NoiseAggregator()
    @ObservationIgnored private var session: SleepSession?
    @ObservationIgnored private var context: ModelContext?
    @ObservationIgnored private var pipelineStart = Date()
    @ObservationIgnored private var lastReading = Date()
    @ObservationIgnored private var lastAudible = Date()
    @ObservationIgnored private var watchdog: Timer?
    @ObservationIgnored private let audioQueue = DispatchQueue(label: "somnia.audio", qos: .userInitiated)
    @ObservationIgnored private let eventQueue = DispatchQueue(label: "somnia.events", qos: .utility)
    @ObservationIgnored private let classifier = SoundClassifier()

    static let maxClipsPerNight = 1000
    private static let staleAfter: TimeInterval = 120
    private static let mutedAfter: TimeInterval = 180

    /// Margen del detector (dB sobre el fondo); se lee de Ajustes al iniciar.
    static let marginKey = "openMarginDb"

    // MARK: - Iniciar / detener

    func start(context: ModelContext) async {
        guard !isRunning else { return }
        let granted = await AVAudioApplication.requestRecordPermission()
        guard granted else {
            permissionDenied = true
            return
        }

        UIDevice.current.isBatteryMonitoringEnabled = true
        let now = Date()
        let night = SleepSession(start: now, batteryStart: batteryPercent())
        context.insert(night)
        try? context.save()
        self.context = context
        self.session = night

        aggregator = NoiseAggregator()
        minutesSaved = 0
        eventsDetected = 0
        startedAt = now
        newPipeline()

        let capture = AudioCapture()
        capture.onSamples = { [weak self] samples in
            self?.audioQueue.async { self?.pipeline?.feed(samples) }
        }
        capture.onFailure = { [weak self] error in
            Task { @MainActor in self?.lastError = error.localizedDescription }
        }
        do {
            try capture.start()
        } catch {
            lastError = error.localizedDescription
            context.delete(night)
            try? context.save()
            self.session = nil
            return
        }
        self.capture = capture
        lastReading = Date()
        lastAudible = Date()
        isRunning = true
        startWatchdog()
    }

    func stop() {
        guard isRunning else { return }
        isRunning = false
        watchdog?.invalidate()
        watchdog = nil
        capture?.stop()
        capture = nil

        // Que el audio pendiente y el último evento alcancen a guardarse.
        audioQueue.sync {}
        eventQueue.sync {}
        if let pending = aggregator.flush() { saveMinute(pending) }
        session?.end = Date()
        session?.batteryEnd = batteryPercent()
        try? context?.save()
        session = nil
        pipeline = nil
        startedAt = nil
        currentDbfs = nil
    }

    /// Cierra noches que quedaron abiertas porque iOS terminó la app a media
    /// grabación: el fin es el último minuto realmente grabado.
    static func closeDanglingSessions(in context: ModelContext) {
        let open = (try? context.fetch(FetchDescriptor<SleepSession>(predicate: #Predicate { $0.end == nil }))) ?? []
        for night in open {
            let last = night.minutes.map(\.minuteIndex).max()
            night.end = night.start.addingTimeInterval(TimeInterval(((last ?? -1) + 1) * 60))
        }
        if !open.isEmpty { try? context.save() }
    }

    // MARK: - Pipeline

    private func newPipeline() {
        let margin = UserDefaults.standard.object(forKey: Self.marginKey) as? Double ?? 12
        var config = EventDetector.Config()
        config.openMarginDb = margin
        let base = Date()
        pipelineStart = base
        // Los eventos se guardan en ESTA noche aunque se detenga antes de que terminen.
        guard let night = session, let context else { return }
        pipeline = AudioPipeline(
            detector: EventDetector(config: config),
            onSecond: { [weak self] db in
                Task { @MainActor in self?.onSecond(db) }
            },
            onEvent: { [weak self] capture in
                self?.eventQueue.async { self?.processEvent(capture, base: base, night: night, context: context) }
            }
        )
    }

    private func onSecond(_ dbfs: Double) {
        lastReading = Date()
        if dbfs > DbMeter.floorDbfs + 1 { lastAudible = lastReading }
        currentDbfs = dbfs
        if let minute = aggregator.add(dbfs) { saveMinute(minute) }
    }

    private func saveMinute(_ stats: MinuteStats) {
        guard let session, let context else { return }
        let m = NoiseMinute(stats: stats)
        context.insert(m)
        m.session = session
        minutesSaved = stats.minuteIndex + 1
        try? context.save()
    }

    /// En la cola de eventos: clasificar y guardar el clip; luego registrar en la BD.
    nonisolated private func processEvent(_ capture: EventCapture, base: Date,
                                          night session: SleepSession, context: ModelContext) {
        let start = base.addingTimeInterval(TimeInterval(capture.startOffsetMs) / 1000)
        let end = base.addingTimeInterval(TimeInterval(capture.endOffsetMs) / 1000)
        let result = classifier.classify(capture.pcm)
        Task { @MainActor in
            var clip: String?
            if session.events.filter({ $0.clipFile != nil }).count < Self.maxClipsPerNight {
                let name = "evt_\(Int(start.timeIntervalSince(session.start) * 1000))"
                let id = session.id
                clip = await Task.detached { ClipStore.write(capture.pcm, session: id, name: name) }.value
            }
            let event = SoundEventRecord(
                start: start, end: end,
                dbPeak: capture.peakDbfs, dbAvg: capture.avgDbfs,
                category: result?.category.rawValue ?? SomniaCategory.other.rawValue,
                confidence: result?.score, rawLabel: result?.label, clipFile: clip
            )
            context.insert(event)
            event.session = session
            if self.session === session { self.eventsDetected += 1 }
            try? context.save()
        }
    }

    // MARK: - Watchdog

    /// Si dejan de llegar lecturas, o solo llegan ceros (micrófono bloqueado),
    /// reabre la captura y empieza un pipeline nuevo alineado con el reloj.
    private func startWatchdog() {
        watchdog = Timer.scheduledTimer(withTimeInterval: 60, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.checkHealth() }
        }
    }

    private func checkHealth() {
        guard isRunning, let session else { return }
        let now = Date()
        let stale = now.timeIntervalSince(lastReading) > Self.staleAfter
        let muted = now.timeIntervalSince(lastAudible) > Self.mutedAfter
        guard stale || muted else { return }
        audioQueue.sync {}
        let minute = Int(now.timeIntervalSince(session.start) / 60)
        if let pending = aggregator.resume(at: minute) { saveMinute(pending) }
        newPipeline()
        lastReading = now
        lastAudible = now
        capture?.restart()
    }

    private func batteryPercent() -> Int? {
        let level = UIDevice.current.batteryLevel
        return level >= 0 ? Int((level * 100).rounded()) : nil
    }
}
