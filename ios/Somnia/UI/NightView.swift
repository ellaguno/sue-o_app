import AVFoundation
import Charts
import SwiftData
import SwiftUI

struct NightView: View {
    let night: SleepSession

    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Environment(MonitorController.self) private var monitor
    @State private var player = ClipPlayer()
    @State private var filter: SomniaCategory?
    @State private var showOther = false
    @State private var confirmDelete = false

    private var events: [SoundEventRecord] { night.sortedEvents }

    private var visibleEvents: [SoundEventRecord] {
        if let filter { return events.filter { $0.effectiveCategory == filter } }
        return showOther ? events : events.filter { $0.effectiveCategory != .other }
    }

    var body: some View {
        let summary = night.summary
        List {
            Section {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Duration: \(Theme.duration(minutes: night.durationMinutes)) · \(summary.total) sound events")
                    if summary.snoringEpisodes > 0 {
                        Text("Snoring: \(summary.snoringEpisodes) events · \(summary.snoringMinutes, specifier: "%.1f") min")
                            .foregroundStyle(Theme.moon)
                    }
                    if let start = night.batteryStart, let end = night.batteryEnd {
                        Text("Battery: \(start)% → \(end)%")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    NoiseChart(night: night)
                        .frame(height: 150)
                        .padding(.top, 6)
                }
            }

            Section("Body sounds") {
                CategoryChips(summary: summary, selected: $filter)
                Text("Ambient: \(summary.count(.environment)) · Other: \(summary.count(.other))")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            if summary.pauseCount > 0 {
                Section("Pause pattern observed") {
                    Text("\(summary.pauseCount) snoring → long pause → abrupt resumption sequences were observed. This is NOT a diagnosis; if it happens often, talk to a doctor.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }

            Section {
                ForEach(visibleEvents) { event in
                    EventRow(event: event,
                             playing: player.playingId == event.id,
                             canPlay: !monitor.isRunning,
                             onPlay: { player.toggle(event) },
                             onRelabel: { category in
                                 event.manualLabel = category.rawValue
                                 try? context.save()
                             })
                }
                if visibleEvents.isEmpty {
                    Text("No sound events were detected in this session.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            } header: {
                HStack {
                    Text("Events (\(visibleEvents.count) of \(events.count))")
                    Spacer()
                    if filter != nil {
                        Button("Show all") { filter = nil }
                    } else if summary.count(.other) > 0 {
                        Button(LocalizedStringKey(showOther ? "Hide “Other”" : "Show “Other”")) { showOther.toggle() }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.night)
        .navigationTitle(Text(night.start, format: .dateTime.weekday(.wide).day().month(.wide)))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("Delete session", role: .destructive) { confirmDelete = true }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .accessibilityLabel(Text("More options"))
                }
            }
        }
        .confirmationDialog("Delete this night?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete session", role: .destructive) {
                player.stop()
                ClipStore.deleteSession(night.id)
                context.delete(night)
                try? context.save()
                dismiss()
            }
        } message: {
            Text("The session and its audio clips will be deleted. This can’t be undone.")
        }
        .alert("Couldn’t play this clip (the file may be damaged).", isPresented: $player.failed) {
            Button("OK", role: .cancel) {}
        }
        .onDisappear { player.stop() }
    }
}

/// Curva de dB por minuto (con banda mín–máx) y cada evento como un punto de color.
private struct NoiseChart: View {
    let night: SleepSession

    var body: some View {
        let minutes = night.sortedMinutes
        let events = night.sortedEvents
        Chart {
            ForEach(minutes, id: \.minuteIndex) { m in
                let t = night.start.addingTimeInterval(TimeInterval(m.minuteIndex * 60))
                AreaMark(x: .value("Time", t),
                         yStart: .value("Min", Theme.db(m.dbMin)),
                         yEnd: .value("Max", Theme.db(m.dbMax)))
                    .foregroundStyle(Theme.moon.opacity(0.2))
                LineMark(x: .value("Time", t), y: .value("dB", Theme.db(m.dbAvg)))
                    .foregroundStyle(Theme.moon)
                    .lineStyle(StrokeStyle(lineWidth: 1.2))
            }
            ForEach(events) { e in
                PointMark(x: .value("Time", e.start), y: .value("dB", Theme.db(e.dbPeak)))
                    .foregroundStyle(Theme.color(e.effectiveCategory))
                    .symbolSize(e.effectiveCategory.isBody ? 30 : 10)
            }
        }
        .chartXAxis {
            AxisMarks(values: .stride(by: .hour)) { _ in
                AxisGridLine()
                AxisValueLabel(format: .dateTime.hour())
            }
        }
        .chartYAxisLabel("dB")
    }
}

private struct CategoryChips: View {
    let summary: NightSummary
    @Binding var selected: SomniaCategory?

    var body: some View {
        let present = SomniaCategory.body.filter { summary.count($0) > 0 }
        if present.isEmpty {
            Text("No body sounds classified tonight.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(present) { category in
                        Button {
                            selected = selected == category ? nil : category
                        } label: {
                            HStack(spacing: 6) {
                                Circle().fill(Theme.color(category)).frame(width: 8, height: 8)
                                Text("\(category.label) · \(summary.count(category))")
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(selected == category ? Theme.moon.opacity(0.25) : Color.clear,
                                        in: Capsule())
                            .overlay(Capsule().stroke(Theme.moon.opacity(0.5)))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

private struct EventRow: View {
    let event: SoundEventRecord
    let playing: Bool
    let canPlay: Bool
    let onPlay: () -> Void
    let onRelabel: (SomniaCategory) -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(event.start, format: .dateTime.hour().minute().second())
                    .font(.subheadline.weight(.semibold))
                Text("\(event.duration, specifier: "%.1f") s · peak \(Theme.db(event.dbPeak)) dB (approx.)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Menu {
                    ForEach(SomniaCategory.allCases) { category in
                        Button(category.label) { onRelabel(category) }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Circle().fill(Theme.color(event.effectiveCategory)).frame(width: 8, height: 8)
                        Text(event.effectiveCategory.label)
                        if event.manualLabel != nil {
                            Image(systemName: "pencil")
                        } else if let c = event.confidence {
                            Text("· \(Int((c * 100).rounded()))%")
                        }
                    }
                    .font(.caption)
                }
            }
            Spacer()
            if event.clipFile != nil {
                Button(action: onPlay) {
                    Image(systemName: playing ? "stop.circle.fill" : "play.circle.fill")
                        .font(.title)
                        .accessibilityLabel(Text(LocalizedStringKey(playing ? "Stop" : "Listen")))
                }
                .buttonStyle(.plain)
                .disabled(!canPlay)
            } else {
                Text("no clip").font(.caption).foregroundStyle(.secondary)
            }
        }
    }
}

/// Reproduce los clips (ya normalizados al guardarlos).
@Observable
final class ClipPlayer: NSObject, AVAudioPlayerDelegate {
    var playingId: UUID?
    var failed = false
    @ObservationIgnored private var player: AVAudioPlayer?

    func toggle(_ event: SoundEventRecord) {
        if playingId == event.id {
            stop()
            return
        }
        stop()
        guard let file = event.clipFile else { return }
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback)
            try AVAudioSession.sharedInstance().setActive(true)
            let p = try AVAudioPlayer(contentsOf: ClipStore.url(for: file))
            p.delegate = self
            p.play()
            player = p
            playingId = event.id
        } catch {
            failed = true
        }
    }

    func stop() {
        player?.stop()
        player = nil
        playingId = nil
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        DispatchQueue.main.async { self.stop() }
    }
}
