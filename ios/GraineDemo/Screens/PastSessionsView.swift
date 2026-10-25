import SwiftUI
import UIKit

/**
 * PAST SESSIONS, DRAWN BY THE APP — the signed-in user's closed sessions with
 * the agent, newest first, from the SDK's `chat.pastSessions`: when, chat or
 * call, how long, how it ended, how many messages, the first words, and the
 * session id shortened with Copy. The next page loads at the end of the list
 * (`loadMorePastSessions()`); empty, loading and refused lists say so in
 * plain words. A tap opens the session's transcript (`sessionTranscript(id:)`),
 * read only. Opened from the chat's header.
 */
struct PastSessionsView: View {
    @EnvironmentObject private var model: AppModel
    let agent: DemoAgent

    var body: some View {
        NavigationStack {
            List {
                Section {
                    if model.pastSessions.sessions.isEmpty { emptyState }
                    ForEach(model.pastSessions.sessions) { session in
                        SessionRow(session: session, opening: model.transcriptLoading == session.id) {
                            Task { await model.openTranscript(session.id) }
                        } onCopy: {
                            UIPasteboard.general.string = session.id
                            model.notice = "Session id copied."
                        }
                    }
                } header: {
                    Text("With \(model.name(of: agent)) · newest first · drawn by this app")
                        .textCase(nil)
                }
                if !model.pastSessions.sessions.isEmpty {
                    Section { footer }
                }
            }
            .navigationTitle("Past sessions")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { model.closePastSessions() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button { model.refreshPastSessions() } label: { Image(systemName: "arrow.clockwise") }
                        .accessibilityLabel("Refresh")
                        .accessibilityIdentifier("past_sessions_refresh")
                }
            }
            .refreshable { model.refreshPastSessions() }
            .navigationDestination(isPresented: Binding(
                get: { model.transcript != nil },
                set: { if !$0 { model.closeTranscript() } }
            )) {
                if let transcript = model.transcript {
                    TranscriptView(agent: agent, transcript: transcript)
                }
            }
            .overlay(alignment: .bottom) {
                if let why = model.transcriptError {
                    Label(why, systemImage: "exclamationmark.triangle")
                        .font(.footnote)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .foregroundColor(.red)
                        .background(Color.red.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        .padding(16)
                        .accessibilityIdentifier("transcript_error")
                }
            }
        }
        .accessibilityIdentifier("past_sessions")
    }

    /// Nothing listed: why not, still loading, or none yet.
    @ViewBuilder private var emptyState: some View {
        let state = model.pastSessions
        if let words = PastSessionsText.error(state.error) {
            VStack(alignment: .leading, spacing: 10) {
                Text(words).accessibilityIdentifier("past_sessions_error")
                if PastSessionsText.canRetry(state.error) {
                    Button("Try again") { model.refreshPastSessions() }
                        .buttonStyle(.bordered)
                }
            }
            .padding(.vertical, 8)
        } else if state.loading {
            HStack(spacing: 10) {
                ProgressView()
                Text(PastSessionsText.loading).foregroundColor(.secondary)
            }
            .padding(.vertical, 8)
        } else {
            Text(PastSessionsText.empty)
                .foregroundColor(.secondary)
                .padding(.vertical, 8)
                .accessibilityIdentifier("past_sessions_empty")
        }
    }

    /// Under the list: the next page on its way, why it did not come, more to load, or the end.
    @ViewBuilder private var footer: some View {
        let state = model.pastSessions
        HStack {
            Spacer()
            if state.loading {
                ProgressView()
            } else if let words = PastSessionsText.error(state.error) {
                VStack(spacing: 4) {
                    Text(words).font(.footnote).foregroundColor(.red)
                    Button("Try again") { model.loadMorePastSessions() }
                }
            } else if state.hasMore {
                Button("Load more") { model.loadMorePastSessions() }
                    .accessibilityIdentifier("past_sessions_more")
                    // The end of the list is in view: the next page.
                    .onAppear { model.loadMorePastSessions() }
            } else {
                Text("No earlier sessions.").font(.caption).foregroundColor(.secondary)
            }
            Spacer()
        }
    }
}

/// One session: when, what kind and how long, its first words, how it ended, and its id with Copy.
private struct SessionRow: View {
    let session: PastSession
    let opening: Bool
    let onOpen: () -> Void
    let onCopy: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(DemoFormat.time(session.startedAt, now: Date())).font(.subheadline.weight(.semibold))
                Text(PastSessionsText.summary(session)).font(.footnote).foregroundColor(.secondary)
                if let preview = session.preview {
                    Text("“\(preview)”").font(.footnote).lineLimit(2)
                }
                Button(action: onCopy) {
                    HStack(spacing: 6) {
                        Text(PastSessionsText.shortId(session.id)).font(.caption.monospaced())
                        Text("Copy").font(.caption)
                    }
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Copy the session id")
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 6) {
                StatusChip(text: PastSessionsText.status(session.status), tone: .neutral)
                if opening { ProgressView() }
            }
        }
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpen)
        .accessibilityAddTraits(.isButton)
        .accessibilityIdentifier("past_session")
    }
}

/// One past session, read only: its lines oldest first, each with its time; who said it by the side it sits on.
private struct TranscriptView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.demoAccent) private var accent
    let agent: DemoAgent
    let transcript: SessionTranscript

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 10) {
                Text(PastSessionsText.summary(transcript))
                    .font(.footnote)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.bottom, 4)
                if transcript.turns.isEmpty {
                    Text("This session has no messages.").foregroundColor(.secondary)
                }
                ForEach(Array(transcript.turns.enumerated()), id: \.offset) { _, line in
                    bubble(line)
                }
            }
            .padding(16)
        }
        .navigationTitle(DemoFormat.time(transcript.startedAt, now: Date()))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    UIPasteboard.general.string = transcript.id
                    model.notice = "Session id copied."
                } label: {
                    Text(PastSessionsText.shortId(transcript.id)).font(.caption.monospaced())
                }
                .accessibilityLabel("Copy the session id")
            }
        }
        .accessibilityIdentifier("transcript")
    }

    private func bubble(_ line: TranscriptTurn) -> some View {
        let mine = PastSessionsText.isCustomer(line)
        return HStack {
            if mine { Spacer(minLength: 48) }
            VStack(alignment: mine ? .trailing : .leading, spacing: 3) {
                Text(line.text)
                    .font(.subheadline)
                    .foregroundColor(mine ? .white : .primary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(mine ? accent : Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                caption(line, mine: mine)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 4)
            }
            if !mine { Spacer(minLength: 48) }
        }
    }

    private func caption(_ line: TranscriptTurn, mine: Bool) -> Text {
        let who = Text(mine ? "You" : model.name(of: agent))
        guard let at = line.at else { return who }
        return who + Text(" · ") + Text(at, style: .time)
    }
}
