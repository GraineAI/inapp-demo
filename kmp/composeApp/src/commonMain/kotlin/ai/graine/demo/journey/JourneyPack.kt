package ai.graine.demo.journey

import ai.graine.demo.domain.JsonObject
import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.domain.int
import ai.graine.demo.domain.obj
import ai.graine.demo.domain.objects
import ai.graine.demo.domain.optStr
import ai.graine.demo.domain.req
import ai.graine.demo.domain.str
import ai.graine.demo.domain.strings

/**
 * A USE-CASE PACK'S JOURNEY — the `journey` block of `demo/usecases/<pack>.json`: every word, profile field,
 * language, unit, item kind and event name of an app's whole in-app journey, and a sample plan. The journey's code
 * (this package and `ui/journey`) is generic: onboarding, a first call, a plan, check-ins. A pack without the block
 * has no journey.
 *
 * Three agents, as the SDK knows them: the coach is the app's main agent (the SDK's default agent), the first-call
 * agent is configured under the name [JourneyAgent.FIRST_CALL]'s, and support is the help agent.
 */
data class JourneyPack(
    /** The trait the user is identified by: the three agents' Who is the user? (`user_id` is the dashboard's User ID). */
    val identifierField: String,
    val title: String,
    val summary: String,
    val notice: String?,
    val agents: Agents,
    val actions: Actions,
    /** The fields the intake action must carry; a call without them is refused, so the agent sends them again. */
    val intakeRequired: List<String>,
    val events: Events,
    val onboarding: Onboarding,
    val firstCall: FirstCall,
    val plan: PlanCopy,
    val checkIns: CheckIns,
    val today: Today,
    val progress: Progress,
    val coach: Coach,
    val support: Support,
) {
    data class AgentCopy(val name: String, val role: String)
    data class Agents(val coach: AgentCopy, val firstCall: AgentCopy, val support: AgentCopy)

    /** The app actions, by the names the agents declare them under (the action files in agent-setup/<pack>/journey). */
    data class Actions(
        val saveIntake: String,
        val getIntake: String,
        val savePlan: String,
        val getPlan: String,
        val swapItem: String,
        val logCheckIn: String,
    )

    data class Events(
        /** reportEvent to the coach once its chat is online, after the first call: the coach builds the plan. */
        val firstCallFinished: String,
        val firstCallFinishedDetail: String,
        /** reportEvent once a check-in reminder is opened: the coach asks how it went. */
        val checkIn: String,
        /** track: an item marked done in the app's own screens. */
        val itemDone: String,
        /** track: a swap the user chose in the app's own screens. */
        val itemSwapped: String,
        /** track: the weekly 1–5 check-in, `score`. */
        val weeklyCheckIn: String,
    )

    data class Copy(val title: String, val body: String)
    data class Permission(val title: String, val body: String, val allow: String, val skip: String)

    enum class FieldType(val wire: String) {
        TEXT("text"), CHOICE("choice"), DATE("date"), NUMBER("number");

        companion object {
            fun of(wire: String): FieldType? = entries.firstOrNull { it.wire == wire }
        }
    }

    data class Option(val value: String, val label: String)

    /**
     * One profile question. [variable] is the prompt variable the answer goes to; a DATE gives its age in years, and
     * [min] / [max] are ages there. The field keyed [NAME_KEY] is the user's name, sent with identify.
     */
    data class ProfileField(
        val key: String,
        val label: String,
        val type: FieldType,
        val variable: String?,
        val required: Boolean,
        val placeholder: String?,
        val unit: String?,
        val min: Double?,
        val max: Double?,
        val maxLength: Int?,
        val options: List<Option>,
    )

    data class LanguageOption(val id: String, val label: String, val code: String, val variables: Map<String, String>)
    data class Language(val title: String, val body: String, val variable: String, val options: List<LanguageOption>)

    data class Onboarding(
        val welcome: Copy,
        val profileTitle: String,
        val profileBody: String,
        val fields: List<ProfileField>,
        val language: Language,
        val microphone: Permission,
        val notifications: Permission,
    )

    data class Picker(val title: String, val body: String, val confirm: String, val none: String)
    data class Booked(val title: String, val body: String, val change: String, val startNow: String)
    data class CouldNotConnect(
        val title: String,
        val body: String,
        val declined: String,
        val endedEarly: String,
        val busy: String,
        val notReached: String,
        val callNow: String,
        val chooseTime: String,
        val chat: String,
    )

    data class FirstCall(
        val title: String,
        val body: String,
        val startNow: String,
        val chooseTime: String,
        /** startCall's reason. */
        val callReason: String,
        /** scheduleCall's reason. */
        val bookingReason: String,
        /** What the call screen says while the first call rings. */
        val incomingTitle: String,
        /** The few times of day the picker offers ("10:30"), out of every time the agent allows. */
        val preferredTimes: List<String>,
        val dayCount: Int,
        val picker: Picker,
        val booked: Booked,
        val couldNotConnect: CouldNotConnect,
        val chat: Copy,
        /** The user's first message in the chat version. */
        val chatStart: String,
    )

    data class Measure(val key: String, val label: String, val unit: String)
    data class Kind(val id: String, val label: String)
    data class Building(val title: String, val body: String, val steps: List<String>, val slow: String)
    data class Ready(val title: String, val card: String, val line: String, val open: String)
    data class ItemCopy(val why: String, val details: String, val swaps: String, val useSwap: String, val ask: String)

    data class PlanCopy(
        val building: Building,
        val ready: Ready,
        val weekTitle: String,
        val targetsTitle: String,
        val targets: List<Measure>,
        val metrics: List<Measure>,
        val kinds: List<Kind>,
        val item: ItemCopy,
        val shareTitle: String,
        val shareIntro: String,
        /** The sample plan, in the shape the plan action saves it. */
        val sample: JsonObject,
    ) {
        fun kindLabel(id: String): String = kinds.firstOrNull { it.id == id }?.label ?: id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    data class CheckIns(
        val kinds: List<String>,
        val afterMinutes: Int,
        val title: String,
        val body: String,
        val detail: String,
        val quietStart: String,
        val quietEnd: String,
    )

    data class Tracker(val label: String, val unit: String, val target: String, val step: Double)
    data class Today(val tracker: Tracker?, val upNext: String, val allDone: String)

    data class ProgressMeasure(val key: String, val label: String, val unit: String, val profileField: String?, val min: Double, val max: Double)
    data class Progress(val completion: String, val measure: ProgressMeasure?, val weeklyQuestion: String, val weeklyLabels: List<String>)

    data class Coach(val callReason: String, val starters: List<String>)
    data class Support(val starters: List<String>)

    /** Every variable the journey sets: the profile's, the language's and each language choice's own. */
    val variableNames: List<String>
        get() = (onboarding.fields.mapNotNull { it.variable } + onboarding.language.variable +
            onboarding.language.options.flatMap { it.variables.keys }).distinct()

    /** The profile field that is the user's name. */
    val nameField: ProfileField? get() = onboarding.fields.firstOrNull { it.key == NAME_KEY }

    companion object {
        /** The profile field that carries the user's name, sent with identify. */
        const val NAME_KEY = "name"

        fun parse(o: JsonObject): JourneyPack {
            val agents = o.req("agents")
            val actions = o.req("actions")
            val events = o.req("events")
            val onboarding = o.req("onboarding")
            val profile = onboarding.req("profile")
            val language = onboarding.req("language")
            val first = o.req("firstCall")
            val cnc = first.req("couldNotConnect")
            val plan = o.req("plan")
            val checkIns = o.req("checkIns")
            val today = o.req("today")
            val progress = o.req("progress")
            val weekly = progress.req("weekly")
            return JourneyPack(
                identifierField = o.str("identifierField"),
                title = o.str("title"),
                summary = o.str("summary"),
                notice = o.optStr("notice"),
                agents = Agents(agent(agents.req("coach")), agent(agents.req("firstCall")), agent(agents.req("support"))),
                actions = Actions(
                    saveIntake = actions.str("saveIntake"),
                    getIntake = actions.str("getIntake"),
                    savePlan = actions.str("savePlan"),
                    getPlan = actions.str("getPlan"),
                    swapItem = actions.str("swapItem"),
                    logCheckIn = actions.str("logCheckIn"),
                ),
                intakeRequired = o.strings("intakeRequired"),
                events = Events(
                    firstCallFinished = events.str("firstCallFinished"),
                    firstCallFinishedDetail = events.str("firstCallFinishedDetail"),
                    checkIn = events.str("checkIn"),
                    itemDone = events.str("itemDone"),
                    itemSwapped = events.str("itemSwapped"),
                    weeklyCheckIn = events.str("weeklyCheckIn"),
                ),
                onboarding = Onboarding(
                    welcome = copy(onboarding.req("welcome")),
                    profileTitle = profile.str("title"),
                    profileBody = profile.str("body"),
                    fields = profile.objects("fields").map { field(it) }.also { fields ->
                        if (fields.none { it.key == NAME_KEY }) throw UseCasePack.PackException("journey: no profile field keyed \"$NAME_KEY\"")
                    },
                    language = Language(
                        title = language.str("title"),
                        body = language.str("body"),
                        variable = language.str("variable"),
                        options = language.objects("options").map { l ->
                            LanguageOption(l.str("id"), l.str("label"), l.str("code"), stringMap(l.obj("variables")))
                        }.also { if (it.isEmpty()) throw UseCasePack.PackException("journey: no languages") },
                    ),
                    microphone = permission(onboarding.req("microphone")),
                    notifications = permission(onboarding.req("notifications")),
                ),
                firstCall = FirstCall(
                    title = first.str("title"),
                    body = first.str("body"),
                    startNow = first.str("startNow"),
                    chooseTime = first.str("chooseTime"),
                    callReason = first.str("callReason"),
                    bookingReason = first.str("bookingReason"),
                    incomingTitle = first.str("incomingTitle"),
                    preferredTimes = first.strings("preferredTimes").mapNotNull { JourneyTime.normalise(it) }.distinct().sorted(),
                    dayCount = (first.int("dayCount") ?: 5).coerceIn(1, 14),
                    picker = first.req("picker").let { Picker(it.str("title"), it.str("body"), it.str("confirm"), it.str("none")) },
                    booked = first.req("booked").let { Booked(it.str("title"), it.str("body"), it.str("change"), it.str("startNow")) },
                    couldNotConnect = CouldNotConnect(
                        title = cnc.str("title"),
                        body = cnc.str("body"),
                        declined = cnc.str("declined"),
                        endedEarly = cnc.str("endedEarly"),
                        busy = cnc.str("busy"),
                        notReached = cnc.str("notReached"),
                        callNow = cnc.str("callNow"),
                        chooseTime = cnc.str("chooseTime"),
                        chat = cnc.str("chat"),
                    ),
                    chat = first.req("chat").let { Copy(it.str("title"), it.str("body")) },
                    chatStart = first.req("chat").str("start"),
                ),
                plan = PlanCopy(
                    building = plan.req("building").let { Building(it.str("title"), it.str("body"), it.strings("steps"), it.str("slow")) },
                    ready = plan.req("ready").let { Ready(it.str("title"), it.str("card"), it.str("line"), it.str("open")) },
                    weekTitle = plan.str("weekTitle"),
                    targetsTitle = plan.str("targetsTitle"),
                    targets = plan.objects("targets").map { measure(it) },
                    metrics = plan.objects("metrics").map { measure(it) },
                    kinds = plan.objects("kinds").map { Kind(it.str("id"), it.str("label")) },
                    item = plan.req("item").let { ItemCopy(it.str("why"), it.str("details"), it.str("swaps"), it.str("useSwap"), it.str("ask")) },
                    shareTitle = plan.req("share").str("title"),
                    shareIntro = plan.req("share").str("intro"),
                    sample = plan.req("sample"),
                ),
                checkIns = CheckIns(
                    kinds = checkIns.strings("kinds"),
                    afterMinutes = (checkIns.int("afterMinutes") ?: 0).coerceIn(0, 240),
                    title = checkIns.str("title"),
                    body = checkIns.str("body"),
                    detail = checkIns.str("detail"),
                    quietStart = JourneyTime.normalise(checkIns.str("quietStart")) ?: "22:00",
                    quietEnd = JourneyTime.normalise(checkIns.str("quietEnd")) ?: "07:00",
                ),
                today = Today(
                    tracker = today.obj("tracker")?.let { t ->
                        Tracker(t.str("label"), t.str("unit"), t.str("target"), Args.number(t["step"])?.takeIf { it > 0 } ?: 1.0)
                    },
                    upNext = today.str("upNext"),
                    allDone = today.str("allDone"),
                ),
                progress = Progress(
                    completion = progress.str("completion"),
                    measure = progress.obj("measure")?.let { m ->
                        ProgressMeasure(
                            key = m.str("key"),
                            label = m.str("label"),
                            unit = m.str("unit"),
                            profileField = m.optStr("profileField"),
                            min = Args.number(m["min"]) ?: 0.0,
                            max = Args.number(m["max"]) ?: 1_000.0,
                        )
                    },
                    weeklyQuestion = weekly.str("question"),
                    weeklyLabels = weekly.strings("labels").takeIf { it.size == 5 } ?: listOf("1", "2", "3", "4", "5"),
                ),
                coach = o.req("coach").let { Coach(it.str("callReason"), it.strings("starters")) },
                support = Support(o.req("support").strings("starters")),
            )
        }

        private fun agent(o: JsonObject) = AgentCopy(o.str("name"), o.str("role"))
        private fun copy(o: JsonObject) = Copy(o.str("title"), o.str("body"))
        private fun permission(o: JsonObject) = Permission(o.str("title"), o.str("body"), o.str("allow"), o.str("skip"))
        private fun measure(o: JsonObject) = Measure(o.str("key"), o.str("label"), o["unit"] as? String ?: "")

        private fun field(o: JsonObject): ProfileField {
            val type = FieldType.of(o.str("type")) ?: throw UseCasePack.PackException("journey: field ${o["key"]} has an unknown type")
            val options = o.objects("options").map { Option(it.str("value"), it.str("label")) }
            if (type == FieldType.CHOICE && options.isEmpty()) throw UseCasePack.PackException("journey: choice field ${o["key"]} has no options")
            return ProfileField(
                key = o.str("key"),
                label = o.str("label"),
                type = type,
                variable = o.optStr("variable"),
                required = o["required"] == true,
                placeholder = o.optStr("placeholder"),
                unit = o.optStr("unit"),
                min = Args.number(o["min"]),
                max = Args.number(o["max"]),
                maxLength = o.int("maxLength"),
                options = options,
            )
        }

        private fun stringMap(o: JsonObject?): Map<String, String> =
            o?.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }?.toMap() ?: emptyMap()
    }
}

/**
 * THE JOURNEY'S AGENTS, by the name the SDK knows each by: the coach is the default agent (the app's main key), the
 * first-call agent is configured as "consultation" (its own key, `graine.consultationPublishableKey`), support is the
 * help agent (the help key).
 */
enum class JourneyAgent(val sdkName: String) {
    COACH("default"),
    FIRST_CALL("consultation"),
    SUPPORT("help");

    companion object {
        fun ofSdkName(name: String?): JourneyAgent? = entries.firstOrNull { it.sdkName == name }
    }
}
