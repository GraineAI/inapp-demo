package ai.graine.demo

import ai.graine.demo.domain.DemoActionResult
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoScreenContext
import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.graine.CallEvent
import ai.graine.demo.graine.GraineClient
import ai.graine.demo.platform.KeyValueStore
import ai.graine.inapp.kmp.GraineCallSource
import ai.graine.inapp.kmp.GraineCallStatus
import ai.graine.inapp.kmp.GraineMemory
import ai.graine.inapp.kmp.GraineScheduledCall
import ai.graine.inapp.kmp.GraineSchedulingRules
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** A fixed zone and clock, so every test reads the same day. */
object TestClock {
    val zone: TimeZone = TimeZone.of("Asia/Kolkata")

    /** 14 Oct 2026 at hour:minute, Asia/Kolkata, in ms. */
    fun at(hour: Int, minute: Int = 0, day: Int = 14): Long = LocalDateTime(2026, 10, day, hour, minute).toInstant(zone).toEpochMilliseconds()

    fun instant(hour: Int, minute: Int = 0, day: Int = 14): Instant = Instant.fromEpochMilliseconds(at(hour, minute, day))
}

/** A small pack in the shared schema, written for the tests (the shipped packs are checked separately). */
object TestPack {
    val json = """
    {
      "schemaVersion": 1, "id": "test-pack", "appTitle": "Test Care", "agentName": "Asha", "agentRole": "care coach",
      "tagline": "A test pack.", "notice": "Not medical advice.", "accent": "#0F766E",
      "identifierField": "patient_id", "identifierLabel": "Patient ID", "userNoun": "patient",
      "users": [
        { "id": "P-1", "name": "Riya Sharma", "traits": { "plan": "Diabetes care" } },
        { "id": "P-2", "name": "Arjun Mehta", "traits": { "plan": "Heart health" } }
      ],
      "home": { "title": "Today", "cards": [ { "id": "plan", "title": "Medicines", "body": "Due today.", "target": "items" } ] },
      "items": {
        "title": "Medicines", "screenName": "medicines", "itemNoun": "medicine", "emptyText": "No medicines.",
        "rows": [
          { "id": "metformin", "title": "Metformin 500 mg", "subtitle": "Twice a day", "status": "Due 8:00 pm",
            "fields": [ { "name": "next_dose", "label": "Next dose", "value": "8:00 pm" } ], "actions": ["mark_done"], "userIds": ["P-1"] },
          { "id": "atorvastatin", "title": "Atorvastatin 10 mg", "subtitle": "At bedtime",
            "fields": [ { "name": "supply", "label": "Supply", "value": "9 days left" } ], "actions": ["mark_done"], "userIds": ["P-1"] },
          { "id": "aspirin", "title": "Aspirin 75 mg", "subtitle": "After lunch", "status": "Due 2:00 pm",
            "fields": [], "actions": ["mark_done"], "userIds": ["P-2"] },
          { "id": "vitamin-d3", "title": "Vitamin D3", "subtitle": "Weekly", "fields": [], "actions": [] }
        ]
      },
      "actions": [
        { "name": "open_screen", "effect": "open_screen", "label": "Open", "description": "Open a screen.",
          "args": [ { "name": "screen", "type": "string", "required": true, "description": "The screen." } ] },
        { "name": "open_item", "effect": "open_item", "label": "Details", "description": "Open a medicine.",
          "args": [ { "name": "item", "type": "string", "required": true, "description": "The medicine." } ] },
        { "name": "mark_done", "effect": "set_status", "label": "Mark as taken", "status": "Taken today", "description": "Record a dose.",
          "args": [ { "name": "item", "type": "string", "required": true, "description": "The medicine." } ] },
        { "name": "pause_reminders", "effect": "set_status", "label": "Pause reminders", "status": "Paused", "description": "Pause.",
          "args": [ { "name": "item", "type": "string", "required": true, "description": "The medicine." } ] },
        { "name": "teleport", "effect": "beam", "label": "Teleport", "description": "An effect from a newer pack.", "args": [] }
      ],
      "call": { "callName": "Asha · Test Care", "title": "Check-in calls", "bookLabel": "Schedule a consultation call",
        "reasons": ["Weekly check-in", "Medicine review"],
        "quickTimes": [ { "label": "In 15 minutes", "inMinutes": 15 }, { "label": "Tomorrow 10:00", "day": "tomorrow", "time": "10:00" },
                        { "label": "Today 19:00", "day": "today", "time": "19:00" } ],
        "placeReason": "patient_question" },
      "memoryExamples": [ { "tell": "I take metformin at 8 pm.", "ask": "What do I take in the evening?", "expect": "Metformin at 8 pm." } ],
      "starters": ["What is due today?"]
    }
    """.trimIndent()

