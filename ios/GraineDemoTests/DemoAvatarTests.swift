import XCTest
@testable import GraineDemo

/// The agent's face, as the demo words it: the line under "Show the agent's face" for every state the SDK reports.
final class DemoAvatarTests: XCTestCase {
    func testOffSaysVoiceOnly() {
        XCTAssertEqual(DemoAvatar(phase: .live, reason: nil).caption(switchedOn: false), "Calls are voice only.")
    }

    func testEachStateSaysWhatTheCustomerSees() {
        XCTAssertEqual(DemoAvatar(phase: .live).caption(switchedOn: true), "The face is speaking.")
        XCTAssertEqual(DemoAvatar(phase: .available).caption(switchedOn: true), "Connecting the face…")
        XCTAssertEqual(DemoAvatar.off.caption(switchedOn: true), "Shown on your next call with the agent.")
        XCTAssertTrue(DemoAvatar(phase: .off, reason: "busy").caption(switchedOn: true).hasPrefix("Every face is in use"))
        XCTAssertTrue(DemoAvatar(phase: .off, reason: "off").caption(switchedOn: true).contains("turn on Avatar"))
        XCTAssertTrue(DemoAvatar(phase: .off, reason: "unsupported").caption(switchedOn: true).contains("continues with voice"))
        XCTAssertTrue(DemoAvatar(phase: .off, reason: "stalled").caption(switchedOn: true).contains("continues with voice"))
        XCTAssertFalse(DemoAvatar.off.showsFace)
        XCTAssertTrue(DemoAvatar(phase: .available).showsFace)
    }
}

extension DemoAvatar {
    init(phase: Phase) { self.init(phase: phase, reason: nil) }
}
