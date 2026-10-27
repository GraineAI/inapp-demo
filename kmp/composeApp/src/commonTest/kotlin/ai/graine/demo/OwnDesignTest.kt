package ai.graine.demo

import ai.graine.demo.domain.OwnComponents
import ai.graine.demo.domain.OwnText
import ai.graine.demo.domain.OwnWidget
import ai.graine.inapp.kmp.GraineCallConnection
import ai.graine.inapp.kmp.GraineCallInfo
import ai.graine.inapp.kmp.GraineCallState
import ai.graine.inapp.kmp.GraineCallTransport
import ai.graine.inapp.kmp.GraineChatChannel
import ai.graine.inapp.kmp.GraineChatRows
import ai.graine.inapp.kmp.GraineChatSession
import ai.graine.inapp.kmp.GraineChatStatus
import ai.graine.inapp.kmp.GraineChatTurn
import ai.graine.inapp.kmp.GraineRole
import ai.graine.inapp.kmp.GraineVoiceInfo
import ai.graine.inapp.kmp.GraineComponent
import ai.graine.inapp.kmp.GraineMissedCall
import ai.graine.inapp.kmp.GraineSchedulingRules
import ai.graine.inapp.kmp.GraineVoiceStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Your own design": how the SDK's public state reads on the app's own screens, and what its own widgets answer. */
class OwnDesignTest {
    @Test
    fun chatStatusSaysDisconnectedAndOffersRetryOnlyOnceTheChatGaveUp() {
        assertEquals("Disconnected · send a message to continue", OwnText.chatStatus(GraineChatStatus.DISCONNECTED))
        assertEquals("Typing…", OwnText.chatStatus(GraineChatStatus.CONNECTED, typing = true))
        assertEquals("Online", OwnText.chatStatus(GraineChatStatus.CONNECTED))
        assertEquals("Reconnecting…", OwnText.chatStatus(GraineChatStatus.RECONNECTING))
        assertEquals("Couldn't reconnect · tap Retry", OwnText.chatStatus(GraineChatStatus.OFFLINE, failed = true))
        assertEquals("Offline · messages are sent when it reconnects", OwnText.chatStatus(GraineChatStatus.OFFLINE))
        assertTrue(OwnText.chatCanRetry(failed = true), "Retry once it gave up")
        assertFalse(OwnText.chatCanRetry(failed = false), "merely offline, or disconnected: it comes back, or a message continues it")
    }

    @Test
    fun voiceWordsRead() {
        assertEquals("Ready", OwnText.voiceStatus(GraineVoiceStatus.IDLE))
        assertEquals("Couldn't connect", OwnText.voiceStatus(GraineVoiceStatus.FAILED))
        assertEquals("Thinking…", OwnText.voiceStatus(GraineVoiceStatus.THINKING))
    }

    @Test
    fun callHeadlineFollowsTheCallsState() {
        assertEquals("Scheduled call", OwnText.callStatus(GraineCallInfo(state = GraineCallState.RINGING, incoming = true, hosted = true)))
        assertEquals("Incoming call", OwnText.callStatus(GraineCallInfo(state = GraineCallState.RINGING, incoming = true)))
        assertEquals("Calling…", OwnText.callStatus(GraineCallInfo(state = GraineCallState.CONNECTING)))
        assertEquals("Connecting…", OwnText.callStatus(GraineCallInfo(state = GraineCallState.CONNECTING, incoming = true)))
        assertEquals("Reconnecting…", OwnText.callStatus(GraineCallInfo(state = GraineCallState.ACTIVE, reconnecting = true)))
        assertEquals("On the call", OwnText.callStatus(GraineCallInfo(state = GraineCallState.ACTIVE)))
        val missed = GraineMissedCall("call_1", "Agent", null, hosted = false, at = Instant.fromEpochMilliseconds(0))
        assertEquals("Missed call", OwnText.callStatus(GraineCallInfo(missedCall = missed)))
        assertEquals("You ended the call", OwnText.endReason("local", "Asha"))
        assertEquals("Asha ended the call", OwnText.endReason("remote", "Asha"))
        assertEquals("The call dropped", OwnText.endReason("failed", "Asha"))
    }

    @Test
    fun durationsAndTimingsRead() {
        assertEquals("0:42", OwnText.duration(42))
        assertEquals("12:05", OwnText.duration(725))
        assertEquals("1:02:03", OwnText.duration(3723))
        assertEquals("0:42", OwnText.elapsed(Instant.fromEpochSeconds(100), Instant.fromEpochSeconds(142)))
        assertNull(OwnText.elapsed(null, Instant.fromEpochSeconds(142)), "no length before the call connected")
        assertEquals("—", OwnText.ms(null))
        assertEquals("312 ms", OwnText.ms(312))
        assertEquals("1.54 s", OwnText.ms(1540))
        assertEquals("0.12 %", OwnText.percent(0.123))
    }

