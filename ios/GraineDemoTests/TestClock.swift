import Foundation

/// A fixed calendar and clock, so every test reads the same day in the same zone.
enum TestClock {
    static let calendar: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Kolkata")!
        return c
    }()

    /// 14 Oct 2026 at `hour:minute`, Asia/Kolkata.
    static func at(_ hour: Int, _ minute: Int = 0, day: Int = 14) -> Date {
        calendar.date(from: DateComponents(year: 2026, month: 10, day: day, hour: hour, minute: minute))!
    }

    /// An empty, private UserDefaults for one test.
    static func defaults(_ name: String = #function) -> UserDefaults {
        let suite = "graine.care.tests.\(name).\(UUID().uuidString)"
        let d = UserDefaults(suiteName: suite)!
        d.removePersistentDomain(forName: suite)
        return d
    }
}
