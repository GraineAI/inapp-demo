package ai.graine.demo

import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.journey.CallPermissionAnswer
import ai.graine.demo.journey.JourneyAgent
import ai.graine.demo.journey.JourneyAnswer
import ai.graine.demo.journey.JourneyModel
import ai.graine.demo.journey.JourneySdk
import ai.graine.demo.journey.JourneyShare
import ai.graine.demo.journey.Reminder
import ai.graine.demo.journey.ReminderScheduler
import ai.graine.demo.journey.TextStore
import ai.graine.inapp.kmp.GraineBookingState
import ai.graine.inapp.kmp.GraineCallInfo
import ai.graine.inapp.kmp.GraineCallOutcome
import ai.graine.inapp.kmp.GraineCallSource
import ai.graine.inapp.kmp.GraineCallStatus
import ai.graine.inapp.kmp.GraineChatStatus
import ai.graine.inapp.kmp.GraineErrorCode
import ai.graine.inapp.kmp.GraineException
import ai.graine.inapp.kmp.GraineMemory
import ai.graine.inapp.kmp.GraineScheduledCall
import ai.graine.inapp.kmp.GraineSchedulingRules
import ai.graine.inapp.kmp.GraineScreen
import ai.graine.inapp.kmp.GraineSessionSummary
import ai.graine.inapp.kmp.GraineSessionTranscript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * A JOURNEY THAT IS NOT HEALTH: a learning app's kickoff call and study plan, in the same schema as the shipped
 * pack's — the journey's code reads it exactly as it reads the care coach's, which is the point.
 */
