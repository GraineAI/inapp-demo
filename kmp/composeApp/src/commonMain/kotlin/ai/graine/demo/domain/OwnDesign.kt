package ai.graine.demo.domain

import ai.graine.inapp.kmp.GraineCallConnection
import ai.graine.inapp.kmp.GraineCallInfo
import ai.graine.inapp.kmp.GraineCallState
import ai.graine.inapp.kmp.GraineChatStatus
import ai.graine.inapp.kmp.GraineComponentSelect
import ai.graine.inapp.kmp.GraineComponentStyle
import ai.graine.inapp.kmp.GraineScheduledCall
import ai.graine.inapp.kmp.GraineSchedulingRules
import ai.graine.inapp.kmp.GraineVoiceInfo
import ai.graine.inapp.kmp.GraineVoiceStatus
import ai.graine.inapp.kmp.GraineChatChannel
import ai.graine.inapp.kmp.GraineChatRow
import ai.graine.inapp.kmp.GraineChatSession
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/*
 * "YOUR OWN DESIGN": the app draws every surface itself — chat, voice, the call
 * screen, booking, the agent's components — from the SDK's public state, with
 * no SDK screen at all. This file is how that state reads in the app's words.
 * Presentation only: what each state means is the SDK's (contract/PUBLIC-STATE.md).
 */

/** The words the app's own screens put on the SDK's states. */
object OwnText {
    /**
     * The chat's connection, in one line under the agent's name. Since SDK 0.14.5 a chat that gave up says
     * [GraineChatStatus.FAILED] and a live one [GraineChatStatus.CONNECTED]; [failed] (`chat.failed`, deprecated since)
     * still reads as FAILED does.
     */
    fun chatStatus(status: GraineChatStatus, typing: Boolean = false, failed: Boolean = false): String = if (failed) {
        "Couldn't reconnect · tap Retry"
    } else when (status) {
        GraineChatStatus.IDLE -> "Not connected"
        GraineChatStatus.CONNECTING -> "Connecting…"
        GraineChatStatus.CONNECTED -> if (typing) "Typing…" else "Online"
        GraineChatStatus.RECONNECTING -> "Reconnecting…"
        GraineChatStatus.OFFLINE -> "Offline · messages are sent when it reconnects"
        GraineChatStatus.FAILED -> "Couldn't reconnect · tap Retry"
        GraineChatStatus.DISCONNECTED -> "Disconnected · send a message to continue"
    }

    /** The chat offers Retry once it gave up (`chat.failed`): while it is merely offline it comes back by itself. */
    fun chatCanRetry(failed: Boolean): Boolean = failed

    /** The voice conversation's word. */
    fun voiceStatus(status: GraineVoiceStatus): String = when (status) {
        GraineVoiceStatus.IDLE -> "Ready"
        GraineVoiceStatus.CONNECTING -> "Connecting…"
        GraineVoiceStatus.LISTENING -> "Listening"
        GraineVoiceStatus.THINKING -> "Thinking…"
        GraineVoiceStatus.SPEAKING -> "Speaking"
        GraineVoiceStatus.RECONNECTING -> "Reconnecting…"
        GraineVoiceStatus.MUTED -> "Muted"
        GraineVoiceStatus.FAILED -> "Couldn't connect"
        GraineVoiceStatus.ENDED -> "Ended"
    }

    /**
     * A voice conversation is under way, or on its way: End and Mute apply, Start does not — decided by what the
     * conversation does (`connected`, `connecting`), so Start is there whenever nothing is under way.
     */
    fun voiceLive(info: GraineVoiceInfo): Boolean = info.connected || info.connecting

    /** The word to show: [voiceLive]'s rule, so "Connecting…" only while a conversation is really on its way. */
    fun voiceWord(info: GraineVoiceInfo): GraineVoiceStatus =
        if (info.status == GraineVoiceStatus.CONNECTING && !voiceLive(info)) GraineVoiceStatus.IDLE else info.status

