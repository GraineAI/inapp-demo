package ai.graine.demo.journey

import ai.graine.inapp.kmp.GraineMemory
import ai.graine.inapp.kmp.GraineRole
import ai.graine.inapp.kmp.GraineSessionSummary
import ai.graine.inapp.kmp.GraineSessionTranscript
import kotlinx.datetime.Instant

/**
 * DOWNLOAD MY DATA, as one JSON file: who the user is on this app, their profile, what the first call learned, the
 * plan and what they did with it — all kept on this phone — and from Graine what the coach remembers about them and
 * their conversations with each agent, with each one's messages.
 */
object JourneyExport {
    fun build(
        journey: JourneyPack,
        account: JourneyAccount,
        profile: JourneyProfile,
        intake: Intake?,
        plan: Plan?,
        activity: JourneyActivity,
        memories: List<GraineMemory>,
        sessions: Map<String, List<Map<String, Any?>>>,
        problems: List<String>,
        exportedAt: Long,
    ): String = pretty(
        linkedMapOf(
            "exported_at" to iso(exportedAt),
            "user" to linkedMapOf(journey.identifierField to account.userId, "since" to iso(account.createdAt)),
            "profile" to linkedMapOf("answers" to profile.values, "language" to profile.languageId),
            "consultation" to intake?.let { LinkedHashMap(it.values).apply { put("saved_at", iso(it.savedAt)) } },
            "plan" to plan?.let { LinkedHashMap(it.toWire()).apply { put("saved_at", iso(it.savedAt)); put("source", it.source.wire) } },
            "check_ins" to activity.checkIns.map {
                linkedMapOf("date" to it.date.toString(), "time" to it.time, "followed" to it.followed, "note" to it.note, "from" to it.source)
            },
            "daily_counter" to activity.tracker.entries.sortedBy { it.key }.associate { (d, v) -> d.toString() to v },
            "measures" to activity.measures.map { linkedMapOf("date" to it.date.toString(), "value" to it.value) },
            "weekly_check_ins" to activity.weekly.entries.sortedBy { it.key }.associate { (d, v) -> d.toString() to v },
            "remembered" to memories.map { m -> linkedMapOf("text" to m.text, "created_at" to m.createdAt?.toString(), "updated_at" to m.updatedAt?.toString()) },
            "conversations" to sessions,
            "not_included" to problems,
        ),
    )

    /** One past conversation: what the list says of it, and its messages when they could be read. */
    fun session(s: GraineSessionSummary, transcript: GraineSessionTranscript?): Map<String, Any?> = linkedMapOf(
        "id" to s.id,
        "started_at" to s.startedAt.toString(),
        "ended_at" to s.endedAt?.toString(),
        "seconds" to s.durationSeconds,
        "channel" to s.channel.wire,
        "status" to s.status.ifBlank { null },
        "messages" to transcript?.turns?.map { t ->
            linkedMapOf("from" to if (t.role == GraineRole.USER) "you" else "agent", "text" to t.text, "at" to t.at.toString())
        },
    )

    private fun iso(ms: Long): String = Instant.fromEpochMilliseconds(ms - ms.mod(1000L)).toString()

    /** JSON with two-space indents and a final newline, for a person to read. */
    fun pretty(value: Any?): String = buildString { put(this, value, 0); append('\n') }

    private fun put(out: StringBuilder, value: Any?, depth: Int) {
        val pad = "  ".repeat(depth + 1)
        val end = "  ".repeat(depth)
        when (value) {
            is Map<*, *> -> {
                if (value.isEmpty()) { out.append("{}"); return }
                out.append("{\n")
                value.entries.forEachIndexed { i, (k, v) ->
                    out.append(pad).append(ai.graine.demo.domain.Json.write(k.toString())).append(": ")
                    put(out, v, depth + 1)
                    out.append(if (i < value.size - 1) ",\n" else "\n")
                }
                out.append(end).append('}')
            }
            is Iterable<*> -> {
                val items = value.toList()
                if (items.isEmpty()) { out.append("[]"); return }
                out.append("[\n")
                items.forEachIndexed { i, v ->
                    out.append(pad)
                    put(out, v, depth + 1)
                    out.append(if (i < items.size - 1) ",\n" else "\n")
                }
                out.append(end).append(']')
            }
            is Double -> out.append(if (value.isFinite()) Args.numberText(value) else "null")
            else -> out.append(ai.graine.demo.domain.Json.write(value))
        }
    }
}

/** The plan as a person shares it: the title, the targets, and each day's timed items. */
object PlanText {
    fun share(journey: JourneyPack, plan: Plan): String = buildString {
        appendLine(journey.plan.shareIntro)
        appendLine()
        appendLine(plan.title)
        val targets = journey.plan.targets.mapNotNull { m -> plan.targets[m.key]?.let { "${m.label} ${JourneyText.figure(it, m.unit)}" } }
        if (targets.isNotEmpty()) appendLine("${journey.plan.targetsTitle}: ${targets.joinToString(" · ")}")
        for (day in plan.days) {
            appendLine()
            appendLine(day.day.label)
            for (item in day.items) {
                appendLine("${JourneyTime.spoken(item.time)}  ${journey.plan.kindLabel(item.kind)}: ${item.title}")
            }
        }
    }.trimEnd() + "\n"
}
