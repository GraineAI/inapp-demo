package ai.graine.demo

import ai.graine.demo.domain.DemoActionResult
import ai.graine.demo.domain.DemoAgent
import ai.graine.demo.domain.DemoScreenContext
import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.graine.CallEvent
import ai.graine.demo.graine.GraineClient
import ai.graine.demo.ui.KeyValueStore
import ai.graine.inapp.GraineCallStatus
import ai.graine.inapp.GraineError
import ai.graine.inapp.GraineMemory
import ai.graine.inapp.GraineScheduledCall
import ai.graine.inapp.GraineSchedulingRules
import android.app.Activity
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** A fixed zone and clock, so every test reads the same day. */
object TestClock {
    val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    /** 14 Oct 2026 at hour:minute, Asia/Kolkata, in ms. */
    fun at(hour: Int, minute: Int = 0, day: Int = 14): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }.timeInMillis

    fun date(hour: Int, minute: Int = 0, day: Int = 14) = Date(at(hour, minute, day))
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

/** A stand-in for the SDK with up to two agents: records every call, answers what the test says (at once, as the SDK would on the main thread). */
class FakeGraineClient : GraineClient {
    override var agents: List<DemoAgent> = listOf(DemoAgent.MAIN)

    val identified = mutableListOf<Pair<Map<String, String>?, Boolean?>>()
    val memorySets = mutableListOf<Boolean>()
    override val installId = "0f8b2a4c-1d3e-4f5a-9b6c-7d8e9f0a1b2c"
    var forgetResult: Pair<Int?, GraineError?> = 0 to null
    var forgetCount = 0
    var memoriesResult: Pair<List<GraineMemory>?, GraineError?> = emptyList<GraineMemory>() to null
    override var agentMemoryEnabled: Boolean? = true

    val screens = mutableListOf<DemoScreenContext?>()
    /** Registered actions, per agent. */
    val actions = mutableMapOf<DemoAgent, MutableMap<String, (Map<String, Any?>) -> DemoActionResult>>()
    var unregisterCount = 0

    val rules = mutableMapOf(DemoAgent.MAIN to GraineSchedulingRules(usersCanBook = true), DemoAgent.HELP to GraineSchedulingRules(usersCanBook = true))
    var scheduleResult: Pair<GraineScheduledCall?, GraineError?> = GraineScheduledCall("c1", Date()) to null
    val scheduled = mutableListOf<Triple<Date, String?, DemoAgent>>()
    val listResults = mutableMapOf<DemoAgent, Pair<List<GraineScheduledCall>?, GraineError?>>()
    val cancelled = mutableListOf<Pair<String, DemoAgent>>()
    var cancelResult: Pair<GraineCallStatus?, GraineError?> = GraineCallStatus.CANCELLED to null

    val voiceStarts = mutableListOf<DemoAgent>()
    var voiceEnds = 0
    val chatMessages = mutableListOf<Pair<String, DemoAgent>>()
    val callNames = mutableListOf<String>()
    var nativeCallId: String? = "call_1"
    val nativeCalls = mutableListOf<Triple<String, Map<String, String>, DemoAgent>>()

    override var onSessionReady: (() -> Unit)? = null
    override var onCallEvent: ((CallEvent) -> Unit)? = null

    override fun identify(traits: Map<String, String>?, memory: Boolean?) { identified += traits to memory }
    override fun setMemory(enabled: Boolean) { memorySets += enabled }
    override fun forgetMemory(done: (Int?, GraineError?) -> Unit) { forgetCount++; done(forgetResult.first, forgetResult.second) }
    override fun memories(done: (List<GraineMemory>?, GraineError?) -> Unit) = done(memoriesResult.first, memoriesResult.second)
    override fun setScreen(context: DemoScreenContext?) { screens += context }
    override fun registerAction(name: String, agent: DemoAgent, handler: (Map<String, Any?>) -> DemoActionResult) {
        actions.getOrPut(agent) { mutableMapOf() }[name] = handler
    }
    override fun unregisterActions() { unregisterCount++; actions.clear() }
    override fun schedulingRules(agent: DemoAgent): GraineSchedulingRules = rules[agent] ?: GraineSchedulingRules()
    override fun scheduleCall(at: Date, reason: String?, agent: DemoAgent, done: (GraineScheduledCall?, GraineError?) -> Unit) {
        scheduled += Triple(at, reason, agent)
        done(scheduleResult.first, scheduleResult.second)
    }
    override fun scheduledCalls(agent: DemoAgent, done: (List<GraineScheduledCall>?, GraineError?) -> Unit) {
        val r = listResults[agent] ?: (emptyList<GraineScheduledCall>() to null)
        done(r.first, r.second)
    }
    override fun cancelScheduledCall(id: String, agent: DemoAgent, done: (GraineCallStatus?, GraineError?) -> Unit) {
        cancelled += id to agent
        done(cancelResult.first, cancelResult.second)
    }
    override fun startVoice(agent: DemoAgent) { voiceStarts += agent }
    override fun endVoice() { voiceEnds++ }
    override fun sendChatMessage(text: String, agent: DemoAgent) { chatMessages += text to agent }
    override fun setCallName(name: String) { callNames += name }
    override fun startNativeCall(activity: Activity, reason: String, variables: Map<String, String>, agent: DemoAgent): String? {
        nativeCalls += Triple(reason, variables, agent)
        return nativeCallId
    }

    /** Run a registered action as an agent would (the main one unless named). */
    fun run(name: String, args: Map<String, Any?> = emptyMap(), agent: DemoAgent = DemoAgent.MAIN): DemoActionResult? =
        actions[agent]?.get(name)?.invoke(args)

    val lastScreen: DemoScreenContext? get() = screens.lastOrNull()
}