object TestJourney {
    val block = """
    "journey": {
      "identifierField": "user_id",
      "title": "Get started", "summary": "A profile, a kickoff call and a study week.",
      "agents": { "coach": { "name": "Ari", "role": "study coach" }, "firstCall": { "name": "Ari", "role": "kickoff" }, "support": { "name": "Support", "role": "help" } },
      "actions": { "saveIntake": "save_kickoff", "getIntake": "get_kickoff", "savePlan": "save_week", "getPlan": "get_week", "swapItem": "change_item", "logCheckIn": "log_progress" },
      "intakeRequired": ["level"],
      "events": { "firstCallFinished": "kickoff_done", "firstCallFinishedDetail": "Build the first study week", "checkIn": "lesson_check_in",
                  "itemDone": "item_done", "itemSwapped": "item_changed", "weeklyCheckIn": "weekly_score" },
      "onboarding": {
        "welcome": { "title": "Welcome", "body": "Three questions, then a call." },
        "profile": { "title": "About you", "body": "So Ari never asks.", "fields": [
          { "key": "name", "label": "Name", "type": "text", "required": true, "maxLength": 40 },
          { "key": "level", "label": "Level", "type": "choice", "variable": "level", "required": true,
            "options": [ { "value": "beginner", "label": "Beginner" }, { "value": "advanced", "label": "Advanced" } ] },
          { "key": "born", "label": "Born", "type": "date", "variable": "age", "required": true, "min": 13, "max": 99 },
          { "key": "hours", "label": "Hours a week", "type": "number", "unit": "h", "variable": "hours_week", "required": true, "min": 1, "max": 40 },
          { "key": "notes", "label": "Notes", "type": "text", "variable": "notes", "required": false, "maxLength": 60 }
        ] },
        "language": { "title": "Language", "body": "Ari speaks it.", "variable": "agent_language", "options": [
          { "id": "en", "label": "English", "code": "en" },
          { "id": "es", "label": "Español", "code": "es", "variables": { "language_style": "Spanish, informal" } }
        ] },
        "microphone": { "title": "Mic", "body": "For the call.", "allow": "Allow", "skip": "Skip" },
        "notifications": { "title": "Notifications", "body": "For reminders.", "allow": "Allow", "skip": "Skip" }
      },
      "firstCall": {
        "title": "Your kickoff call", "body": "Ten minutes with Ari.", "startNow": "Start now", "chooseTime": "Choose a time",
        "callReason": "kickoff_call", "bookingReason": "Your kickoff call", "incomingTitle": "Your kickoff call",
        "preferredTimes": ["10:30", "13:00", "17:30", "20:00"], "dayCount": 3,
        "picker": { "title": "Choose a time", "body": "Ari calls you then.", "confirm": "Confirm", "none": "No times." },
        "booked": { "title": "Booked", "body": "Ari calls you {time}.", "change": "Change time", "startNow": "Start now instead" },
        "couldNotConnect": { "title": "We couldn't connect", "body": "Missed.", "declined": "Declined.", "endedEarly": "Ended early.",
          "busy": "Busy.", "notReached": "Not reached.", "callNow": "Call me now", "chooseTime": "Choose another time", "chat": "Continue in chat" },
        "chat": { "title": "Kickoff", "body": "Same questions.", "start": "Let's do it here." }
      },
      "plan": {
        "building": { "title": "Building your week", "body": "A minute.", "steps": ["Reading"], "slow": "Slow." },
        "ready": { "title": "Your week is ready", "card": "Your first week", "line": "{days} days · {actions} actions", "open": "My week" },
        "weekTitle": "My week", "targetsTitle": "Daily targets",
        "targets": [ { "key": "minutes", "label": "Study", "unit": "min" } ],
        "metrics": [ { "key": "minutes", "label": "Length", "unit": "min" } ],
        "kinds": [ { "id": "lesson", "label": "Lesson" }, { "id": "practice", "label": "Practice" }, { "id": "review", "label": "Review" } ],
        "item": { "why": "Why", "details": "What to do", "swaps": "Instead", "useSwap": "Use selected swap", "ask": "Ask Ari" },
        "share": { "title": "Share", "intro": "My study week" },
        "sample": { "title": "A first week", "daily_targets": { "minutes": 45 }, "days": [
          { "day": "mon", "items": [ { "time": "08:00", "kind": "lesson", "title": "Lesson 1", "minutes": 20, "swaps": ["Lesson 1 video"] },
                                      { "time": "19:00", "kind": "practice", "title": "Practice set A", "minutes": 25 } ] },
          { "day": "tue", "items": [ { "time": "08:00", "kind": "lesson", "title": "Lesson 2", "minutes": 20 } ] }
        ] }
      },
      "checkIns": { "kinds": ["lesson"], "afterMinutes": 30, "title": "{kind} check-in", "body": "How was {title}?",
        "detail": "Opened the {kind} reminder: {title} at {time} on {day}.", "quietStart": "22:00", "quietEnd": "07:00" },
      "today": { "tracker": { "label": "Practice", "unit": "min", "target": "minutes", "step": 10 }, "upNext": "Up next", "allDone": "All done." },
      "progress": { "completion": "Followed", "measure": { "key": "score", "label": "Score", "unit": "pts", "profileField": "hours", "min": 0, "max": 100 },
        "weekly": { "question": "How was the week?", "labels": ["1", "2", "3", "4", "5"] } },
      "coach": { "callReason": "coach_call", "starters": ["What's next?"] },
      "support": { "starters": ["Help"] }
    }
    """.trimIndent()

    /** The tour's test pack with this journey. */
    val pack: UseCasePack get() = UseCasePack.parse(TestPack.json.trimEnd().removeSuffix("}") + ",\n" + block + "\n}")

    /** A profile that passes the journey's rules (born 1990-05-20). */
    val profile = mapOf("name" to "Sam", "level" to "beginner", "born" to "1990-05-20", "hours" to "6")
}

class MemoryTextStore : TextStore {
    val files = LinkedHashMap<String, String>()
    var writes = 0
    override fun read(name: String): String? = files[name]
    override fun write(name: String, text: String) { writes++; files[name] = text }
    override fun delete(name: String) { files.remove(name) }
}

class FakeReminders : ReminderScheduler {
    var scheduled: List<Reminder> = emptyList()
    var replaced = 0
    var allow = true
    var asked = 0
    override fun replaceAll(reminders: List<Reminder>) { replaced++; scheduled = reminders }
    override suspend fun requestPermission(): Boolean { asked++; return allow }
}

