import SwiftData
import SwiftUI

struct HomeView: View {
    @Environment(MonitorController.self) private var monitor
    @Environment(\.modelContext) private var context
    @Query(sort: \SleepSession.start, order: .reverse) private var sessions: [SleepSession]

    var body: some View {
        List {
            Section {
                MonitorCard()
            }
            .listRowBackground(Theme.surface)

            if !sessions.isEmpty {
                Section {
                    NavigationLink {
                        TrendsView()
                    } label: {
                        Label("View trends", systemImage: "chart.bar.xaxis")
                    }
                }
                Section("Recorded nights") {
                    ForEach(sessions) { night in
                        NavigationLink {
                            NightView(night: night)
                        } label: {
                            SessionRow(night: night, live: monitor.isRunning && night.end == nil)
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.night)
        .navigationTitle("Somnia")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink {
                    SettingsView()
                } label: {
                    Image(systemName: "gearshape")
                        .accessibilityLabel(Text("Settings"))
                }
            }
        }
        .alert("Microphone blocked", isPresented: Binding(
            get: { monitor.permissionDenied },
            set: { monitor.permissionDenied = $0 }
        )) {
            Button("Open settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("To monitor your sleep, allow microphone access for Somnia in Settings. Audio is processed only on your phone.")
        }
    }
}

private struct MonitorCard: View {
    @Environment(MonitorController.self) private var monitor
    @Environment(\.modelContext) private var context

    var body: some View {
        VStack(spacing: 10) {
            if monitor.isRunning, let started = monitor.startedAt {
                Text("Monitoring")
                    .font(.headline)
                    .foregroundStyle(Theme.moon)
                TimelineView(.periodic(from: started, by: 1)) { timeline in
                    let s = Int(timeline.date.timeIntervalSince(started))
                    Text(String(format: "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60))
                        .font(.system(size: 40, weight: .light, design: .rounded))
                        .monospacedDigit()
                }
                if let db = monitor.currentDbfs {
                    Text("\(Theme.db(db)) dB (approx.)")
                        .font(.title3)
                }
                Text("Estimate: the phone’s microphone isn’t calibrated.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Text("\(monitor.minutesSaved) min recorded · \(monitor.eventsDetected) events detected")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button("Stop", role: .destructive) { monitor.stop() }
                    .buttonStyle(.bordered)
            } else {
                Text("Ready to monitor")
                    .font(.headline)
                Text("Start when you go to bed. Audio is processed on your phone; nothing leaves the device.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                Button {
                    Task { await monitor.start(context: context) }
                } label: {
                    Text("Start monitoring").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
            if let error = monitor.lastError {
                Text(error).font(.caption).foregroundStyle(.red)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }
}

private struct SessionRow: View {
    let night: SleepSession
    let live: Bool

    var body: some View {
        let summary = night.summary
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(night.start, format: .dateTime.weekday(.abbreviated).day().month(.abbreviated).hour().minute())
                    .font(.subheadline.weight(.semibold))
                Spacer()
                if live {
                    Text("in progress").font(.caption).foregroundStyle(Theme.moon)
                } else {
                    Text(Theme.duration(minutes: night.durationMinutes))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            if summary.total > 0 {
                if summary.snoringEpisodes > 0 {
                    Text("\(summary.total) events · snoring: \(summary.snoringEpisodes) (\(summary.snoringMinutes, specifier: "%.1f") min)")
                        .font(.footnote)
                        .foregroundStyle(Theme.moon)
                } else {
                    Text("\(summary.total) events · no snoring")
                        .font(.footnote)
                }
            }
        }
    }
}