    /** Where the call is, as the call screen's headline says it. */
    fun callStatus(info: GraineCallInfo): String = when (info.state) {
        GraineCallState.RINGING -> if (info.hosted) "Scheduled call" else "Incoming call"
        GraineCallState.CONNECTING -> if (info.incoming) "Connecting…" else "Calling…"
        GraineCallState.ACTIVE -> if (info.reconnecting) "Reconnecting…" else "On the call"
        GraineCallState.ENDED -> "Call ended"
        GraineCallState.IDLE -> if (info.missedCall != null) "Missed call" else "No call"
    }

    /** How a call ended (`endReason`, or an outcome's reason): who hung up, or that it dropped. */
    fun endReason(reason: String?, agentName: String): String = when (reason) {
        "local" -> "You ended the call"
        "remote" -> "$agentName ended the call"
        "failed" -> "The call dropped"
        null, "" -> "Call ended"
        else -> "Call ended (${reason.replace('_', ' ')})"
    }

    /**
     * Who or what ended a call — the outcome's `endCause` — in words: `local_end`, `remote_bye`, `media_drop`,
     * `audio_watch`, `failed`, `provider_reset`; a newer word reads as itself. Null when the platform does not say.
     */
    fun endCause(cause: String?, agentName: String): String? = when (cause) {
        null, "" -> null
        "local_end" -> "Hung up on this phone"
        "remote_bye" -> "$agentName's side hung up"
        "media_drop" -> "The audio dropped and did not recover"
        "audio_watch" -> "No audio arrived, so the call was ended"
        "failed" -> "The call failed"
        "provider_reset" -> "The phone's call system reset the call"
        else -> cause.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    /** A call's length: "0:42", "12:05", "1:02:03". */
    fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val r = (s % 60).toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$r" else "$m:$r"
    }

    /** The call's length from `startedAt` to [now]; null before it connected. */
    fun elapsed(startedAt: Instant?, now: Instant): String? = startedAt?.let { duration((now - it).inWholeSeconds) }

    /** A timing: "—" until known, "312 ms", "1.54 s". */
    fun ms(value: Long?): String = when {
        value == null -> "—"
        value < 1000 -> "$value ms"
        else -> {
            val hundredths = (value + 5) / 10
            "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')} s"
        }
    }

    /**
     * THE CONNECTION REPORT as a readout — the same facts as Execution Logs'
     * Connection card (GraineCallConnection), in its order: what carried the
     * call, how it was set up, the tap's wait step by step, and the audio.
     * A fact not known yet reads "—". Timings and network figures only.
     */
    fun connectionRows(c: GraineCallConnection?): List<Pair<String, String>> {
        val audio = c?.audio
        return listOf(
            "Transport" to (c?.transport?.wire ?: "—"),
            "Prepared" to when (c?.prepared) {
                null -> "—"
                true -> c?.preparedAgeSeconds?.let { "yes · ready ${it}s before the tap" } ?: "yes"
                false -> "no · set up after the tap"
            },
            "Credential" to (c?.credential ?: "—"),
            "Setup" to ms(c?.setupMs),
            "Login" to ms(c?.loginMs),
            "Answer" to ms(c?.answerMs),
            "First words" to ms(c?.firstWordsMs),
            "Tap to voice" to ms(c?.tapToVoiceMs),
            "Packet loss" to (audio?.lossPct?.let { percent(it) } ?: "—"),
            "Jitter" to if (audio?.jitterAvgMs == null && audio?.jitterPeakMs == null) "—"
            else "avg ${ms(audio?.jitterAvgMs)} · peak ${ms(audio?.jitterPeakMs)}",
            "Round trip" to ms(audio?.rttMs),
            "Audio quality" to (audio?.quality ?: "—"),
            "Recoveries" to (c?.recoveries?.toString() ?: "—"),
        )
    }

