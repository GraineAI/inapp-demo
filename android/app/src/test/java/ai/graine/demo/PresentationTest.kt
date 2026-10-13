package ai.graine.demo

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.Booking
import ai.graine.demo.domain.CallLog
import ai.graine.demo.domain.CallLogEntry
import ai.graine.demo.domain.CallText
import ai.graine.demo.domain.DemoFormat
import ai.graine.demo.domain.Tone
import ai.graine.demo.notify.NotificationAccess
import ai.graine.inapp.GraineCallSource
import ai.graine.inapp.GraineCallStatus
import ai.graine.inapp.GraineError
import ai.graine.inapp.GraineErrorCode
import ai.graine.inapp.GraineScheduledCall
import ai.graine.inapp.GraineSchedulingRules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The app only presents scheduled calls; the SDK owns their rules. These pin the presentation. */
class CallTextTest {
    private lateinit var savedZone: TimeZone

    @Before fun zone() { savedZone = TimeZone.getDefault(); TimeZone.setDefault(TestClock.zone) }
    @After fun restore() { TimeZone.setDefault(savedZone) }

    @Test fun everyStatusHasALabelAndTone() {
        assertEquals(
            listOf("Scheduled", "Ringing", "Answered", "Completed", "Declined", "Missed", "Failed", "Cancelled", "Expired", "No device"),
            GraineCallStatus.KNOWN.map(CallText::label),
        )
        assertEquals(Tone.POSITIVE, CallText.tone(GraineCallStatus.COMPLETED))
        assertEquals(Tone.WARNING, CallText.tone(GraineCallStatus.MISSED))
        assertEquals(Tone.WARNING, CallText.tone(GraineCallStatus.NO_DEVICE))
        assertEquals(Tone.NEGATIVE, CallText.tone(GraineCallStatus.FAILED))
        assertEquals(Tone.INFO, CallText.tone(GraineCallStatus.SCHEDULED))
        assertEquals("On hold", CallText.label(GraineCallStatus.from("on_hold")))
        assertEquals(Tone.NEUTRAL, CallText.tone(GraineCallStatus.from("on_hold")))
    }

    @Test fun bookedBy() {
        assertEquals("Booked by Asha", CallText.bookedBy(GraineCallSource.AGENT, "Asha"))
        assertEquals("Booked in the app", CallText.bookedBy(GraineCallSource.APP, "Asha"))
        assertEquals("Booked by the team", CallText.bookedBy(GraineCallSource.DASHBOARD, "Asha"))
        assertNull(CallText.bookedBy(null, "Asha"))
    }

    @Test fun splitAndNextUseTheSDKsIdeaOfUpcoming() {
        val calls = listOf(
            GraineScheduledCall("e", TestClock.date(10), GraineCallStatus.RINGING),
            GraineScheduledCall("b", TestClock.date(12), GraineCallStatus.SCHEDULED),
            GraineScheduledCall("f", null, GraineCallStatus.SCHEDULED),
            GraineScheduledCall("c", TestClock.date(9), GraineCallStatus.COMPLETED),
            GraineScheduledCall("d", TestClock.date(8), GraineCallStatus.MISSED),
        )
        val (upcoming, past) = CallText.split(calls)
        assertEquals(listOf("e", "b", "f"), upcoming.map { it.id })
        assertEquals(listOf("c", "d"), past.map { it.id })
        assertEquals("e", CallText.next(calls)?.id)
    }

    @Test fun suggestedTimeIsAtLeastHalfAnHourOutAndNeverBeforeTheEarliest() {
        assertEquals(TestClock.at(10, 45), CallText.suggestedTime(TestClock.at(10, 7), GraineSchedulingRules(usersCanBook = true)))
        assertEquals(TestClock.at(12, 15), CallText.suggestedTime(TestClock.at(10, 7), GraineSchedulingRules(usersCanBook = true, minLeadMinutes = 120)))
        assertEquals(TestClock.at(10, 0), CallText.suggestedTime(TestClock.at(9, 30), GraineSchedulingRules(usersCanBook = true)))
    }

