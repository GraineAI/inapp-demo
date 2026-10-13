import GraineInApp
import XCTest
@testable import GraineDemo

/// The booking sheet's times: quick-time chips, the 5-minute grid, the SDK's bounds, the zone shown.
final class BookingTests: XCTestCase {
    private let cal = TestClock.calendar
    private let now = TestClock.at(10, 7)
    private let rules = GraineSchedulingRules(usersCanBook: true, minLeadMinutes: 5, maxDaysAhead: 14)
    private var quick: [UseCasePack.QuickTime] { TestPack.pack.call.quickTimes }

    func testTheSheetReadsThePacksLabelReasonsAndQuickTimes() {
        let call = TestPack.pack.call
        XCTAssertEqual(call.bookLabel, "Schedule a consultation call")
        XCTAssertEqual(call.reasons, ["Weekly check-in", "Medicine review"])
        XCTAssertEqual(quick.map(\.label), ["In 15 minutes", "Tomorrow 10:00", "Today 19:00"])
    }

    func testQuickTimesResolveInTheDevicesZoneOnTheGrid() {
        // 10:07 + 15 minutes = 10:22, up to the 5-minute grid: 10:25.
        XCTAssertEqual(Booking.resolve(quick[0], now: now, calendar: cal), TestClock.at(10, 25))
        XCTAssertEqual(Booking.resolve(quick[1], now: now, calendar: cal), TestClock.at(10, 0, day: 15))
        XCTAssertEqual(Booking.resolve(quick[2], now: now, calendar: cal), TestClock.at(19, 0))
        XCTAssertNil(Booking.resolve(UseCasePack.QuickTime(label: "Soon"), now: now, calendar: cal))
        XCTAssertNil(Booking.resolve(UseCasePack.QuickTime(label: "Bad", day: "someday", time: "10:00"), now: now, calendar: cal))
        XCTAssertNil(Booking.resolve(UseCasePack.QuickTime(label: "Bad", day: "today", time: "25:00"), now: now, calendar: cal))
    }

    func testAChipOutsideTheSDKsBoundsIsNotBookable() {
        let late = TestClock.at(20, 30)
        // At 20:30, "Today 19:00" has passed.
        XCTAssertFalse(Booking.isBookable(Booking.resolve(quick[2], now: late, calendar: cal)!, now: late, rules: rules))
        XCTAssertTrue(Booking.isBookable(Booking.resolve(quick[1], now: late, calendar: cal)!, now: late, rules: rules))
        // A 30-minute lead refuses "In 15 minutes".
        let strict = GraineSchedulingRules(usersCanBook: true, minLeadMinutes: 30)
        XCTAssertFalse(Booking.isBookable(Booking.resolve(quick[0], now: now, calendar: cal)!, now: now, rules: strict))
    }

    func testThePickersRangeIsTheSDKsBoundsOnTheGrid() {
        let range = Booking.range(now: now, rules: rules, calendar: cal)
        // earliest 10:12 → 10:15; latest 28 Oct 10:07 → 10:05.
        XCTAssertEqual(range.lowerBound, TestClock.at(10, 15))
        XCTAssertEqual(range.upperBound, TestClock.at(10, 5, day: 28))
    }

    func testSnapping() {
        XCTAssertEqual(Booking.snapped(TestClock.at(10, 7), calendar: cal), TestClock.at(10, 5))
        XCTAssertEqual(Booking.snapped(TestClock.at(10, 8), calendar: cal), TestClock.at(10, 10))
        XCTAssertEqual(Booking.snapped(TestClock.at(10, 10), calendar: cal), TestClock.at(10, 10))
        XCTAssertEqual(Booking.ceilToStep(TestClock.at(10, 11), calendar: cal), TestClock.at(10, 15))
        XCTAssertEqual(Booking.floorToStep(TestClock.at(10, 14), calendar: cal), TestClock.at(10, 10))
    }

    func testTheZoneIsShownWithTheTime() {
        let label = Booking.zoneLabel(TestClock.at(18), zone: cal.timeZone, locale: Locale(identifier: "en_GB"))
        XCTAssertEqual(label, "Asia/Kolkata · 18:00")
    }
}
