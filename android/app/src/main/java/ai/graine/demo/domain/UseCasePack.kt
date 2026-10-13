package ai.graine.demo.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * A USE-CASE PACK: every domain word the demo shows — the assistant's name,
 * the fictional users, the list on the Items screen, the actions the agent
 * can run, the calls it can book. One JSON file per use case in
 * `demo/usecases/` (schema: `usecase.schema.json`, rules: its README), shared
 * by every platform and bundled into the app unchanged. The app's code is the
 * same for every pack.
 */
data class UseCasePack(
    val schemaVersion: Int,
    val id: String,
    val appTitle: String,
    val agentName: String,
    val agentRole: String,
    val tagline: String,
    val notice: String?,
    val accent: String?,
    val identifierField: String,
    val identifierLabel: String,
    val userNoun: String,
    val users: List<User>,
    val home: Home,
    val items: Items,
    val actions: List<Action>,
    val call: Call,
    val memoryExamples: List<MemoryExample>,
    val starters: List<String>,
    /** The main agent and the help desk; null in a pack written before there were two. */
    val agents: Agents? = null,
) {
    /**
     * One of the pack's agents (`agents.main`, `agents.help`). The help desk also carries its own call settings:
     * the name its calls ring with, the reason on a call placed from Help, its booking button and reasons.
     */
    data class AgentInfo(
        val name: String,
        val role: String? = null,
        val starters: List<String>? = null,
        val callName: String? = null,
        val placeReason: String? = null,
        val bookLabel: String? = null,
        val reasons: List<String>? = null,
    )

    data class Agents(val main: AgentInfo?, val help: AgentInfo?)

    /** The booking button for an agent: the help desk's own, else the pack's. */
    fun bookLabel(agent: DemoAgent): String = (if (agent == DemoAgent.HELP) agents?.help?.bookLabel else null) ?: call.bookLabel

    /** The booking reasons for an agent: the help desk's own, else the pack's. */
    fun reasons(agent: DemoAgent): List<String> = (if (agent == DemoAgent.HELP) agents?.help?.reasons else null) ?: call.reasons

    /** The reason a call placed with an agent carries: the help desk's own, else the pack's. */
    fun placeReason(agent: DemoAgent): String = (if (agent == DemoAgent.HELP) agents?.help?.placeReason else null) ?: call.placeReason

    /** The agent's profile: the pack's `agents.main` / `agents.help`; the main one falls back to the pack's own name, role and starters. */
    fun profile(agent: DemoAgent): AgentProfile? = when (agent) {
        DemoAgent.MAIN -> agents?.main.let { AgentProfile(it?.name ?: agentName, it?.role ?: agentRole, it?.starters ?: starters) }
        DemoAgent.HELP -> agents?.help?.let { AgentProfile(it.name, it.role ?: "", it.starters ?: emptyList()) }
    }

    data class User(
        /** The identifier's value: sent as traits[identifierField]. */
        val id: String,
        val name: String,
        /** Extra prompt variables (strings). */
        val traits: Map<String, String>,
    ) {
        val firstName: String get() = name.split(" ").firstOrNull()?.takeIf { it.isNotEmpty() } ?: name
    }

    data class Home(val title: String, val cards: List<HomeCard>)

    /** [target]: the generic screen a tap opens (items, calls, memory, chat or profile). */
    data class HomeCard(val id: String, val title: String, val body: String, val target: String)

    data class Field(val name: String, val label: String, val value: String)

    data class Row(
        val id: String,
        val title: String,
        val subtitle: String,
        /** The starting status chip; a set_status action replaces it. */
        val status: String?,
        val fields: List<Field>,
        /** The set_status actions this row offers as buttons and accepts from the agent. */
        val actions: List<String>,
        /** Shown only to these users. Null: to everyone. */
        val userIds: List<String>?,
    )

    data class Items(
        val title: String,
        val screenName: String,
        val itemNoun: String,
        val emptyText: String,
        val rows: List<Row>,
    )

    enum class Effect(val wire: String) {
        OPEN_SCREEN("open_screen"),
        OPEN_ITEM("open_item"),
        SET_STATUS("set_status");

        companion object {
            fun of(wire: String): Effect? = values().firstOrNull { it.wire == wire }
        }
    }

    data class Action(
        val name: String,
        val effect: String,
        /** Button text. */
        val label: String,
        val description: String,
        /** set_status only: the status the row gets. */
        val status: String?,
        val args: List<String>,
    ) {
        /** Null for an effect this build does not know (a newer pack): the agent is told so. */
        val knownEffect: Effect? get() = Effect.of(effect)
    }

    /** A one-tap time in the booking sheet: [inMinutes] from now, or [day] ("today" / "tomorrow") at [time] ("HH:MM"), in the device's zone. */
    data class QuickTime(val label: String, val inMinutes: Int? = null, val day: String? = null, val time: String? = null)

    data class Call(
        /** Name on the incoming call (matches the dashboard). */
        val callName: String,
        /** The scheduled-calls screen's title. */
        val title: String,
        /** The button that opens the booking sheet: "Schedule a consultation call". */
        val bookLabel: String,
        /** Offered when booking (one is chosen); sent as the booking's reason. */
        val reasons: List<String>,
        /** One-tap times in the booking sheet. */
        val quickTimes: List<QuickTime>,
        /** The reason a call the user places from Home carries. */
        val placeReason: String,
    )

    data class MemoryExample(val tell: String, val ask: String, val expect: String)

    /** identify's traits: the identifier under the agent's identity field, the name, and the extra traits. */
    fun traitsFor(user: User): Map<String, String> =
        LinkedHashMap(user.traits).apply {
            put("name", user.name)
            put(identifierField, user.id)
        }

    /** The rows [user] sees. */
    fun rowsFor(user: User): List<Row> = items.rows.filter { it.userIds?.contains(user.id) ?: true }

    fun action(name: String): Action? = actions.firstOrNull { it.name == name }

    class PackException(message: String) : Exception(message)

    companion object {
        @Throws(PackException::class)
        fun parse(json: String): UseCasePack = try {
            parse(JSONObject(json))
        } catch (e: PackException) {
            throw e
        } catch (e: Exception) {
            throw PackException("Not a use-case pack: ${e.message}")
        }

        fun parse(o: JSONObject): UseCasePack {
            val home = o.req("home")
            val items = o.req("items")
            val call = o.req("call")
            return UseCasePack(
                schemaVersion = o.getInt("schemaVersion"),
                id = o.str("id"),
                appTitle = o.str("appTitle"),
                agentName = o.str("agentName"),
                agentRole = o.str("agentRole"),
                tagline = o.str("tagline"),
                notice = o.optStr("notice"),
                accent = o.optStr("accent"),
                identifierField = o.str("identifierField"),
                identifierLabel = o.str("identifierLabel"),
                userNoun = o.str("userNoun"),
                users = o.objects("users").map { u ->
                    val t = u.optJSONObject("traits") ?: JSONObject()
                    User(u.str("id"), u.str("name"), t.keys().asSequence().associateWith { t.getString(it) })
                },
                home = Home(
                    title = home.str("title"),
                    cards = home.objects("cards").map { HomeCard(it.str("id"), it.str("title"), it.str("body"), it.str("target")) },
                ),
                items = Items(
                    title = items.str("title"),
                    screenName = items.str("screenName"),
                    itemNoun = items.str("itemNoun"),
                    emptyText = items.str("emptyText"),
                    rows = items.objects("rows").map { r ->
                        Row(
                            id = r.str("id"),
                            title = r.str("title"),
                            subtitle = r.optString("subtitle", ""),
                            status = r.optStr("status"),
                            fields = r.objects("fields").map { Field(it.str("name"), it.str("label"), it.optString("value", "")) },
                            actions = r.strings("actions"),
                            userIds = if (r.has("userIds")) r.strings("userIds") else null,
                        )
                    },
                ),
                actions = o.objects("actions").map { a ->
                    Action(
                        name = a.str("name"),
                        effect = a.str("effect"),
                        label = a.str("label"),
                        description = a.str("description"),
                        status = a.optStr("status"),
                        args = a.objects("args").map { it.str("name") },
                    )
                },
                call = Call(
                    callName = call.str("callName"),
                    title = call.str("title"),
                    bookLabel = call.optStr("bookLabel") ?: "Schedule a call",
                    reasons = call.strings("reasons"),
                    quickTimes = call.objects("quickTimes").mapNotNull { q ->
                        val label = q.optStr("label") ?: return@mapNotNull null
                        QuickTime(
                            label = label,
                            inMinutes = if (q.has("inMinutes")) q.optInt("inMinutes") else null,
                            day = q.optStr("day"),
                            time = q.optStr("time"),
                        )
                    },
                    placeReason = call.str("placeReason"),
                ),
                memoryExamples = o.objects("memoryExamples").map { MemoryExample(it.str("tell"), it.str("ask"), it.str("expect")) },
                starters = o.strings("starters"),
                agents = o.optJSONObject("agents")?.let { a -> Agents(agentInfo(a.optJSONObject("main")), agentInfo(a.optJSONObject("help"))) },
            )
        }

        /** Read leniently, so a pack written before the block existed, or with other words for the role, still reads. */
        private fun agentInfo(o: JSONObject?): AgentInfo? {
            if (o == null) return null
            val name = o.optStr("name") ?: o.optStr("agentName") ?: return null
            return AgentInfo(
                name = name,
                role = o.optStr("role") ?: o.optStr("agentRole") ?: o.optStr("roleLine"),
                starters = if (o.has("starters")) o.strings("starters") else null,
                callName = o.optStr("callName"),
                placeReason = o.optStr("placeReason"),
                bookLabel = o.optStr("bookLabel"),
                reasons = if (o.has("reasons")) o.strings("reasons") else null,
            )
        }

        private fun JSONObject.req(key: String): JSONObject = optJSONObject(key) ?: throw PackException("missing \"$key\"")
        private fun JSONObject.str(key: String): String =
            (opt(key) as? String) ?: throw PackException("missing \"$key\"")
        private fun JSONObject.optStr(key: String): String? = (opt(key) as? String)?.takeIf { it.isNotEmpty() }
        private fun JSONObject.objects(key: String): List<JSONObject> {
            val a = optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        }
        private fun JSONObject.strings(key: String): List<String> {
            val a: JSONArray = optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.opt(it) as? String }
        }
    }
}

/** The packs this app ships: demo/usecases/<id>.json, bundled as assets/usecases/. A new pack is added here too. */
object UseCasePacks {
    val bundled = listOf("health-coach", "banking-support", "retail-shopping")
    const val DEFAULT_ID = "health-coach"
    const val CHOICE_KEY = "demo.pack"

    /** [read] returns a pack file's text by id, or null. */
    fun load(read: (String) -> String?): List<UseCasePack> =
        bundled.mapNotNull { id -> read(id)?.let { runCatching { UseCasePack.parse(it) }.getOrNull() } }

    /** The pack the device chose last, else the default, else the first. */
    fun chosen(packs: List<UseCasePack>, savedId: String?): UseCasePack? =
        packs.firstOrNull { it.id == savedId } ?: packs.firstOrNull { it.id == DEFAULT_ID } ?: packs.firstOrNull()
}
