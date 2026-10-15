import Foundation
import GraineInApp

/**
 * HOW SCHEDULED CALLS AND REFUSALS READ IN THE APP. Presentation only: what
 * "upcoming" or "can be cancelled" mean belongs to the SDK
 * (`GraineScheduledCall`), and booking — its rules, its checks and its
 * refusals — is the SDK's booking sheet.
 */

/// How a status reads at a glance. The views pick the colour.
enum Tone: Equatable {
    case neutral
    case info
    case positive
    case warning
    case negative
}

enum CallText {
    /// Under a booking button the SDK says cannot be used now (`bookingAvailable` is false).
    static let bookingUnavailable = "Booking needs, in Graine UI: users may book (Calls & scheduling), this app's push keys (Workspace settings → In-app calling), and Who is the user? set to this pack's ID."

    static func label(_ status: GraineCallStatus) -> String {
        switch status {
        case .scheduled: return "Scheduled"
        case .ringing: return "Ringing"
        case .answered: return "Answered"
        case .completed: return "Completed"
        case .declined: return "Declined"
        case .missed: return "Missed"
        case .failed: return "Failed"
        case .cancelled: return "Cancelled"
        case .expired: return "Expired"
        case .noDevice: return "No device"
        case .unknown(let raw):
            // A status this build does not know passes through, readable.
            let words = raw.replacingOccurrences(of: "_", with: " ")
            return words.prefix(1).uppercased() + words.dropFirst()
        }
    }

    static func tone(_ status: GraineCallStatus) -> Tone {
        switch status {
        case .scheduled, .ringing, .answered: return .info
        case .completed: return .positive
        case .declined, .missed, .expired, .noDevice: return .warning
        case .failed: return .negative
        case .cancelled, .unknown: return .neutral
        }
    }

    /// Who booked it, in words.
    static func bookedBy(_ source: GraineCallSource?, agentName: String) -> String? {
        switch source {
        case .agent?: return "Booked by \(agentName)"
        case .app?: return "Booked in the app"
        case .dashboard?, .api?: return "Booked by the team"
        case .unknown?, nil: return nil
        }
    }

    /// Upcoming calls (the server lists them first), then the rest, in the server's order.
    static func split(_ calls: [GraineScheduledCall]) -> (upcoming: [GraineScheduledCall], past: [GraineScheduledCall]) {
        (calls.filter(\.isUpcoming), calls.filter { !$0.isUpcoming })
    }

    /// The next call that will ring, for Home.
    static func next(_ calls: [GraineScheduledCall]) -> GraineScheduledCall? {
        calls.filter { $0.isUpcoming && $0.at != nil }.min { ($0.at ?? .distantFuture) < ($1.at ?? .distantFuture) }
    }

    /**
     * Plain words for a refusal (the platform's error codes) when listing or
     * cancelling calls, or reading memory. For a code the app has no words of
     * its own for, the SDK's message — which is the server's sentence when
     * there was one. A booking's refusals are worded by the SDK's booking sheet.
     */
    static func message(_ error: Error) -> String {
        guard let e = error as? GraineServiceError else { return message(code: .network, fallback: nil) }
        return message(code: e.code, fallback: e.message)
    }

    static func message(code: GraineErrorCode, fallback: String?) -> String {
        switch code {
        case .identityRequired: return "Sign in to use this."
        case .deviceLimit: return "This user is signed in on the maximum number of devices. Sign out on another device first."
        case .rateLimited: return "Too many requests. Try again in a few minutes."
        case .notFound: return "That call no longer exists. Pull to refresh."
        case .network: return "No connection. Check your network and try again."
        case .invalidKey, .originRefused: return "This app is not set up for the assistant yet. Check the publishable key and the agent's allowed domains."
        case .schedulingOff, .tooSoon, .tooFar, .outsideWindow, .tooManyPending, .invalidRequest, .unavailable, .unknown:
            if let s = fallback?.trimmingCharacters(in: .whitespacesAndNewlines), !s.isEmpty { return s }
            return "Something went wrong. Try again."
        }
    }
}

/// How times read in the app: "Today, 18:00", "Tomorrow, 09:30", "Thu 16 Oct, 18:00".
enum DemoFormat {
    /// The time as the screen context carries it: ISO-8601, UTC.
    static func iso(_ date: Date) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        f.timeZone = TimeZone(identifier: "UTC")
        return f.string(from: date)
    }

    static func dayWord(_ date: Date, now: Date, calendar: Calendar = .current) -> String? {
        if calendar.isDate(date, inSameDayAs: now) { return "Today" }
        if let tomorrow = calendar.date(byAdding: .day, value: 1, to: now), calendar.isDate(date, inSameDayAs: tomorrow) { return "Tomorrow" }
        if let yesterday = calendar.date(byAdding: .day, value: -1, to: now), calendar.isDate(date, inSameDayAs: yesterday) { return "Yesterday" }
        return nil
    }

    static func time(_ date: Date?, now: Date, calendar: Calendar = .current, locale: Locale = .current) -> String {
        guard let date = date else { return "Time not available" }
        let time = DateFormatter()
        time.calendar = calendar
        time.timeZone = calendar.timeZone
        time.locale = locale
        time.setLocalizedDateFormatFromTemplate("jmm")
        if let word = dayWord(date, now: now, calendar: calendar) {
            return "\(word), \(time.string(from: date))"
        }
        let day = DateFormatter()
        day.calendar = calendar
        day.timeZone = calendar.timeZone
        day.locale = locale
        day.setLocalizedDateFormatFromTemplate("EEEdMMM")
        return "\(day.string(from: date)), \(time.string(from: date))"
    }
}
