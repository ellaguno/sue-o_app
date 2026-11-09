import Charts
import SwiftData
import SwiftUI

/// Tendencias: promedios y minutos roncando por noche.
struct TrendsView: View {
    @Query(sort: \SleepSession.start) private var sessions: [SleepSession]

    /// Noches cerradas de al menos 5 minutos (las más cortas son pruebas).
    private var nights: [SleepSession] {
        sessions.filter { ($0.durationMinutes ?? 0) >= 5 }
    }

    private struct NightPoint: Identifiable {
        let id: UUID
        let date: Date
        let snoringMinutes: Double
        let bodyCount: Int
    }

    var body: some View {
        let summaries = nights.map { ($0, $0.summary) }
        let points = summaries.map {
            NightPoint(id: $0.0.id, date: $0.0.start,
                       snoringMinutes: $0.1.snoringMinutes, bodyCount: $0.1.bodyCount)
        }
        List {
            if nights.isEmpty {
                Text("Not enough nights yet. Record a few nights to see your trends.")
                    .foregroundStyle(.secondary)
            } else {
                let count = Double(nights.count)
                let avgSnore = summaries.reduce(0) { $0 + $1.1.snoringMinutes } / count
                let avgBody = Double(summaries.reduce(0) { $0 + $1.1.bodyCount }) / count
                let avgDuration = nights.reduce(0) { $0 + ($1.durationMinutes ?? 0) } / nights.count
                let pauseNights = summaries.filter { $0.1.pauseCount > 0 }.count
                Section("Summary") {
                    Text("\(nights.count) nights recorded")
                    Text("Average snoring: \(avgSnore, specifier: "%.1f") min/night")
                        .foregroundStyle(Theme.moon)
                    Text("Body sounds: \(avgBody, specifier: "%.1f") per night")
                    Text("Average duration: \(Theme.duration(minutes: avgDuration))")
                    if pauseNights > 0 {
                        Text("Pause pattern in \(pauseNights) of \(nights.count) nights")
                    }
                }
                Section("Minutes snoring per night") {
                    Chart(points) { p in
                        BarMark(x: .value("Night", p.date, unit: .day),
                                y: .value("Minutes", p.snoringMinutes))
                            .foregroundStyle(Theme.color(.snoring))
                    }
                    .frame(height: 180)
                }
                Section("Body sounds per night") {
                    Chart(points) { p in
                        BarMark(x: .value("Night", p.date, unit: .day),
                                y: .value("Events", p.bodyCount))
                            .foregroundStyle(Theme.color(.breathing))
                    }
                    .frame(height: 180)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.night)
        .navigationTitle("Trends")
    }
}

struct SettingsView: View {
    @AppStorage(MonitorController.marginKey) private var margin = 12.0

    var body: some View {
        Form {
            Section {
                Text("+\(Int(margin)) dB above background noise")
                    .font(.headline)
                Slider(value: $margin, in: 6...20, step: 1)
                Text("← more sensitive (more events) · less sensitive (loud sounds only) →")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                if margin != 12 {
                    Button("Reset to the recommended value (+12 dB)") { margin = 12 }
                }
            } header: {
                Text("Detection sensitivity")
            } footer: {
                Text("An event is recorded when sound rises above the background noise by this margin. If you sleep somewhere noisy and too many events are recorded, raise it; if soft snores are missed, lower it. Applies from the next session.")
            }
            Section("Your privacy") {
                Text("The whole night isn’t recorded: only short clips of each detected sound. Everything is analyzed and stored on your phone; audio isn’t uploaded to any server or included in backups.")
                    .font(.footnote)
            }
            Section {
                Text("Somnia guides and documents, but doesn’t diagnose. If you notice breathing pauses or a lot of daytime tiredness, talk to a doctor.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.night)
        .navigationTitle("Settings")
    }
}

/// Bienvenida (solo la primera vez).
struct WelcomeView: View {
    let onDone: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Welcome to Somnia")
                    .font(.largeTitle.bold())
                Text("Somnia listens while you sleep, detects snoring, coughing, speech and movement, and shows you how your night went. Before you start:")
                WelcomeCard(icon: "iphone",
                            title: "Where to put the phone",
                            text: "On the nightstand, 30–60 cm (1–2 ft) from your head, with the microphone (bottom edge) facing you. Keep it plugged into the charger and don’t put it under the pillow.")
                WelcomeCard(icon: "lock.fill",
                            title: "Your privacy",
                            text: "The whole night isn’t recorded: only short clips of each detected sound. Everything is analyzed and stored on your phone; audio isn’t uploaded to any server or included in backups.")
                WelcomeCard(icon: "moon.fill",
                            title: "Keep it running all night",
                            text: "Start monitoring and lock the phone: Somnia keeps listening in the background. Avoid closing it from the app switcher.")
                WelcomeCard(icon: "cross.case.fill",
                            title: "Not a medical device",
                            text: "Somnia guides and documents, but doesn’t diagnose. If you notice breathing pauses or a lot of daytime tiredness, talk to a doctor.")
                Button(action: onDone) {
                    Text("Get started").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
            }
            .padding()
        }
        .background(Theme.night)
    }
}

private struct WelcomeCard: View {
    let icon: String
    let title: LocalizedStringKey
    let text: LocalizedStringKey

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(Theme.moon)
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline)
                Text(text).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 16))
    }
}
