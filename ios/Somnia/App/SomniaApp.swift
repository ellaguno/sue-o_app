import SwiftData
import SwiftUI

@main
struct SomniaApp: App {
    @State private var monitor = MonitorController()
    let container: ModelContainer

    init() {
        do {
            container = try ModelContainer(for: SleepSession.self, NoiseMinute.self, SoundEventRecord.self)
        } catch {
            fatalError("No se pudo abrir la base de datos: \(error)")
        }
        // Si iOS terminó la app a media noche, esa noche quedó abierta.
        MonitorController.closeDanglingSessions(in: container.mainContext)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(monitor)
                .preferredColorScheme(.dark)
                .tint(Theme.moon)
        }
        .modelContainer(container)
    }
}

/// Bienvenida la primera vez; después, la pantalla de inicio.
struct RootView: View {
    @AppStorage("onboardingDone") private var onboardingDone = false

    var body: some View {
        if onboardingDone {
            NavigationStack { HomeView() }
        } else {
            WelcomeView { onboardingDone = true }
        }
    }
}

enum Theme {
    static let night = Color(red: 0.06, green: 0.07, blue: 0.13)
    static let surface = Color(red: 0.10, green: 0.12, blue: 0.19)
    static let moon = Color(red: 0.62, green: 0.71, blue: 1.0)

    static func color(_ category: SomniaCategory) -> Color {
        switch category {
        case .snoring: return Color(red: 0.89, green: 0.34, blue: 0.18)
        case .breathing: return Color(red: 0.09, green: 0.64, blue: 0.60)
        case .cough: return Color(red: 0.95, green: 0.65, blue: 0.07)
        case .speech: return Color(red: 0.48, green: 0.42, blue: 0.96)
        case .movement: return Color(red: 0.31, green: 0.56, blue: 0.85)
        case .environment: return Color(red: 0.60, green: 0.63, blue: 0.65)
        case .other: return Color(red: 0.75, green: 0.77, blue: 0.79)
        }
    }

    /// dBFS -> dB aproximados para mostrar.
    static func db(_ dbfs: Double) -> Int { Int((dbfs + DbMeter.calibrationOffset).rounded()) }

    static func duration(minutes: Int?) -> String {
        guard let m = minutes else { return "—" }
        return String(format: "%dh %02dm", m / 60, m % 60)
    }
}