    /** "0.12 %": two decimals, as Execution Logs shows packet loss. */
    fun percent(value: Double): String {
        val hundredths = kotlin.math.round(value * 100).toLong()
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')} %"
    }

    /**
     * Why booking is off, in plain words — the `reason` of the agent's
     * `bookingState`. Null when booking is available. `not_loaded` is "not
     * yet": the agent's settings are still on their way. A word this build
     * does not know still reads.
     */
    fun bookingUnavailable(reason: String?): String? = when (reason) {
        null -> null
        "not_loaded" -> "Loading this agent's settings…"
        "rings_not_set_up" -> "Graine cannot ring this phone yet: this app's push keys are not uploaded for this platform (Workspace settings → In-app calling)."
        "scheduling_off" -> "This agent does not let users book calls (Calls & scheduling → Users, from your app)."
        "not_identified" -> "The signed-in user is not identified with the agent's \"Who is the user?\" field."
        "agent_off" -> "The agent is switched off."
        else -> "Booking is not available here (${reason.replace('_', ' ')})."
    }

    /** The days a user may book on, in [zone]: today through the agent's `maxDaysAhead`. */
    fun bookingDays(rules: GraineSchedulingRules, now: Instant, zone: TimeZone): List<LocalDate> {
        val today = now.toLocalDateTime(zone).date
        return (0..rules.maxDaysAhead.coerceIn(0, 60)).map { today.plus(it, DateTimeUnit.DAY) }
    }

    /** A day picker's label: "Today", "Tomorrow", else "Thu 23 Oct". */
    fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.plus(1, DateTimeUnit.DAY) -> "Tomorrow"
        else -> "${WEEKDAYS[day.dayOfWeek.ordinal]} ${day.dayOfMonth} ${MONTHS[day.monthNumber - 1]}"
    }

    /** The agent's booking rules in one line, in the user's time zone. */
    fun rulesLine(rules: GraineSchedulingRules, zone: TimeZone): String {
        val lead = if (rules.minLeadMinutes == 1) "1 minute" else "${rules.minLeadMinutes} minutes"
        val days = if (rules.maxDaysAhead == 1) "1 day" else "${rules.maxDaysAhead} days"
        val pending = if (rules.maxPendingPerUser == 1) "1 call" else "${rules.maxPendingPerUser} calls"
        return "From $lead to $days ahead · up to $pending booked · times in ${zone.id}"
    }

    /** The slots of a day grouped by the hour they start in, in [zone] — one row of times per hour. */
    fun byHour(slots: List<Instant>, zone: TimeZone): List<Pair<Int, List<Instant>>> =
        slots.groupBy { it.toLocalDateTime(zone).hour }.toList().sortedBy { it.first }

    /** "6 pm", for an hour's row. */
    fun hourLabel(hour: Int): String {
        val h = (hour % 12).let { if (it == 0) 12 else it }
        return "$h ${if (hour < 12) "am" else "pm"}"
    }

    /** "6:05", a slot's time within its hour's row. */
    fun slotLabel(at: Instant, zone: TimeZone): String {
        val t = at.toLocalDateTime(zone)
        val h = (t.hour % 12).let { if (it == 0) 12 else it }
        return "$h:${t.minute.toString().padStart(2, '0')}"
    }

    /** What a session without an id is called: the messages on screen as one run (before Graine sends session ids). */
    const val UNNUMBERED_SESSION = "Earlier messages"

    /** "Chat · 4 messages", "Voice call · 1 message". */
    fun sessionKind(s: GraineChatSession): String =
        (if (s.channel == GraineChatChannel.VOICE) "Voice call" else "Chat") + " · " + if (s.turns == 1) "1 message" else "${s.turns} messages"

    /** Where a session's first turn is in the chat's turns: sessions are consecutive runs, oldest first. */
    fun sessionStart(sessions: List<GraineChatSession>, index: Int): Int = sessions.take(index).sumOf { it.turns }

    /**
     * The row a session begins at, among `GraineChatRows.build`'s rows: the row of its first turn (its bubble, or
     * the call row holding it), with the day line or "new conversation" line just above it when there is one.
     */
    fun sessionRow(rows: List<GraineChatRow>, turnIndex: Int): Int {
        val r = rows.indexOfFirst { row ->
            when (row) {
                is GraineChatRow.Turn -> row.index >= turnIndex
                is GraineChatRow.VoiceCall -> row.last >= turnIndex
                else -> false
            }
        }
        if (r <= 0) return 0
        val above = rows[r - 1]
        return if (above is GraineChatRow.Day || above is GraineChatRow.NewConversation) r - 1 else r
    }

    /** An id shown on the Public state screen: enough to tell two apart, never the whole of it. */
    fun shortId(id: String?): String = when {
        id.isNullOrEmpty() -> "—"
        id.length <= 8 -> id
        else -> "…" + id.takeLast(6)
    }

    /** A level 0–1 as the readout shows it. */
    fun level(value: Float): String {
        val hundredths = kotlin.math.round(value.coerceIn(0f, 1f) * 100).toInt()
        return "0.${hundredths.toString().padStart(2, '0')}".let { if (hundredths >= 100) "1.00" else it }
    }

    /** Under the user's answer while the agent answers it (`componentAwaitingReply`): "Waiting for Asha…". */
    fun waitingFor(agentName: String): String = agentName.trim().let { if (it.isEmpty()) "Waiting for the agent…" else "Waiting for $it…" }

    /**
     * A component's look (`component.style`) in one line, as the Public state readout shows it: the preset, where it
     * goes, the item's button, one choice or several (and how many), then the accent and the corners when it sets them.
     */
    fun componentLook(s: GraineComponentStyle): String = listOfNotNull(
        s.preset.wire,
        s.surface.wire,
        s.button.wire,
        if (s.select == GraineComponentSelect.MANY) "many, up to ${s.maxSelect}" else "one",
        s.accent,
        s.radius?.let { "radius $it" },
    ).joinToString(" · ")

    private val WEEKDAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}

