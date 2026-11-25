import Foundation
import SwiftData

/// Modo demostración para las capturas del App Store (workflow
/// ios-screenshots.yml). Solo se activa con el argumento de arranque
/// `-SomniaDemo`, que únicamente puede pasar `xcrun simctl launch`: la app
/// usa entonces una base de datos EN MEMORIA con noches de ejemplo (nunca toca
/// los datos reales) y abre la pantalla indicada con `-SomniaShot <pantalla>`.
enum DemoMode {
    static let isActive = ProcessInfo.processInfo.arguments.contains("-SomniaDemo")

    /// home (por defecto) | night | trends | welcome | settings
    static var shot: String {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: "-SomniaShot"), i + 1 < args.count else { return "home" }
        return args[i + 1]
    }

    static func container() throws -> ModelContainer {
        try ModelContainer(
            for: SleepSession.self, NoiseMinute.self, SoundEventRecord.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true)
        )
    }

    /// Cinco noches verosímiles: la más reciente con ronquidos, tos, habla y
    /// movimiento; las demás, más tranquilas, para que las tendencias se vean.
    static func seed(_ context: ModelContext) {
        var rng = SeededGenerator(seed: 7)
        let cal = Calendar.current
        let today = cal.startOfDay(for: Date())
        // (horas, ronquidos, toses, habla, movimiento)
        let nights: [(Double, Int, Int, Int, Int)] = [
            (7.4, 14, 3, 2, 6), (6.9, 6, 1, 0, 4), (7.8, 2, 0, 1, 3), (6.5, 11, 2, 1, 7), (7.2, 4, 1, 0, 2),
        ]
        for (n, night) in nights.enumerated() {
            let (hours, snores, coughs, speech, moves) = night
            let bedtime = cal.date(byAdding: .minute, value: -(n * 24 * 60) - 120 - Int.random(in: 0...40, using: &rng),
                                   to: today)!
            let session = SleepSession(start: bedtime, batteryStart: 96)
            session.end = bedtime.addingTimeInterval(hours * 3600)
            session.batteryEnd = 96 - Int(hours * 1.6)
            context.insert(session)

            let totalMinutes = Int(hours * 60)
            for m in 0..<totalMinutes {
                // Fondo de ~-62 dBFS con oleadas (ronquidos) a mitad de la noche.
                let wave = sin(Double(m) / 35) * 3 + (m > totalMinutes / 3 && m < totalMinutes * 2 / 3 ? 4 : 0)
                let avg = -62 + wave + Double.random(in: -1.5...1.5, using: &rng)
                let minute = NoiseMinute(stats: MinuteStats(
                    minuteIndex: m, dbMin: avg - Double.random(in: 3...6, using: &rng),
                    dbAvg: avg, dbMax: avg + Double.random(in: 6...14, using: &rng)))
                minute.session = session
                context.insert(minute)
            }

            func add(_ category: SomniaCategory, _ count: Int, length: ClosedRange<Double>, peak: ClosedRange<Double>) {
                for _ in 0..<count {
                    let at = session.start.addingTimeInterval(Double.random(in: 900...(hours * 3600 - 900), using: &rng))
                    let event = SoundEventRecord(
                        start: at, end: at.addingTimeInterval(Double.random(in: length, using: &rng)),
                        dbPeak: Double.random(in: peak, using: &rng), dbAvg: Double.random(in: peak, using: &rng) - 6,
                        category: category.rawValue, confidence: Double.random(in: 0.62...0.94, using: &rng),
                        rawLabel: category.rawValue, clipFile: nil)
                    event.session = session
                    context.insert(event)
                }
            }
            add(.snoring, snores, length: 20...70, peak: -42 ... -30)
            add(.cough, coughs, length: 1...4, peak: -38 ... -26)
            add(.speech, speech, length: 3...9, peak: -44 ... -34)
            add(.movement, moves, length: 2...6, peak: -50 ... -40)
            add(.breathing, snores / 2, length: 10...30, peak: -52 ... -44)
        }
        try? context.save()
    }
}

/// Generador reproducible: las capturas salen iguales en cada corrida.
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64
    init(seed: UInt64) { state = seed &* 0x9E37_79B9_7F4A_7C15 | 1 }
    mutating func next() -> UInt64 {
        state ^= state << 13
        state ^= state >> 7
        state ^= state << 17
        return state
    }
}
