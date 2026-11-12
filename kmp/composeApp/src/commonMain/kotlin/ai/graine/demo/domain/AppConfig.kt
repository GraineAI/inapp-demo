package ai.graine.demo.domain

/**
 * The publishable keys, the base URL and the packs a build offers: on Android from local.properties (git-ignored;
 * see local.properties.example) through BuildConfig, on iOS from Config.xcconfig
 * through Info.plist.
 */
data class AppConfig(
    val baseUrl: String,
    val publishableKey: String,
    /** The help desk agent's key (graine.helpPublishableKey). Optional: without it the Help screen says how to add one. */
    val helpPublishableKey: String = "",
    /** The Journey's first-call agent's key (graine.consultationPublishableKey), configured as "consultation". Optional. */
    val consultationPublishableKey: String = "",
    /**
     * The use-case packs this build offers (graine.packs / GRAINE_PACKS: pack ids, comma-separated), in that order;
     * the first opens on a device that never chose. Empty: every bundled pack.
     */
    val packIds: List<String> = emptyList(),
    /**
     * Agents of a pack's own, by pack id (graine.pack.<id>.publishableKey and .helpPublishableKey; on iOS
     * GRAINE_<ID>_PUBLISHABLE_KEY and GRAINE_<ID>_HELP_PUBLISHABLE_KEY): while that pack is open its screens talk to
     * these. The keys above serve every pack without its own. Only a pack with its own main key is here.
     */
    val packKeys: Map<String, PackKeys> = emptyMap(),
) {
    /** A key is present. Without one the app runs, and every agent feature reports that it is not set up. */
    val hasKey: Boolean get() = publishableKey.startsWith("pk_")

    /** The second agent is configured. */
    val hasHelpKey: Boolean get() = helpPublishableKey.startsWith("pk_")

    /** The Journey's first-call agent is configured. */
    val hasConsultationKey: Boolean get() = consultationPublishableKey.startsWith("pk_")

    /** The keys [packId]'s screens use: its own agents', else the app's. */
    fun keysFor(packId: String?): PackKeys = packKeys[packId] ?: PackKeys(publishableKey, helpPublishableKey)

    /** Where [packId]'s help desk key is set, in the words the Help screen uses. */
    fun helpKeySetting(packId: String?): String =
        if (packId != null && packId in packKeys) {
            "graine.pack.$packId.helpPublishableKey in local.properties on Android, GRAINE_${settingId(packId)}_HELP_PUBLISHABLE_KEY in Config.xcconfig on iOS"
        } else {
            "graine.helpPublishableKey in local.properties on Android, GRAINE_HELP_PUBLISHABLE_KEY in Config.xcconfig on iOS"
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.graine.ai"

        fun of(
            baseUrl: String?,
            publishableKey: String?,
            helpPublishableKey: String? = null,
            consultationPublishableKey: String? = null,
            packs: String? = null,
            packKeys: Map<String, Pair<String?, String?>> = emptyMap(),
        ) = AppConfig(
            baseUrl = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_BASE_URL,
            publishableKey = publishableKey?.trim() ?: "",
            helpPublishableKey = helpPublishableKey?.trim() ?: "",
            consultationPublishableKey = consultationPublishableKey?.trim() ?: "",
            packIds = packIdsOf(packs),
            packKeys = packKeysOf(packKeys),
        )

        /** The setting's pack ids: trimmed, blanks dropped, each once ("a, b,a" → [a, b]). */
        fun packIdsOf(raw: String?): List<String> =
            raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        /** Each pack's (main, help) keys as the settings give them: a pack keeps its own only when its main key is set. */
        fun packKeysOf(raw: Map<String, Pair<String?, String?>>): Map<String, PackKeys> = raw.mapNotNull { (id, keys) ->
            val main = keys.first?.trim().orEmpty()
            if (id.isBlank() || !main.startsWith("pk_")) null else id.trim() to PackKeys(main, keys.second?.trim().orEmpty())
        }.toMap()

        /** Android's BuildConfig.GRAINE_PACK_KEYS, as composeApp's build writes it: "<id>=<main>,<help>;…". */
        fun packKeysWire(raw: String?): Map<String, Pair<String?, String?>> = raw.orEmpty().split(';').mapNotNull { entry ->
            val id = entry.substringBefore('=', "").trim()
            if (id.isEmpty()) null else id to entry.substringAfter('=').split(',').let { it.getOrNull(0) to it.getOrNull(1) }
        }.toMap()

        /** A pack id as its iOS setting spells it: "retail-shopping" → "RETAIL_SHOPPING". */
        fun settingId(packId: String): String = packId.uppercase().replace('-', '_')
    }
}

/** One pack's own agents: its main agent's publishable key and, optionally, its help desk's. */
data class PackKeys(val publishableKey: String, val helpPublishableKey: String = "") {
    val hasKey: Boolean get() = publishableKey.startsWith("pk_")
    val hasHelpKey: Boolean get() = helpPublishableKey.startsWith("pk_")
}