    val pack: UseCasePack get() = UseCasePack.parse(json)

    /** A second pack, to switch to. */
    val other: UseCasePack
        get() = UseCasePack.parse(
            json.replace("\"id\": \"test-pack\"", "\"id\": \"other-pack\"")
                .replace("Asha · Test Care", "Kabir · Test Bank")
                .replace("\"agentName\": \"Asha\"", "\"agentName\": \"Kabir\""),
        )
}

class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, String?>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String?) { values[key] = value }
}

/** A stand-in for the SDK with up to two agents: records every call, answers what the test says. */
class FakeGraineClient : GraineClient {
    override var agents: List<DemoAgent> = listOf(DemoAgent.MAIN)

    val identified = mutableListOf<Pair<Map<String, String>?, Boolean?>>()
    val memorySets = mutableListOf<Boolean>()
    override val installId = "0f8b2a4c-1d3e-4f5a-9b6c-7d8e9f0a1b2c"
    var forgetResult: Result<Int> = Result.success(0)
    var forgetCount = 0
    var memoriesResult: Result<List<GraineMemory>> = Result.success(emptyList())
    override var agentMemoryEnabled: Boolean? = true

    val screens = mutableListOf<DemoScreenContext?>()
    /** Registered actions, per agent. */
    val actions = mutableMapOf<DemoAgent, MutableMap<String, (Map<String, Any?>) -> DemoActionResult>>()
    var unregisterCount = 0

    /** bookingAvailable, per agent, as the SDK answers it now. */
    val bookable = mutableMapOf(DemoAgent.MAIN to true, DemoAgent.HELP to true)
    /** What the SDK's booking sheet returns: the booked call, or null when the person closed it. */
    var bookingResult: Result<GraineScheduledCall?> = Result.success(GraineScheduledCall("c1", TestClock.instant(18), GraineCallStatus.Scheduled))
    /** Every sheet opened: the reason it was opened with, and whose. */
    val bookings = mutableListOf<Pair<String?, DemoAgent>>()
    val listResults = mutableMapOf<DemoAgent, Result<List<GraineScheduledCall>>>()
    val cancelled = mutableListOf<Pair<String, DemoAgent>>()
    var cancelResult: Result<GraineCallStatus> = Result.success(GraineCallStatus.Cancelled)

    val voiceStarts = mutableListOf<DemoAgent>()
    var voiceEnds = 0
    val chatMessages = mutableListOf<Pair<String, DemoAgent>>()
    val callNames = mutableListOf<String>()
    var nativeCallId: String? = "call_1"
    val nativeCalls = mutableListOf<Triple<String, Map<String, String>, DemoAgent>>()

    override var onSessionReady: (() -> Unit)? = null
    override var onCallEvent: ((CallEvent) -> Unit)? = null