/**
 * THE APP'S OWN BOOKING SCREEN, as it stands: for which agent; whether booking
 * is on (its `bookingState`) and the agent's rules; each day and how
 * many times the SDK offers on it (`bookingSlots`); the day and time picked;
 * what the call is about; and how the last booking went.
 */
data class OwnBooking(
    val agent: DemoAgent,
    /** Null when the agent can be booked here now; else the SDK's word for why not. */
    val unavailableReason: String?,
    val rules: GraineSchedulingRules,
    val days: List<Day>,
    val day: LocalDate?,
    /** The picked day's times, oldest first. */
    val slots: List<Instant>,
    val slot: Instant?,
    val reason: String,
    val busy: Boolean = false,
    /** Why the last booking was refused, in the SDK's words. */
    val refusal: String? = null,
    /** The call just booked. */
    val booked: GraineScheduledCall? = null,
) {
    data class Day(val date: LocalDate, val slotCount: Int)

    val available: Boolean get() = unavailableReason == null

    /** Book is on: booking is available, a time is picked, nothing is on its way. */
    val canBook: Boolean get() = available && slot != null && !busy

    companion object {
        /** The longest reason Graine keeps with a call. */
        const val REASON_MAX = 120
    }
}

/** A call that is over, as the app's own call screen shows it: how it ended, how long it was, and how it connected. */
data class EndedCall(
    val callId: String,
    val agent: DemoAgent?,
    /** `ended` (it connected) or `failed` (it never did). */
    val outcome: String,
    /** ended: `local`, `remote` or `failed`; failed: why. */
    val reason: String?,
    val durationSeconds: Int?,
    /** The final connection report (GraineCallOutcome.connection); null from a platform that has none. */
    val connection: GraineCallConnection?,
    /** Who or what ended it (GraineCallOutcome.endCause); null from a platform that does not say. */
    val endCause: String? = null,
)
