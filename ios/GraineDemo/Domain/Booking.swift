import Foundation
import GraineInApp

/**
 * THE BOOKING SHEET'S TIMES: the pack's quick-time chips, the pickers' 5-minute
 * grid, and the device's time zone as the sheet shows it. The bounds are the
 * SDK's (`GraineSchedulingRules.earliest(now:)` / `latest(now:)`); the SDK
 * checks them again when the call is booked.
 */
enum Booking {
    /// The pickers move in 5-minute steps.
    static let stepMinutes = 5

    /// The time a quick-time chip stands for, on the 5-minute grid, in the device's zone. Nil when it names no time.
    static func resolve(_ quick: UseCasePack.QuickTime, now: Date, calendar: Calendar = .current) -> Date? {
        if let minutes = quick.inMinutes {
            return ceilToStep(now.addingTimeInterval(TimeInterval(minutes * 60)), calendar: calendar)
        }
        guard let time = quick.time else { return nil }
        let parts = time.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2, (0..<24).contains(parts[0]), (0..<60).contains(parts[1]) else { return nil }
        let offset: Int
        switch quick.day?.lowercased() ?? "today" {
        case "today": offset = 0
        case "tomorrow": offset = 1
        default: return nil
        }
        guard let day = calendar.date(byAdding: .day, value: offset, to: calendar.startOfDay(for: now)) else { return nil }
        return calendar.date(bySettingHour: parts[0], minute: parts[1], second: 0, of: day)
    }

    /// Inside the SDK's bounds: a chip outside them is shown disabled.
    static func isBookable(_ date: Date, now: Date, rules: GraineSchedulingRules) -> Bool {
        date >= rules.earliest(now: now) && date <= rules.latest(now: now)
    }

    /// What the pickers offer: the SDK's bounds, moved inwards onto the 5-minute grid.
    static func range(now: Date, rules: GraineSchedulingRules, calendar: Calendar = .current) -> ClosedRange<Date> {
        let low = ceilToStep(rules.earliest(now: now), calendar: calendar)
        let high = max(low, floorToStep(rules.latest(now: now), calendar: calendar))
        return low...high
    }

    /// A picked time on the grid (the nearest step), seconds dropped.
    static func snapped(_ date: Date, calendar: Calendar = .current) -> Date {
        let floor = floorToStep(date, calendar: calendar)
        let next = floor.addingTimeInterval(TimeInterval(stepMinutes * 60))
        return date.timeIntervalSince(floor) < next.timeIntervalSince(date) ? floor : next
    }

    /// "Asia/Kolkata · 6:00 PM": the device's zone, which the time is booked in.
    static func zoneLabel(_ date: Date, zone: TimeZone = .current, locale: Locale = .current) -> String {
        let f = DateFormatter()
        f.timeZone = zone
        f.locale = locale
        f.setLocalizedDateFormatFromTemplate("jmm")
        return "\(zone.identifier) · \(f.string(from: date))"
    }

    static func floorToStep(_ date: Date, calendar: Calendar = .current) -> Date {
        let c = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        var down = c
        down.minute = ((c.minute ?? 0) / stepMinutes) * stepMinutes
        return calendar.date(from: down) ?? date
    }

    static func ceilToStep(_ date: Date, calendar: Calendar = .current) -> Date {
        let floor = floorToStep(date, calendar: calendar)
        return floor == date ? date : floor.addingTimeInterval(TimeInterval(stepMinutes * 60))
    }
}