    override fun identify(traits: Map<String, String>?, memory: Boolean?) { identified += traits to memory }
    val variables = mutableListOf<Map<String, String?>>()
    /** What the SDK would hold: every setVariables merged, null removing. */
    val held = LinkedHashMap<String, String>()
    override fun setVariables(values: Map<String, String?>) {
        variables += values
        values.forEach { (k, v) -> if (v == null) held.remove(k) else held[k] = v }
    }
    override fun currentVariables(): Map<String, String> = LinkedHashMap(held)
    override fun setMemory(enabled: Boolean) { memorySets += enabled }
    override suspend fun forgetMemory(): Int { forgetCount++; return forgetResult.getOrThrow() }
    override suspend fun memories(): List<GraineMemory> = memoriesResult.getOrThrow()
    override fun setScreen(context: DemoScreenContext?) { screens += context }
    override fun registerAction(name: String, agent: DemoAgent, handler: (Map<String, Any?>) -> DemoActionResult) {
        actions.getOrPut(agent) { mutableMapOf() }[name] = handler
    }
    override fun unregisterActions() { unregisterCount++; actions.clear() }
    override fun bookingAvailable(agent: DemoAgent): Boolean = bookable[agent] ?: false
    override suspend fun presentBooking(reason: String?, agent: DemoAgent): GraineScheduledCall? {
        bookings += reason to agent
        return bookingResult.getOrThrow()
    }
    override suspend fun scheduledCalls(agent: DemoAgent): List<GraineScheduledCall> = (listResults[agent] ?: Result.success(emptyList())).getOrThrow()
    override suspend fun cancelScheduledCall(id: String, agent: DemoAgent): GraineCallStatus {
        cancelled += id to agent
        return cancelResult.getOrThrow()
    }

    /** bookingUnavailableReason, per agent, when the test sets one; else none while bookable, `not_identified` otherwise. */
    val unavailable = mutableMapOf<DemoAgent, String?>()
    override fun bookingUnavailableReason(agent: DemoAgent): String? =
        if (agent in unavailable) unavailable[agent] else if (bookingAvailable(agent)) null else "not_identified"
    var rules = GraineSchedulingRules(usersCanBook = true, minLeadMinutes = 5, maxDaysAhead = 2, maxPendingPerUser = 3)
    override fun schedulingRules(agent: DemoAgent): GraineSchedulingRules = rules
    /** The times the SDK offers on a day: the test's own. */
    var slots: (LocalDate) -> List<Instant> = { emptyList() }
    override fun bookingSlots(day: LocalDate, agent: DemoAgent): List<Instant> = slots(day)
    /** Every scheduleCall: when, why, whose. */
    val scheduled = mutableListOf<Triple<Instant, String?, DemoAgent>>()
    /** What scheduleCall answers; null books the call asked for. */
    var scheduleResult: Result<GraineScheduledCall>? = null
    override suspend fun scheduleCall(at: Instant, reason: String?, agent: DemoAgent): GraineScheduledCall {
        scheduled += Triple(at, reason, agent)
        return scheduleResult?.getOrThrow() ?: GraineScheduledCall("s${scheduled.size}", at, GraineCallStatus.Scheduled, GraineCallSource.App, reason)
    }
    /** Every useOwnCallScreen, in order. */
    val ownCallScreens = mutableListOf<Boolean>()
    override fun useOwnCallScreen(own: Boolean) { ownCallScreens += own }
    override fun startVoice(agent: DemoAgent) { voiceStarts += agent }
    override fun endVoice() { voiceEnds++ }
    override fun sendChatMessage(text: String, agent: DemoAgent) { chatMessages += text to agent }
    override fun setCallName(name: String) { callNames += name }
    override fun startNativeCall(reason: String, variables: Map<String, String>, agent: DemoAgent): String? {
        nativeCalls += Triple(reason, variables, agent)
        return nativeCallId
    }

    /** Run a registered action as an agent would (the main one unless named). */
    fun run(name: String, args: Map<String, Any?> = emptyMap(), agent: DemoAgent = DemoAgent.MAIN): DemoActionResult? =
        actions[agent]?.get(name)?.invoke(args)

    val lastScreen: DemoScreenContext? get() = screens.lastOrNull()
}