    @Test
    fun theConnectionReportReadsLikeExecutionLogsAndEveryUnknownFactIsADash() {
        val report = GraineCallConnection(
            transport = GraineCallTransport.WEBRTC,
            prepared = false,
            credential = "direct",
            setupMs = 412,
            loginMs = 120,
            answerMs = 380,
            firstWordsMs = 910,
            tapToVoiceMs = 1702,
            audio = GraineCallConnection.Audio(lossPct = 0.4, jitterAvgMs = 3, jitterPeakMs = 12, rttMs = 48, quality = "good"),
            recoveries = 1,
        )
        assertEquals(
            listOf(
                "Transport" to "webrtc", "Prepared" to "no · set up after the tap", "Credential" to "direct",
                "Setup" to "412 ms", "Login" to "120 ms", "Answer" to "380 ms", "First words" to "910 ms", "Tap to voice" to "1.70 s",
                "Packet loss" to "0.40 %", "Jitter" to "avg 3 ms · peak 12 ms", "Round trip" to "48 ms", "Audio quality" to "good",
                "Recoveries" to "1",
            ),
            OwnText.connectionRows(report),
        )
        assertTrue(OwnText.connectionRows(null).all { it.second == "—" })
        assertEquals("yes · ready 30s before the tap", OwnText.connectionRows(GraineCallConnection(prepared = true, preparedAgeSeconds = 30))[1].second)
    }

    @Test
    fun whyBookingIsOffInPlainWords() {
        assertNull(OwnText.bookingUnavailable(null))
        listOf("not_loaded", "rings_not_set_up", "scheduling_off", "not_identified", "agent_off").forEach {
            val words = OwnText.bookingUnavailable(it)
            assertTrue(words != null && !words.contains("_"), "$it reads as words")
        }
        assertEquals("Booking is not available here (new reason).", OwnText.bookingUnavailable("new_reason"))
    }

    @Test
    fun bookingDaysAreTheUsersDaysThroughTheAgentsLimit() {
        // 20:00 UTC on 14 Oct is 01:30 on 15 Oct in Kolkata: the user's day is the 15th.
        val now = Instant.parse("2026-10-14T20:00:00Z")
        val days = OwnText.bookingDays(GraineSchedulingRules(maxDaysAhead = 2), now, TestClock.zone)
        assertEquals(listOf(LocalDate(2026, 10, 15), LocalDate(2026, 10, 16), LocalDate(2026, 10, 17)), days)
        assertEquals("Today", OwnText.dayLabel(days[0], days[0]))
        assertEquals("Tomorrow", OwnText.dayLabel(days[1], days[0]))
        assertEquals("Sat 17 Oct", OwnText.dayLabel(days[2], days[0]))
        val slots = listOf(TestClock.instant(17, 55), TestClock.instant(18), TestClock.instant(18, 5))
        assertEquals(listOf(17 to listOf(slots[0]), 18 to slots.drop(1)), OwnText.byHour(slots, TestClock.zone))
        assertEquals("6 pm", OwnText.hourLabel(18))
        assertEquals("12 am", OwnText.hourLabel(0))
        assertEquals("6:05", OwnText.slotLabel(slots[2], TestClock.zone))
        assertEquals(
            "From 5 minutes to 14 days ahead · up to 3 calls booked · times in Asia/Kolkata",
            OwnText.rulesLine(GraineSchedulingRules(), TestClock.zone),
        )
    }

    // ── components: what the app draws, and what each control answers ─────

    private fun component(kind: String, data: Map<String, Any?>, style: Map<String, Any?>? = null, fallback: String? = null) =
        GraineComponent("w1", kind, null, buildMap {
            put("data", data)
            style?.let { put("style", it) }
            fallback?.let { put("fallback_text", it) }
        })

    @Test
    fun quickActionsAndChoicesAreChipsAnsweredAsTheSdksViewsAnswer() {
        val quick = OwnComponents.read(component("quick_actions", mapOf("prompt" to "How is it going?", "actions" to listOf(mapOf("id" to "better", "label" to "Better")))))
        assertIs<OwnWidget.Chips>(quick)
        assertEquals("How is it going?", quick.prompt)
        val a = OwnComponents.chip(quick, quick.chips.single())
        assertEquals(mapOf("id" to "better", "label" to "Better"), a.data)
        assertEquals("I'll go with Better.", a.summary)

        val choices = OwnComponents.read(component("choices", mapOf("options" to listOf("Yes", mapOf("id" to "n", "title" to "No")))))
        assertIs<OwnWidget.Chips>(choices)
        assertEquals(listOf(OwnWidget.Chip("Yes", "Yes"), OwnWidget.Chip("n", "No")), choices.chips)
        assertEquals(mapOf("chosen" to "n", "label" to "No"), OwnComponents.chip(choices, choices.chips[1]).data)
    }