class FakeShare : JourneyShare {
    val texts = mutableListOf<Pair<String, String>>()
    val files = mutableListOf<Pair<String, String>>()
    override fun shareText(title: String, text: String) { texts += title to text }
    override fun shareFile(fileName: String, text: String) { files += fileName to text }
}

/** The SDK, as the journey sees it: records every call, answers what the test says. */
class FakeJourneySdk : JourneySdk {
    val configured = JourneyAgent.entries.toMutableSet()
    override fun isConfigured(agent: JourneyAgent): Boolean = agent in configured

    /** What each signIn identified (null: a signOut), in order. */
    val identified = mutableListOf<Map<String, String>?>()
    /** Every signIn's user id, in order. */
    val signedInIds = mutableListOf<String>()
    override fun signIn(userId: String, traits: Map<String, String>) {
        signedInIds += userId
        identified += traits
    }
    override fun signOut() { identified += null }

    val variables = mutableListOf<Map<String, String?>>()
    val held = LinkedHashMap<String, String>()
    override fun setVariables(values: Map<String, String?>) {
        variables += values
        values.forEach { (k, v) -> if (v == null) held.remove(k) else held[k] = v }
    }

    var permissions = CallPermissionAnswer(microphone = true, notifications = true)
    var permissionAsks = 0
    override suspend fun requestCallPermissions(): CallPermissionAnswer { permissionAsks++; return permissions }

    override val callState = MutableStateFlow(GraineCallInfo())
    val outcomes = MutableSharedFlow<GraineCallOutcome>(extraBufferCapacity = 16)
    override val callOutcomes: Flow<GraineCallOutcome> get() = outcomes

    val started = mutableListOf<Pair<String, JourneyAgent>>()
    var startResult: String? = "call_1"
    override fun startCall(reason: String, agent: JourneyAgent): String? { started += reason to agent; return startResult }
    val controls = mutableListOf<String>()
    override fun answer() { controls += "answer" }
    override fun decline() { controls += "decline" }
    override fun end() { controls += "end" }
    override fun setMuted(muted: Boolean) { controls += "muted:$muted" }
    override fun setSpeaker(on: Boolean) { controls += "speaker:$on" }

    var rules = GraineSchedulingRules(usersCanBook = true, minLeadMinutes = 5, maxDaysAhead = 14, maxPendingPerUser = 3)
    override fun schedulingRules(agent: JourneyAgent): GraineSchedulingRules = rules
    val booking = MutableStateFlow(GraineBookingState(available = true, reason = null))
    override fun bookingState(agent: JourneyAgent): StateFlow<GraineBookingState> = booking
    var slots: (LocalDate) -> List<Instant> = { emptyList() }
    override fun bookingSlots(day: LocalDate, agent: JourneyAgent): List<Instant> = slots(day)
    val scheduled = mutableListOf<Triple<Instant, String, JourneyAgent>>()
    var scheduleResult: Result<GraineScheduledCall>? = null
    /** Every call booked, as Graine would list it. */
    val bookedCalls = mutableListOf<GraineScheduledCall>()
    override suspend fun scheduleCall(at: Instant, reason: String, agent: JourneyAgent): GraineScheduledCall {
        scheduled += Triple(at, reason, agent)
        val call = scheduleResult?.getOrThrow() ?: GraineScheduledCall("booked_${scheduled.size}", at, GraineCallStatus.Scheduled, GraineCallSource.App, reason)
        bookedCalls += call
        return call
    }
    /** What scheduledCalls answers; null lists the calls booked here, those cancelled as Cancelled. */
    var listResult: Result<List<GraineScheduledCall>>? = null
    override suspend fun scheduledCalls(agent: JourneyAgent): List<GraineScheduledCall> =
        listResult?.getOrThrow() ?: bookedCalls.map { if (it.id in cancelled) it.copy(status = GraineCallStatus.Cancelled) else it }
    val cancelled = mutableListOf<String>()
    override suspend fun cancelScheduledCall(id: String, agent: JourneyAgent): GraineCallStatus { cancelled += id; return GraineCallStatus.Cancelled }

    var callBackResult: String? = "call_back_1"
    var callBacks = 0
    override fun callBack(): String? { callBacks++; return callBackResult }
    var dismissed = 0
    override fun dismissMissedCall() { dismissed++ }

