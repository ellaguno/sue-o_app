import XCTest
@testable import Somnia

final class AudioCoreTests: XCTestCase {

    func testDbMeterFullScaleAndSilence() {
        XCTAssertEqual(DbMeter.dbfs(sumOfSquares: 1, count: 1), 0, accuracy: 1e-9)
        XCTAssertEqual(DbMeter.dbfs(sumOfSquares: 0, count: 100), DbMeter.floorDbfs)
        // RMS 0.1 -> -20 dBFS
        XCTAssertEqual(DbMeter.dbfs(sumOfSquares: 0.01 * 10, count: 10), -20, accuracy: 1e-9)
    }

    func testDetectorOpensAndClosesWithHysteresis() {
        let detector = EventDetector()
        for _ in 0..<50 { XCTAssertNil(detector.process(-60)) }
        XCTAssertEqual(detector.process(-30), .start)
        for _ in 0..<9 { XCTAssertNil(detector.process(-30)) }
        var end: EventDetector.Signal?
        for _ in 0..<40 {
            if let s = detector.process(-60) { end = s; break }
        }
        guard case .end(let stats)? = end else { return XCTFail("debió cerrar el evento") }
        XCTAssertEqual(stats.startFrame, 50)
        XCTAssertEqual(stats.peakDbfs, -30, accuracy: 1e-9)
    }

    func testDetectorDiscardsVeryShortEvents() {
        let detector = EventDetector()
        for _ in 0..<50 { _ = detector.process(-60) }
        XCTAssertEqual(detector.process(-30), .start)
        var last: EventDetector.Signal?
        for _ in 0..<40 {
            if let s = detector.process(-60) { last = s; break }
        }
        XCTAssertEqual(last, .discard)
    }

    func testAggregatorEmitsOneStatPerMinute() {
        let agg = NoiseAggregator(readingsPerMinute: 3)
        XCTAssertNil(agg.add(-50))
        XCTAssertNil(agg.add(-40))
        let m = agg.add(-30)
        XCTAssertEqual(m, MinuteStats(minuteIndex: 0, dbMin: -50, dbAvg: -40, dbMax: -30))
        XCTAssertNil(agg.flush())
    }

    func testAggregatorResumesAtRealMinute() {
        let agg = NoiseAggregator(readingsPerMinute: 60)
        _ = agg.add(-50)
        XCTAssertEqual(agg.resume(at: 5)?.minuteIndex, 0)
        _ = agg.add(-50)
        XCTAssertEqual(agg.flush()?.minuteIndex, 5)
    }

    func testRingReturnsLastSamplesInOrder() {
        var ring = SampleRing(capacity: 4)
        ring.append([1, 2, 3, 4, 5, 6][...])
        XCTAssertEqual(ring.last(3), [4, 5, 6])
        XCTAssertEqual(ring.last(10), [3, 4, 5, 6])
    }

    func testPipelineCapturesEventWithPreroll() {
        var events: [EventCapture] = []
        var seconds = 0
        let pipeline = AudioPipeline(detector: EventDetector(),
                                     onSecond: { _ in seconds += 1 },
                                     onEvent: { events.append($0) })
        let quiet = [Float](repeating: 0.001, count: 16_000 * 3)
        let loud = (0..<16_000).map { i in Float(sin(Double(i) * 0.2) * 0.3) }
        pipeline.feed(quiet)
        pipeline.feed(loud)
        pipeline.feed(quiet + quiet)
        XCTAssertEqual(seconds, 10)
        XCTAssertEqual(events.count, 1)
        // 1 s de pre-roll + el evento + la cola de silencio
        XCTAssertGreaterThan(events[0].pcm.count, 16_000 * 2)
        XCTAssertEqual(events[0].startOffsetMs, 3_000)
    }
}

final class NightStatsTests: XCTestCase {

    func testMapperUsesWholeWords() {
        XCTAssertEqual(CategoryMapper.category(forLabel: "snoring"), .snoring)
        XCTAssertEqual(CategoryMapper.category(forLabel: "throat_clearing"), .cough)
        XCTAssertEqual(CategoryMapper.category(forLabel: "baby_crying"), .speech)
        XCTAssertEqual(CategoryMapper.category(forLabel: "infant"), .other) // no casa con "fan"
        XCTAssertEqual(CategoryMapper.category(forLabel: "mechanical_fan"), .environment)
    }

    func testDecoderIgnoresSilenceAndBreaksSnoreTies() {
        let d1 = ScoreDecoder.decide(["silence": 0.9, "footsteps": 0.4])
        XCTAssertEqual(d1.category, .movement)
        let d2 = ScoreDecoder.decide(["breathing": 0.89, "snoring": 0.86])
        XCTAssertEqual(d2.category, .snoring)
        let d3 = ScoreDecoder.decide(["speech": 0.05])
        XCTAssertEqual(d3.category, .other)
    }

    func testSummaryAndPausePattern() {
        let t0 = Date(timeIntervalSince1970: 0)
        func ev(_ s: Double, _ len: Double, _ c: SomniaCategory, _ peak: Double = -40) -> EventSnapshot {
            EventSnapshot(start: t0.addingTimeInterval(s), end: t0.addingTimeInterval(s + len), dbPeak: peak, category: c)
        }
        let events = [ev(0, 6, .snoring), ev(26, 4, .snoring), ev(100, 3, .cough), ev(200, 2, .other)]
        let s = NightSummary.of(events)
        XCTAssertEqual(s.snoringEpisodes, 2)
        XCTAssertEqual(s.snoringMinutes, 10.0 / 60, accuracy: 1e-9)
        XCTAssertEqual(s.bodyCount, 3)
        XCTAssertEqual(s.pauseCount, 1) // 20 s de pausa entre los dos ronquidos
    }
}
