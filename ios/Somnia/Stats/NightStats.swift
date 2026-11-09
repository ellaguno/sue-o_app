import Foundation

/// Categorías propias de Somnia; `rawValue` es lo que se guarda en la base.
enum SomniaCategory: String, CaseIterable, Identifiable {
    case snoring, breathing, cough, speech, movement, environment, other

    var id: String { rawValue }

    /// Sonidos "del cuerpo": lo relevante del resumen (sin ambiente ni otros).
    static let body: [SomniaCategory] = [.snoring, .breathing, .cough, .speech, .movement]

    var isBody: Bool { SomniaCategory.body.contains(self) }

    var label: String {
        switch self {
        case .snoring: return String(localized: "Snoring")
        case .breathing: return String(localized: "Breathing")
        case .cough: return String(localized: "Cough")
        case .speech: return String(localized: "Speech")
        case .movement: return String(localized: "Movement")
        case .environment: return String(localized: "Ambient")
        case .other: return String(localized: "Other")
        }
    }
}

/// Traduce las clases del clasificador de sonidos de Apple (identificadores en
/// snake_case, p. ej. "throat_clearing") a categorías de Somnia, comparando
/// palabras completas como CategoryMapper.kt.
enum CategoryMapper {

    private static let rules: [(SomniaCategory, [String])] = [
        (.snoring, ["snoring", "snort"]),
        (.breathing, ["breathing", "gasp", "wheeze", "pant", "sigh", "hiccup"]),
        (.cough, ["cough", "throat clearing", "sneeze", "sniff"]),
        (.speech, ["speech", "conversation", "babble", "whispering", "laughter", "giggle",
                   "chuckle", "singing", "humming", "shout", "yell", "screaming", "crying",
                   "sobbing", "whimper", "groan", "grunt"]),
        (.movement, ["rustle", "rustling", "squeak", "creak", "door", "drawer", "footsteps",
                     "walk", "thump", "knock", "tap", "zipper", "chewing"]),
        (.environment, ["vehicle", "car", "truck", "motorcycle", "traffic", "engine", "aircraft",
                        "airplane", "helicopter", "train", "siren", "horn", "dog", "bark", "cat",
                        "meow", "bird", "insect", "cricket", "rain", "thunder", "wind", "water",
                        "toilet", "music", "television", "radio", "alarm", "clock", "tick", "fan",
                        "hum", "refrigerator", "noise", "static", "air conditioner"]),
    ]

    private static func words(_ text: String) -> [String] {
        text.lowercased()
            .split(whereSeparator: { !$0.isLetter })
            .map(String.init)
    }

    private static let tokenized: [(SomniaCategory, [[String]])] =
        rules.map { ($0.0, $0.1.map(words)) }

    static func category(forLabel label: String) -> SomniaCategory {
        let w = words(label)
        for (category, keywords) in tokenized {
            for k in keywords where !k.isEmpty && k.count <= w.count {
                for start in 0...(w.count - k.count) where Array(w[start..<start + k.count]) == k {
                    return category
                }
            }
        }
        return .other
    }
}

/// Decide la clase final a partir del puntaje máximo por clase (como
/// ScoreDecoder.kt): ignora clases que describen el cuarto y no el sonido, y
/// desempata ronquido sobre respiración.
enum ScoreDecoder {
    static let minConfidence = 0.2
    static let snoreTieMargin = 0.05
    static let nonInformative: Set<String> = ["silence", "inside_small_room", "inside_large_room"]

    struct Decision: Equatable {
        let label: String
        let score: Double
        let category: SomniaCategory
    }

    static func decide(_ scores: [String: Double]) -> Decision {
        let candidates = scores.filter { !nonInformative.contains($0.key) }
        guard let best = candidates.max(by: { $0.value < $1.value }) else {
            return Decision(label: "?", score: 0, category: .other)
        }
        var label = best.key
        var score = best.value
        var category = CategoryMapper.category(forLabel: label)
        if category == .breathing, let snore = scores["snoring"], snore >= score - snoreTieMargin {
            label = "snoring"
            score = snore
            category = .snoring
        }
        if score < minConfidence { category = .other }
        return Decision(label: label, score: score, category: category)
    }
}

/// Vista mínima de un evento para las estadísticas (independiente de SwiftData).
struct EventSnapshot: Equatable {
    let start: Date
    let end: Date
    let dbPeak: Double
    let category: SomniaCategory
}

/// Patrón ronquido -> pausa larga -> reanudación (no es diagnóstico).
enum PausePattern {
    static let minPause: TimeInterval = 10
    static let maxPause: TimeInterval = 120
    static let resumePeakToleranceDb = 3.0

    struct Occurrence: Equatable {
        let snoreEnd: Date
        let resume: Date
        var pause: TimeInterval { resume.timeIntervalSince(snoreEnd) }
    }

    static func detect(_ events: [EventSnapshot]) -> [Occurrence] {
        let sorted = events.sorted { $0.start < $1.start }
        guard sorted.count > 1 else { return [] }
        var result: [Occurrence] = []
        for i in 0..<(sorted.count - 1) {
            let a = sorted[i], b = sorted[i + 1]
            guard a.category == .snoring, b.category == .snoring || b.category == .breathing else { continue }
            let pause = b.start.timeIntervalSince(a.end)
            guard pause >= minPause, pause <= maxPause, b.dbPeak >= a.dbPeak - resumePeakToleranceDb else { continue }
            result.append(Occurrence(snoreEnd: a.end, resume: b.start))
        }
        return result
    }
}

/// Resumen de una noche.
struct NightSummary: Equatable {
    let counts: [SomniaCategory: Int]
    let snoringEpisodes: Int
    let snoringMinutes: Double
    let pauseCount: Int

    func count(_ c: SomniaCategory) -> Int { counts[c] ?? 0 }
    var bodyCount: Int { SomniaCategory.body.reduce(0) { $0 + count($1) } }
    var total: Int { counts.values.reduce(0, +) }

    static func of(_ events: [EventSnapshot]) -> NightSummary {
        var counts: [SomniaCategory: Int] = [:]
        for e in events { counts[e.category, default: 0] += 1 }
        let snores = events.filter { $0.category == .snoring }
        let minutes = snores.reduce(0.0) { $0 + $1.end.timeIntervalSince($1.start) } / 60
        return NightSummary(counts: counts, snoringEpisodes: snores.count,
                            snoringMinutes: minutes, pauseCount: PausePattern.detect(events).count)
    }
}
