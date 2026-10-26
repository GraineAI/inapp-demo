package ai.graine.demo.journey

import ai.graine.demo.domain.Json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Where the journey keeps its files: one text file per name, read whole, written whole and atomically (a write
 * that stops half way leaves the last good file). Android: the app's files folder; iOS: Application Support.
 */
interface TextStore {
    fun read(name: String): String?
    fun write(name: String, text: String)
    fun delete(name: String)
}

/** Who is on the journey on this phone: the id they are identified by, and whether they are signed in. */
data class JourneyAccount(val userId: String, val createdAt: Long, val signedIn: Boolean)

/** The profile's answers by field key ("birth_date" → "1992-04-17"), and the language chosen (its option id). */
data class JourneyProfile(val values: Map<String, String> = emptyMap(), val languageId: String? = null)

/** Where the user is on the journey. */
enum class JourneyStage(val wire: String) {
    ONBOARDING("onboarding"),
    FIRST_CALL("first_call"),
    BOOKED("booked"),
    IN_CALL("in_call"),
    COULD_NOT_CONNECT("could_not_connect"),
    FIRST_CALL_CHAT("first_call_chat"),
    BUILDING_PLAN("building_plan"),
    READY("ready");

    companion object {
        fun of(wire: String?): JourneyStage = entries.firstOrNull { it.wire == wire } ?: ONBOARDING
    }
}

enum class OnboardingStep(val wire: String) {
    WELCOME("welcome"), PROFILE("profile"), LANGUAGE("language"), MICROPHONE("microphone"), NOTIFICATIONS("notifications");

    companion object {
        fun of(wire: String?): OnboardingStep = entries.firstOrNull { it.wire == wire } ?: WELCOME
    }
}

/** Why the first call did not happen. */
enum class NoConnect(val wire: String) {
    /** Nobody answered the ring. */
    MISSED("missed"),
    /** The user declined it. */
    DECLINED("declined"),
    /** It never connected. */
    FAILED("failed"),
    /** Another call was live on the phone. */
    BUSY("busy"),
    /** It ended before the first-call agent saved what it learned. */
    ENDED_EARLY("ended_early"),
    /** A booked call that could not ring this phone: no device, or it expired. */
    NOT_REACHED("not_reached");

    companion object {
        fun of(wire: String?): NoConnect? = entries.firstOrNull { it.wire == wire }
    }
}

/** A first call booked for later: its id with the first-call agent, and when it rings (epoch ms). */
data class BookedCall(val id: String, val at: Long)

data class JourneyProgress(
    val stage: JourneyStage = JourneyStage.ONBOARDING,
    val onboardingStep: OnboardingStep = OnboardingStep.WELCOME,
    val noConnect: NoConnect? = null,
    val booked: BookedCall? = null,
    /** The first call's id, while one is on its way or live. */
    val firstCallId: String? = null,
)

/** What the first-call agent saved, as it sent it, and when. */
data class Intake(val values: Map<String, Any?>, val savedAt: Long)

/** How an item went, on a date: from the coach's check-in action, or the app's own Done. */
data class CheckIn(val date: LocalDate, val time: String?, val followed: Boolean, val note: String?, val source: String, val at: Long)

data class MeasureEntry(val date: LocalDate, val value: Double)

data class JourneyActivity(
    val checkIns: List<CheckIn> = emptyList(),
    /** Today's counter by date (the pack's tracker). */
    val tracker: Map<LocalDate, Double> = emptyMap(),
    val measures: List<MeasureEntry> = emptyList(),
    /** The weekly check-in's score by the week's Monday. */
    val weekly: Map<LocalDate, Int> = emptyMap(),
) {
    /** How the item at [time] on [date] went, by its latest check-in; null when nobody said. */
    fun followed(date: LocalDate, time: String): Boolean? = checkIns.lastOrNull { it.date == date && it.time == time }?.followed

    companion object {
        const val CHECK_INS_MAX = 300
        const val DAYS_KEPT = 90
        const val MEASURES_MAX = 120
        const val WEEKS_KEPT = 52
    }
}

data class JourneySettings(
    val reminders: Boolean = true,
    val quietStart: String,
    val quietEnd: String,
    /** "Load sample plan" and "Simulate reminder", outside debug builds too. */
    val testingTools: Boolean = false,
)