    val chats = JourneyAgent.entries.associateWith { MutableStateFlow(GraineChatStatus.IDLE) }
    override fun chatStatus(agent: JourneyAgent): StateFlow<GraineChatStatus> = chats.getValue(agent)
    val shown = mutableListOf<Pair<JourneyAgent, Boolean>>()
    override fun chatShown(agent: JourneyAgent, visible: Boolean) { shown += agent to visible }
    var retries = 0
    override fun chatRetry(agent: JourneyAgent) { retries++ }

    val reported = mutableListOf<Pair<String, String?>>()
    override fun reportEvent(name: String, detail: String?) { reported += name to detail }
    val tracked = mutableListOf<Pair<String, Map<String, Any?>>>()
    override fun track(name: String, data: Map<String, Any?>) { tracked += name to data }
    val screens = mutableListOf<GraineScreen?>()
    override fun setScreen(screen: GraineScreen?) { screens += screen }

    val actions = mutableMapOf<JourneyAgent, MutableMap<String, (Map<String, Any?>) -> JourneyAnswer>>()
    var unregisters = 0
    override fun registerAction(agent: JourneyAgent, name: String, handler: (Map<String, Any?>) -> JourneyAnswer) {
        actions.getOrPut(agent) { mutableMapOf() }[name] = handler
    }
    override fun unregisterActions() { unregisters++; actions.clear() }

    var memoriesResult: Result<List<GraineMemory>> = Result.success(listOf(GraineMemory("m1", "Prefers mornings")))
    override suspend fun memories(): List<GraineMemory> = memoriesResult.getOrThrow()
    val forgets = mutableListOf<JourneyAgent>()
    val forgetResults = mutableMapOf<JourneyAgent, Result<Int>>()
    override suspend fun forgetMemory(agent: JourneyAgent): Int { forgets += agent; return (forgetResults[agent] ?: Result.success(1)).getOrThrow() }
    val sessions = mutableMapOf<JourneyAgent, List<GraineSessionSummary>>()
    override suspend fun pastSessions(agent: JourneyAgent, max: Int): List<GraineSessionSummary> = sessions[agent].orEmpty().take(max)
    override suspend fun sessionTranscript(agent: JourneyAgent, id: String): GraineSessionTranscript =
        GraineSessionTranscript(id) .takeIf { sessions[agent].orEmpty().any { it.id == id } } ?: throw GraineException(GraineErrorCode.NotFound, "No such session.")

    /** Run an agent's action as the agent would. */
    fun run(agent: JourneyAgent, name: String, args: Map<String, Any?> = emptyMap()): JourneyAnswer =
        actions[agent]?.get(name)?.invoke(args) ?: error("no action $name on $agent")

    val lastScreen: GraineScreen? get() = screens.lastOrNull()
}

/** One journey under test: its fakes, and the model over them, on the test's clock. */
class JourneyHarness(
    val test: TestScope,
    val pack: UseCasePack = TestJourney.pack,
    val sdk: FakeJourneySdk = FakeJourneySdk(),
    val files: MemoryTextStore = MemoryTextStore(),
    val reminders: FakeReminders = FakeReminders(),
    val share: FakeShare = FakeShare(),
    /** 14 Oct 2026 (a Wednesday) at 12:00, Asia/Kolkata, plus the test's virtual time. */
    val start: Long = TestClock.at(12),
    val debug: Boolean = false,
) {
    val opens = mutableListOf<Boolean>()
    private var ids = 0
    private val dispatcher = UnconfinedTestDispatcher(test.testScheduler)

    fun now(): Long = start + test.testScheduler.currentTime

    fun model(): JourneyModel = JourneyModel(
        pack = pack,
        sdk = sdk,
        store = files,
        reminders = reminders,
        share = share,
        scope = CoroutineScope(dispatcher + SupervisorJob()),
        io = dispatcher,
        now = { now() },
        zone = TestClock.zone,
        debugBuild = debug,
        newUserId = { "user-test-${++ids}" },
        onOpenChanged = { opens += it },
    )
}
