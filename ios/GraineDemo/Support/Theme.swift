import SwiftUI

/**
 * The demo's own chrome. Its accent is the pack's (`accent`); the agent's
 * surfaces (the bar, the chat, the call screen) draw the brand's design from
 * the dashboard instead.
 */
enum DemoTheme {
    static let fallbackAccent = Color("AccentColor")
    static let cardCorner: CGFloat = 14
    static let warning = Color(red: 0.75, green: 0.45, blue: 0.05)

    static func color(_ tone: Tone, accent: Color) -> Color {
        switch tone {
        case .neutral: return .secondary
        case .info: return accent
        case .positive: return Color(red: 0.12, green: 0.55, blue: 0.33)
        case .warning: return warning
        case .negative: return Color(red: 0.78, green: 0.18, blue: 0.18)
        }
    }
}

private struct DemoAccentKey: EnvironmentKey {
    static let defaultValue = DemoTheme.fallbackAccent
}

extension EnvironmentValues {
    /// The pack's accent, set once at the root.
    var demoAccent: Color {
        get { self[DemoAccentKey.self] }
        set { self[DemoAccentKey.self] = newValue }
    }
}

extension Color {
    /// "#0F766E" → a colour; nil for anything else.
    init?(hex: String?) {
        guard let hex = hex, hex.count == 7, hex.hasPrefix("#"), let v = UInt32(hex.dropFirst(), radix: 16) else { return nil }
        self.init(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }
}

/// A small status label: "Scheduled", "Taken today", "Missed".
struct StatusChip: View {
    @Environment(\.demoAccent) private var accent
    let text: String
    let tone: Tone

    var body: some View {
        let color = DemoTheme.color(tone, accent: accent)
        Text(text)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .foregroundColor(color)
            .background(color.opacity(0.12), in: Capsule())
            .accessibilityLabel("Status: \(text)")
    }
}

/// A grouped block on the home screen.
struct Card<Content: View>: View {
    let title: String?
    let content: Content

    init(_ title: String? = nil, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let title = title {
                Text(title).font(.headline).foregroundColor(.primary)
            }
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: DemoTheme.cardCorner, style: .continuous))
    }
}

/// A large, labelled action button.
struct ActionTile: View {
    @Environment(\.demoAccent) private var accent
    let title: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: systemImage).font(.title2)
                Text(title).font(.subheadline.weight(.semibold)).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity, minHeight: 84)
            .foregroundColor(accent)
            .background(accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("tile_\(title)")
    }
}

/// A one-line message at the bottom of the screen that goes away by itself.
struct NoticeBanner: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.subheadline)
            .multilineTextAlignment(.leading)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .foregroundColor(.white)
            .background(Color.black.opacity(0.85), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .padding(.horizontal, 16)
            .accessibilityAddTraits(.updatesFrequently)
    }
}

/// The pack's standing notice (Home and Chat): what the assistant does not do.
struct NoticeCard: View {
    let text: String

    var body: some View {
        Label(text, systemImage: "info.circle")
            .font(.footnote)
            .foregroundColor(.secondary)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .accessibilityIdentifier("pack_notice")
    }
}
