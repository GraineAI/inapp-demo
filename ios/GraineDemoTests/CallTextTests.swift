import GraineInApp
import XCTest
@testable import GraineDemo

/// The app only presents scheduled calls; the SDK owns their rules. These pin the presentation.
final class CallTextTests: XCTestCase {
    private let now = TestClock.at(10, 7)

    func testEveryStatusHasALabelAndTone() {
        let all: [GraineCallStatus] = [.scheduled, .ringing, .answered, .completed, .declined, .missed, .failed, .cancelled, .expired, .noDevice]
        XCTAssertEqual(all.map(CallText.label), ["Scheduled", "Ringing", "Answered", "Completed", "Declined", "Missed", "Failed", "Cancelled", "Expired", "No device"])
        XCTAssertEqual(CallText.tone(.completed), .positive)
        XCTAssertEqual(CallText.tone(.missed), .warning)
        XCTAssertEqual(CallText.tone(.noDevice), .warning)
        XCTAssertEqual(CallText.tone(.failed), .negative)
        XCTAssertEqual(CallText.tone(.scheduled), .info)
        XCTAssertEqual(CallText.label(.unknown("on_hold")), "On hold")
        XCTAssertEqual(CallText.tone(.unknown("on_hold")), .neutral)
    }

    func testBookedBy() {
        XCTAssertEqual(CallText.bookedBy(.agent, agentName: "Asha"), "Booked by Asha")
        XCTAssertEqual(CallText.bookedBy(.app, agentName: "Asha"), "Booked in the app")
        XCTAssertEqual(CallText.bookedBy(.dashboard, agentName: "Asha"), "Booked by the team")
        XCTAssertNil(CallText.bookedBy(nil, agentName: "Asha"))
        XCTAssertNil(CallText.bookedBy(.unknown("crm"), agentName: "Asha"))
    }

    func testSplitAndNextUseTheSDKsIdeaOfUpcoming() {
        let calls = [
            GraineScheduledCall(id: "e", at: TestClock.at(10), status: .ringing),
            GraineScheduledCall(id: "b", at: TestClock.at(12), status: .scheduled),
            GraineScheduledCall(id: "f", at: nil, status: .scheduled),
            GraineScheduledCall(id: "c", at: TestClock.at(9), status: .completed),
            GraineScheduledCall(id: "d", at: TestClock.at(8), status: .missed),
        ]
        let split = CallText.split(calls)
        XCTAssertEqual(split.upcoming.map(\.id), ["e", "b", "f"])
        XCTAssertEqual(split.past.map(\.id), ["c", "d"])
        XCTAssertEqual(CallText.next(calls)?.id, "e")
        XCTAssertNil(CallText.next([GraineScheduledCall(id: "x", at: nil, status: .scheduled)]))
    }

    func testRefusalsInWords() {
        func say(_ code: GraineErrorCode, _ message: String = "x") -> String {
            CallText.message(GraineServiceError(code: code, message: message))
        }
        XCTAssertEqual(say(.notFound), "That call no longer exists. Pull to refresh.")
        XCTAssertEqual(say(.identityRequired), "Sign in to use this.")
        XCTAssertEqual(say(.network), "No connection. Check your network and try again.")
        // A booking's refusals are the SDK's booking sheet's to word; anywhere else, the SDK's sentence.
        XCTAssertEqual(say(.outsideWindow, "Outside calling hours."), "Outside calling hours.")
        // A code without words of its own: the SDK's sentence.
        XCTAssertEqual(say(.unavailable, "Try again shortly."), "Try again shortly.")
        XCTAssertEqual(say(.unknown("new_code"), " "), "Something went wrong. Try again.")
        // Anything that is not the SDK's error reads as a lost connection.
        XCTAssertEqual(CallText.message(URLError(.notConnectedToInternet)), "No connection. Check your network and try again.")
    }

    func testTimes() {
        let cal = TestClock.calendar
        XCTAssertEqual(DemoFormat.dayWord(TestClock.at(18), now: now, calendar: cal), "Today")
        XCTAssertEqual(DemoFormat.dayWord(TestClock.at(9, day: 15), now: now, calendar: cal), "Tomorrow")
        XCTAssertEqual(DemoFormat.dayWord(TestClock.at(9, day: 13), now: now, calendar: cal), "Yesterday")
        XCTAssertNil(DemoFormat.dayWord(TestClock.at(9, day: 20), now: now, calendar: cal))
        XCTAssertEqual(DemoFormat.time(nil, now: now), "Time not available")
        XCTAssertTrue(DemoFormat.time(TestClock.at(18), now: now, calendar: cal, locale: Locale(identifier: "en_GB")).hasPrefix("Today, 18:00"))
        // 18:00 in Kolkata is 12:30 UTC.
        XCTAssertEqual(DemoFormat.iso(TestClock.at(18)), "2026-10-14T12:30:00Z")
    }
}

final class CallLogTests: XCTestCase {
    func testNewestFirstAndCapped() {
        var log = CallLog()
        for i in 0..<(CallLog.cap + 5) {
            log.record(CallLogEntry(callId: "c\(i)", event: "missed", at: TestClock.at(10)))
        }
        XCTAssertEqual(log.entries.count, CallLog.cap)
        XCTAssertEqual(log.entries.first?.callId, "c\(CallLog.cap + 4)")
    }

    func testKeptOnTheDevice() {
        let defaults = TestClock.defaults()
        var log = CallLog()
        log.record(CallLogEntry(callId: "c1", event: "ended", detail: "remote", at: TestClock.at(10)))
        log.save(to: defaults)
        XCTAssertEqual(CallLog.load(from: defaults), log)
        log.clear()
        log.save(to: defaults)
        XCTAssertTrue(CallLog.load(from: defaults).entries.isEmpty)
    }

    func testDescriptionsUseTheAgentsName() {
        func d(_ e: String, _ detail: String? = nil) -> String { CallLog.describe(event: e, detail: detail, agentName: "Kabir") }
        XCTAssertEqual(d("placed"), "You called Kabir")
        XCTAssertEqual(d("incoming", "scheduled"), "Kabir called (scheduled call)")
        XCTAssertEqual(d("incoming"), "Kabir called")
        XCTAssertEqual(d("answered"), "Answered")
        XCTAssertEqual(d("declined"), "Declined")
        XCTAssertEqual(d("missed"), "Missed")
        XCTAssertEqual(d("ended", "local"), "Ended by you")
        XCTAssertEqual(d("ended", "remote"), "Ended by Kabir")
        XCTAssertEqual(d("ended", "failed"), "Call dropped")
        XCTAssertEqual(d("failed", "Microphone denied"), "Could not connect: Microphone denied")
        XCTAssertEqual(d("failed"), "Could not connect")
    }
}