    @Test
    fun rowsAnswerOneItemOrSeveralAsOneAnswer() {
        val items = listOf(
            mapOf("id" to "a", "title" to "Option one", "subtitle" to "First", "price" to 499L, "facts" to listOf("Fast", "Light", "Small", "Extra")),
            mapOf("id" to "b", "title" to "Option two"),
            mapOf("id" to "c", "title" to "Option three", "unavailable" to true),
        )
        val one = OwnComponents.read(component("product_list", mapOf("title" to "Pick one", "items" to items)))
        assertIs<OwnWidget.Rows>(one)
        assertFalse(one.many)
        assertEquals("Choose", one.selectLabel)
        assertEquals("499", one.rows[0].price, "a whole number reads without a decimal point")
        assertEquals(listOf("Fast", "Light", "Small"), one.rows[0].facts, "at most three facts")
        assertTrue(one.rows[2].unavailable)
        val picked = OwnComponents.row(one, one.rows[0])
        assertEquals(mapOf("id" to "a", "title" to "Option one", "price" to "499"), picked.data)
        assertEquals("I'll take a look at Option one at 499.", picked.summary)

        val many = OwnComponents.read(component("product_list", mapOf("selectLabel" to "Done", "items" to items), style = mapOf("select" to "many", "maxSelect" to 2L)))
        assertIs<OwnWidget.Rows>(many)
        assertTrue(many.many)
        assertEquals(2, many.maxSelect)
        val both = OwnComponents.many(many, listOf("a", "b"))!!
        assertEquals("a,b", both.data["ids"])
        assertEquals("Option one, Option two", both.data["titles"])
        assertEquals(2, both.data["count"])
        assertEquals("Done: Option one and Option two", both.summary)
        assertNull(OwnComponents.many(many, emptyList()))
    }

    @Test
    fun plansAndInfoCardsAnswerInTheirOwnShape() {
        val plans = OwnComponents.read(component("plan_comparison", mapOf("plans" to listOf(mapOf("id" to "plus", "name" to "Plus", "price" to 499.0, "priceSuffix" to "/month", "facts" to listOf(mapOf("label" to "Seats", "value" to "5")))))))
        assertIs<OwnWidget.Rows>(plans)
        assertEquals(OwnWidget.Shape.PLANS, plans.shape)
        assertEquals("499/month", plans.rows[0].price)
        assertEquals(listOf("Seats 5"), plans.rows[0].facts)
        val plan = OwnComponents.row(plans, plans.rows[0])
        assertEquals(mapOf("id" to "plus", "name" to "Plus", "price" to "499/month"), plan.data)
        assertEquals("I'll take Plus at 499/month.", plan.summary)

        val card = OwnComponents.read(component("info_card", mapOf("title" to "About this", "summary" to "One line.", "actionLabel" to "Show me")))
        assertIs<OwnWidget.Rows>(card)
        assertEquals(OwnWidget.Shape.CARD, card.shape)
        val more = OwnComponents.row(card, card.rows.single())
        assertEquals(mapOf("action" to "Show me"), more.data)
        assertEquals("Show me", more.summary)
    }

    @Test
    fun calendarSlotsAnswerTheDayAndTheTime() {
        val cal = OwnComponents.read(component("calendar_slots", mapOf("title" to "When?", "note" to "Your time zone.", "days" to listOf(
            mapOf("id" to "today", "label" to "Today", "slots" to emptyList<String>()),
            mapOf("id" to "tomorrow", "label" to "Tomorrow", "slots" to listOf("9:00 AM", "6:00 PM")),
        ))))
        assertIs<OwnWidget.Calendar>(cal)
        assertEquals("Your time zone.", cal.prompt)
        assertEquals(listOf("Tomorrow"), cal.days.map { it.label }, "a day with no times is left out")
        val a = OwnComponents.slot(cal.days[0], "9:00 AM")
        assertEquals(mapOf("day" to "Tomorrow", "slot" to "9:00 AM"), a.data)
        assertEquals("Tomorrow at 9:00 AM, please.", a.summary)
    }