/**
 * THE JOURNEY'S REPOSITORY — the app is the store (no server of its own): the account, the profile, where the user
 * is, what the first call learned, the plan, check-ins and settings, each kept in memory and in its own JSON file
 * per pack. Reads happen once, at start; every change is written in the background by one writer, in the order the
 * changes were made, each write the file's latest state — so an erase is never undone by a write queued before it.
 */
class JourneyRepository(
    private val store: TextStore,
    private val packId: String,
    private val defaults: JourneySettings,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    /** The writer's queue: jobs run one at a time, in order, off the main thread. */
    private val writes = Channel<() -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch(io) { for (job in writes) runCatching { job() } }
    }

    var account: JourneyAccount? = readAccount()
        private set
    var profile: JourneyProfile = readProfile()
        private set
    var progress: JourneyProgress = readProgress()
        private set
    var intake: Intake? = readIntake()
        private set
    var plan: Plan? = read(PLAN)?.let { Plan.fromStored(it) }
        private set
    var activity: JourneyActivity = readActivity()
        private set
    var settings: JourneySettings = readSettings()
        private set

    fun saveAccount(next: JourneyAccount?) { account = next; persist(ACCOUNT) }
    fun saveProfile(next: JourneyProfile) { profile = next; persist(PROFILE) }
    fun saveProgress(next: JourneyProgress) { if (next != progress) { progress = next; persist(PROGRESS) } }
    fun saveIntake(next: Intake?) { intake = next; persist(INTAKE) }
    fun savePlan(next: Plan?) { plan = next; persist(PLAN) }
    fun saveSettings(next: JourneySettings) { settings = next; persist(SETTINGS) }

    /** Activity, kept bounded: the newest check-ins, the last 90 days of the counter, the last year of weekly scores. */
    fun saveActivity(next: JourneyActivity, today: LocalDate) {
        val oldest = LocalDate.fromEpochDays(today.toEpochDays() - JourneyActivity.DAYS_KEPT)
        val oldestWeek = LocalDate.fromEpochDays(today.toEpochDays() - 7 * JourneyActivity.WEEKS_KEPT)
        activity = next.copy(
            checkIns = next.checkIns.filter { it.date >= oldest }.takeLast(JourneyActivity.CHECK_INS_MAX),
            tracker = next.tracker.filterKeys { it >= oldest },
            measures = next.measures.sortedBy { it.date }.takeLast(JourneyActivity.MEASURES_MAX),
            weekly = next.weekly.filterKeys { it >= oldestWeek },
        )
        persist(ACTIVITY)
    }

    /** Everything of this pack's journey on this phone, gone: the files and what is in memory. */
    fun eraseAll() {
        account = null
        profile = JourneyProfile()
        progress = JourneyProgress()
        intake = null
        plan = null
        activity = JourneyActivity()
        settings = defaults
        writes.trySend { ALL.forEach { name -> runCatching { store.delete(file(name)) } } }
    }

    // ── the files ───────────────────────────────────────────────────────────

    private fun file(name: String) = "$packId.$name.json"

    /** Queue a write of [name]: the job writes the state it finds when it runs, which is the newest. */
    private fun persist(name: String) {
        writes.trySend {
            val value = snapshot(name)
            if (value == null) store.delete(file(name)) else store.write(file(name), Json.write(value))
        }
    }

    private fun snapshot(name: String): Any? = when (name) {
        ACCOUNT -> account?.let { linkedMapOf("user_id" to it.userId, "created_at" to it.createdAt, "signed_in" to it.signedIn) }
        PROFILE -> linkedMapOf("values" to profile.values, "language" to profile.languageId)
        PROGRESS -> linkedMapOf(
            "stage" to progress.stage.wire,
            "onboarding_step" to progress.onboardingStep.wire,
            "no_connect" to progress.noConnect?.wire,
            "booked" to progress.booked?.let { linkedMapOf("id" to it.id, "at" to it.at) },
            "first_call_id" to progress.firstCallId,
        )
        INTAKE -> intake?.let { linkedMapOf("values" to it.values, "saved_at" to it.savedAt) }
        PLAN -> plan?.toWire(withMeta = true)
        ACTIVITY -> linkedMapOf(
            "check_ins" to activity.checkIns.map {
                linkedMapOf("date" to it.date.toString(), "time" to it.time, "followed" to it.followed, "note" to it.note, "source" to it.source, "at" to it.at)
            },
            "tracker" to activity.tracker.entries.associate { (d, v) -> d.toString() to v },
            "measures" to activity.measures.map { linkedMapOf("date" to it.date.toString(), "value" to it.value) },
            "weekly" to activity.weekly.entries.associate { (d, v) -> d.toString() to v },
        )
        SETTINGS -> linkedMapOf(
            "reminders" to settings.reminders,
            "quiet_start" to settings.quietStart,
            "quiet_end" to settings.quietEnd,
            "testing_tools" to settings.testingTools,
        )
        else -> null
    }

    /** A file read and parsed; a missing or damaged file reads as nothing, never as a crash. */
    private fun read(name: String): Map<String, Any?>? = runCatching {
        store.read(file(name))?.let { Args.map(Json.parse(it)) }
    }.getOrNull()

    private fun readAccount(): JourneyAccount? = read(ACCOUNT)?.let { m ->
        val id = Args.string(m["user_id"]) ?: return@let null
        JourneyAccount(id, Args.number(m["created_at"])?.toLong() ?: 0L, Args.bool(m["signed_in"]) ?: false)
    }

    private fun readProfile(): JourneyProfile = read(PROFILE)?.let { m ->
        JourneyProfile(
            values = Args.map(m["values"]).orEmpty().mapNotNull { (k, v) -> Args.string(v)?.let { k to it } }.toMap(),
            languageId = Args.string(m["language"]),
        )
    } ?: JourneyProfile()

    private fun readProgress(): JourneyProgress = read(PROGRESS)?.let { m ->
        JourneyProgress(
            stage = JourneyStage.of(m["stage"] as? String),
            onboardingStep = OnboardingStep.of(m["onboarding_step"] as? String),
            noConnect = NoConnect.of(m["no_connect"] as? String),
            booked = Args.map(m["booked"])?.let { b ->
                val id = Args.string(b["id"]) ?: return@let null
                BookedCall(id, Args.number(b["at"])?.toLong() ?: return@let null)
            },
            firstCallId = Args.string(m["first_call_id"]),
        )
    } ?: JourneyProgress()

    private fun readIntake(): Intake? = read(INTAKE)?.let { m ->
        val values = Args.map(m["values"]) ?: return@let null
        Intake(values, Args.number(m["saved_at"])?.toLong() ?: 0L)
    }

    private fun readActivity(): JourneyActivity = read(ACTIVITY)?.let { m ->
        JourneyActivity(
            checkIns = Args.list(m["check_ins"]).orEmpty().mapNotNull { raw ->
                val c = Args.map(raw) ?: return@mapNotNull null
                CheckIn(
                    date = date(c["date"]) ?: return@mapNotNull null,
                    time = Args.time(c["time"]),
                    followed = Args.bool(c["followed"]) ?: return@mapNotNull null,
                    note = Args.string(c["note"]),
                    source = Args.string(c["source"]) ?: "app",
                    at = Args.number(c["at"])?.toLong() ?: 0L,
                )
            },
            tracker = Args.map(m["tracker"]).orEmpty().mapNotNull { (k, v) -> date(k)?.let { d -> Args.number(v)?.let { d to it } } }.toMap(),
            measures = Args.list(m["measures"]).orEmpty().mapNotNull { raw ->
                val e = Args.map(raw) ?: return@mapNotNull null
                MeasureEntry(date(e["date"]) ?: return@mapNotNull null, Args.number(e["value"]) ?: return@mapNotNull null)
            },
            weekly = Args.map(m["weekly"]).orEmpty().mapNotNull { (k, v) -> date(k)?.let { d -> Args.int(v)?.takeIf { it in 1..5 }?.let { d to it } } }.toMap(),
        )
    } ?: JourneyActivity()

    private fun readSettings(): JourneySettings = read(SETTINGS)?.let { m ->
        JourneySettings(
            reminders = Args.bool(m["reminders"]) ?: defaults.reminders,
            quietStart = Args.time(m["quiet_start"]) ?: defaults.quietStart,
            quietEnd = Args.time(m["quiet_end"]) ?: defaults.quietEnd,
            testingTools = Args.bool(m["testing_tools"]) ?: false,
        )
    } ?: defaults

    private fun date(v: Any?): LocalDate? = Args.string(v)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private companion object {
        const val ACCOUNT = "account"
        const val PROFILE = "profile"
        const val PROGRESS = "progress"
        const val INTAKE = "intake"
        const val PLAN = "plan"
        const val ACTIVITY = "activity"
        const val SETTINGS = "settings"
        val ALL = listOf(ACCOUNT, PROFILE, PROGRESS, INTAKE, PLAN, ACTIVITY, SETTINGS)
    }
}
