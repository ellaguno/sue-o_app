import Foundation
import SwiftData

/// Una noche (o siesta) de monitoreo.
@Model
final class SleepSession {
    @Attribute(.unique) var id: UUID
    var start: Date
    var end: Date?
    var batteryStart: Int?
    var batteryEnd: Int?
    var calibrationOffset: Double

    @Relationship(deleteRule: .cascade, inverse: \NoiseMinute.session)
    var minutes: [NoiseMinute] = []

    @Relationship(deleteRule: .cascade, inverse: \SoundEventRecord.session)
    var events: [SoundEventRecord] = []

    init(start: Date, batteryStart: Int?) {
        self.id = UUID()
        self.start = start
        self.batteryStart = batteryStart
        self.calibrationOffset = DbMeter.calibrationOffset
    }

    var durationMinutes: Int? {
        end.map { Int($0.timeIntervalSince(start) / 60) }
    }

    var sortedMinutes: [NoiseMinute] { minutes.sorted { $0.minuteIndex < $1.minuteIndex } }
    var sortedEvents: [SoundEventRecord] { events.sorted { $0.start < $1.start } }

    var summary: NightSummary { NightSummary.of(events.map(\.snapshot)) }
}

/// Serie de ruido: dBFS por minuto (sin audio).
@Model
final class NoiseMinute {
    var minuteIndex: Int
    var dbMin: Double
    var dbAvg: Double
    var dbMax: Double
    var session: SleepSession?

    init(stats: MinuteStats) {
        minuteIndex = stats.minuteIndex
        dbMin = stats.dbMin
        dbAvg = stats.dbAvg
        dbMax = stats.dbMax
    }
}

/// Un evento de sonido detectado. El clip vive como archivo (ver ClipStore).
@Model
final class SoundEventRecord {
    @Attribute(.unique) var id: UUID
    var start: Date
    var end: Date
    var dbPeak: Double
    var dbAvg: Double
    var category: String
    var confidence: Double?
    var rawLabel: String?
    var clipFile: String?
    /// Reetiquetado manual; tiene prioridad sobre `category`.
    var manualLabel: String?
    var session: SleepSession?

    init(start: Date, end: Date, dbPeak: Double, dbAvg: Double, category: String,
         confidence: Double?, rawLabel: String?, clipFile: String?) {
        self.id = UUID()
        self.start = start
        self.end = end
        self.dbPeak = dbPeak
        self.dbAvg = dbAvg
        self.category = category
        self.confidence = confidence
        self.rawLabel = rawLabel
        self.clipFile = clipFile
    }

    var effectiveCategory: SomniaCategory {
        SomniaCategory(rawValue: manualLabel ?? category) ?? .other
    }

    var duration: TimeInterval { end.timeIntervalSince(start) }

    var snapshot: EventSnapshot {
        EventSnapshot(start: start, end: end, dbPeak: dbPeak, category: effectiveCategory)
    }
}