    @Test
    fun aFormNeedsItsRequiredFieldsAndListsWhatWasFilledIn() {
        val form = OwnComponents.read(component("lead_form", mapOf("title" to "Details", "submitLabel" to "Send", "fields" to listOf(
            mapOf("name" to "full_name", "label" to "Name", "required" to true),
            mapOf("name" to "note", "label" to "Note", "type" to "text"),
        ))))
        assertIs<OwnWidget.Form>(form)
        assertFalse(OwnComponents.formComplete(form, mapOf("note" to "x")))
        assertTrue(OwnComponents.formComplete(form, mapOf("full_name" to "A Person")))
        val a = OwnComponents.form(form, mapOf("full_name" to " A Person "))
        assertEquals(mapOf("full_name" to "A Person", "note" to ""), a.data)
        assertEquals("full name: A Person", a.summary)
    }

    @Test
    fun aKindTheAppCannotDrawShowsTheComponentsOwnSentence() {
        val w = OwnComponents.read(component("map_view", mapOf("lat" to 1.0), fallback = "Here is what I found."))
        assertIs<OwnWidget.Fallback>(w)
        assertEquals("Here is what I found.", w.text)
    }

    // ── sessions, the end of a call, and Talk's word ─────────────────────

    private fun turn(id: String, session: String?, voice: Boolean = false, minute: Int = 0) =
        GraineChatTurn(id, if (voice) GraineRole.AGENT else GraineRole.USER, "x", TestClock.instant(10, minute), session = session,
            channel = if (voice) GraineChatChannel.VOICE else GraineChatChannel.CHAT)

    @Test
    fun aSessionStartsAtItsFirstTurnsRowWithTheLineAboveIt() {
        val turns = listOf(turn("a", null), turn("b", null), turn("c", "s2", voice = true, minute = 1), turn("d", "s3", minute = 2), turn("e", "s3", minute = 3))
        val sessions = listOf(
            GraineChatSession(null, turns[0].at, turns[1].at, GraineChatChannel.CHAT, 2, false),
            GraineChatSession("s2", turns[2].at, turns[2].at, GraineChatChannel.VOICE, 1, false),
            GraineChatSession("s3", turns[3].at, turns[4].at, GraineChatChannel.CHAT, 2, true),
        )
        assertEquals(listOf(0, 2, 3), sessions.indices.map { OwnText.sessionStart(sessions, it) })
        val rows = GraineChatRows.build(turns, listOf("d"), now = TestClock.instant(12), zone = TestClock.zone)
        // turn a, turn b, the call row (c), "new conversation" before d, turn d, turn e — all today.
        assertEquals(0, OwnText.sessionRow(rows, 0))
        assertEquals(2, OwnText.sessionRow(rows, 2), "the call's row")
        assertEquals(3, OwnText.sessionRow(rows, 3), "with the new-conversation line above its first bubble")
        assertEquals("Voice call · 1 message", OwnText.sessionKind(sessions[1]))
        assertEquals("Chat · 2 messages", OwnText.sessionKind(sessions[2]))
    }

    @Test
    fun theEndCauseReadsInWords() {
        assertEquals("Hung up on this phone", OwnText.endCause("local_end", "Asha"))
        assertEquals("Asha's side hung up", OwnText.endCause("remote_bye", "Asha"))
        assertEquals("The audio dropped and did not recover", OwnText.endCause("media_drop", "Asha"))
        assertEquals("The phone's call system reset the call", OwnText.endCause("provider_reset", "Asha"))
        assertEquals("Some new cause", OwnText.endCause("some_new_cause", "Asha"))
        assertNull(OwnText.endCause(null, "Asha"))
    }

    @Test
    fun talkIsLiveOnlyWhileAConversationIsConnectedOrOnItsWay() {
        // A "connecting" word with nothing connected or connecting is not a conversation: not live, shown as Ready.
        val unloaded = GraineVoiceInfo(status = GraineVoiceStatus.CONNECTING)
        assertFalse(OwnText.voiceLive(unloaded))
        assertEquals(GraineVoiceStatus.IDLE, OwnText.voiceWord(unloaded))
        val starting = GraineVoiceInfo(connecting = true, status = GraineVoiceStatus.CONNECTING)
        assertTrue(OwnText.voiceLive(starting))
        assertEquals(GraineVoiceStatus.CONNECTING, OwnText.voiceWord(starting))
        assertTrue(OwnText.voiceLive(GraineVoiceInfo(connected = true, status = GraineVoiceStatus.SPEAKING)))
        assertEquals(GraineVoiceStatus.ENDED, OwnText.voiceWord(GraineVoiceInfo(status = GraineVoiceStatus.ENDED)))
    }
}