    @Test fun refusalsInWords() {
        val rules = GraineSchedulingRules(usersCanBook = true, minLeadMinutes = 15, maxDaysAhead = 7, maxPendingPerUser = 2)
        fun say(code: GraineErrorCode, message: String = "x") = CallText.message(GraineError(code, message), rules)
        assertEquals("Choose a time at least 15 minutes from now.", say(GraineErrorCode.TOO_SOON))
        assertEquals("Choose a time within the next 7 days.", say(GraineErrorCode.TOO_FAR))
        assertEquals("You already have 2 upcoming calls. Cancel one to book another.", say(GraineErrorCode.TOO_MANY_PENDING))
        assertEquals(CallText.SCHEDULING_OFF, say(GraineErrorCode.SCHEDULING_OFF))
        assertTrue(say(GraineErrorCode.OUTSIDE_WINDOW).contains("calling hours"))
        assertEquals("Try again shortly.", say(GraineErrorCode.UNAVAILABLE, "Try again shortly."))
        assertEquals("Something went wrong. Try again.", say(GraineErrorCode.from("new_code"), " "))
        assertEquals("No connection. Check your network and try again.", CallText.message(null))
    }

    @Test fun times() {
        val now = TestClock.at(10, 7)
        assertEquals("Today", DemoFormat.dayWord(TestClock.at(18), now, TestClock.zone))
        assertEquals("Tomorrow", DemoFormat.dayWord(TestClock.at(9, day = 15), now, TestClock.zone))
        assertEquals("Yesterday", DemoFormat.dayWord(TestClock.at(9, day = 13), now, TestClock.zone))
        assertNull(DemoFormat.dayWord(TestClock.at(9, day = 20), now, TestClock.zone))
        assertEquals("Time not available", DemoFormat.time(null, now))
        assertEquals("Today, 18:00", DemoFormat.time(TestClock.date(18), now, TestClock.zone, Locale.UK))
        // 18:00 in Kolkata is 12:30 UTC.
        assertEquals("2026-10-14T12:30:00Z", DemoFormat.iso(TestClock.at(18)))
    }
}

/** The booking sheet's times: quick-time chips, the 5-minute grid, the SDK's bounds, the zone shown. */
class BookingTest {
    private val now = TestClock.at(10, 7)
    private val rules = GraineSchedulingRules(usersCanBook = true, minLeadMinutes = 5, maxDaysAhead = 14)
    private val quick = TestPack.pack.call.quickTimes

    @Test fun quickTimesResolveInTheDevicesZoneOnTheGrid() {
        // 10:07 + 15 minutes = 10:22, up to the grid: 10:25.
        assertEquals(TestClock.at(10, 25), Booking.resolve(quick[0], now, TestClock.zone))
        assertEquals(TestClock.at(10, 0, day = 15), Booking.resolve(quick[1], now, TestClock.zone))
        assertEquals(TestClock.at(19, 0), Booking.resolve(quick[2], now, TestClock.zone))
        assertNull(Booking.resolve(ai.graine.demo.domain.UseCasePack.QuickTime("Soon"), now, TestClock.zone))
        assertNull(Booking.resolve(ai.graine.demo.domain.UseCasePack.QuickTime("Bad", day = "someday", time = "10:00"), now, TestClock.zone))
        assertNull(Booking.resolve(ai.graine.demo.domain.UseCasePack.QuickTime("Bad", day = "today", time = "25:00"), now, TestClock.zone))
    }

    @Test fun aChipOutsideTheSDKsBoundsIsNotBookable() {
        val late = TestClock.at(20, 30)
        assertFalse(Booking.isBookable(Booking.resolve(quick[2], late, TestClock.zone)!!, late, rules))
        assertTrue(Booking.isBookable(Booking.resolve(quick[1], late, TestClock.zone)!!, late, rules))
        val strict = GraineSchedulingRules(usersCanBook = true, minLeadMinutes = 30)
        assertFalse(Booking.isBookable(Booking.resolve(quick[0], now, TestClock.zone)!!, now, strict))
    }

    @Test fun thePickersRangeIsTheSDKsBoundsOnTheGrid() {
        val range = Booking.range(now, rules)
        assertEquals(TestClock.at(10, 15), range.first)
        assertEquals(TestClock.at(10, 5, day = 28), range.last)
        assertEquals(range.first, Booking.clamp(TestClock.at(9), range))
    }

    @Test fun snappingAndCombining() {
        assertEquals(TestClock.at(10, 5), Booking.snapped(TestClock.at(10, 7)))
        assertEquals(TestClock.at(10, 10), Booking.snapped(TestClock.at(10, 8)))
        assertEquals(TestClock.at(10, 15), Booking.ceilToStep(TestClock.at(10, 11)))
        val day = Booking.utcDayOf(TestClock.at(23, 50), TestClock.zone)
        assertEquals(TestClock.at(18, 30), Booking.combine(day, 18, 30, TestClock.zone))
    }

    @Test fun theZoneIsShownWithTheTime() {
        assertEquals("Asia/Kolkata · 18:00", Booking.zoneLabel(TestClock.at(18), TestClock.zone, Locale.UK))
    }
}

class CallLogTest {
    @Test fun newestFirstCappedAndKept() {
        var log = CallLog()
        repeat(CallLog.CAP + 5) { log = log.record(CallLogEntry(callId = "c$it", event = "missed", at = TestClock.at(10))) }
        assertEquals(CallLog.CAP, log.entries.size)
        assertEquals("c${CallLog.CAP + 4}", log.entries.first().callId)
        assertEquals(log, CallLog.fromJson(log.toJson()))
        assertEquals(CallLog(), CallLog.fromJson(null))
        assertEquals(CallLog(), CallLog.fromJson("not json"))
    }

    @Test fun descriptionsUseTheAgentsName() {
        fun d(e: String, detail: String? = null) = CallLog.describe(e, detail, "Kabir")
        assertEquals("You called Kabir", d("placed"))
        assertEquals("Kabir called (scheduled call)", d("incoming", "scheduled"))
        assertEquals("Kabir called", d("incoming"))
        assertEquals("Ended by you", d("ended", "local"))
        assertEquals("Ended by Kabir", d("ended", "remote"))
        assertEquals("Call dropped", d("ended", "failed"))
        assertEquals("Could not connect: Microphone denied", d("failed", "Microphone denied"))
        assertEquals("Could not connect", d("failed"))
        assertEquals("Missed", d("missed"))
    }
}

class SetupTest {
    @Test fun configDefaultsTheServer() {
        assertEquals(AppConfig("https://www.graine.ai", ""), AppConfig.of(null, null))
        assertFalse(AppConfig.of("", "").hasKey)
        assertTrue(AppConfig.of("http://10.0.2.2:3000", " pk_live_x ").hasKey)
        assertEquals("pk_live_x", AppConfig.of(null, " pk_live_x ").publishableKey)
        assertFalse(AppConfig.of(null, "pk_live_x").hasHelpKey)
        assertTrue(AppConfig.of(null, "pk_live_x", " pk_live_help ").hasHelpKey)
        assertEquals("pk_live_help", AppConfig.of(null, "pk_live_x", " pk_live_help ").helpPublishableKey)
    }

    @Test fun whatAPhoneNeedsBeforeACallCanRingIt() {
        assertEquals(emptyList<NotificationAccess.Step>(), NotificationAccess.missing(31, notificationsAllowed = false, fullScreenAllowed = false))
        assertEquals(listOf(NotificationAccess.Step.ALLOW_NOTIFICATIONS), NotificationAccess.missing(33, notificationsAllowed = false, fullScreenAllowed = false))
        assertEquals(
            listOf(NotificationAccess.Step.ALLOW_NOTIFICATIONS, NotificationAccess.Step.ALLOW_FULL_SCREEN),
            NotificationAccess.missing(34, notificationsAllowed = false, fullScreenAllowed = false),
        )
        assertEquals(listOf(NotificationAccess.Step.ALLOW_FULL_SCREEN), NotificationAccess.missing(36, notificationsAllowed = true, fullScreenAllowed = false))
        assertEquals(emptyList<NotificationAccess.Step>(), NotificationAccess.missing(36, notificationsAllowed = true, fullScreenAllowed = true))
    }
}
