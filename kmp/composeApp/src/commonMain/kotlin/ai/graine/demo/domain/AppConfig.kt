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
     * the first opens on a device that never chose. Empty: every bundled pack. The keys above serve every pack the
     * build offers, so a build whose keys are one pack's agents names that pack.
     */
    val packIds: List<String> = emptyList(),
) {
    /** A key is present. Without one the app runs, and every agent feature reports that it is not set up. */
    val hasKey: Boolean get() = publishableKey.startsWith("pk_")

    /** The second agent is configured. */
    val hasHelpKey: Boolean get() = helpPublishableKey.startsWith("pk_")

    /** The Journey's first-call agent is configured. */
    val hasConsultationKey: Boolean get() = consultationPublishableKey.startsWith("pk_")

    companion object {
        const val DEFAULT_BASE_URL = "https://www.graine.ai"

        fun of(
            baseUrl: String?,
            publishableKey: String?,
            helpPublishableKey: String? = null,
            consultationPublishableKey: String? = null,
            packs: String? = null,
        ) = AppConfig(
            baseUrl = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_BASE_URL,
            publishableKey = publishableKey?.trim() ?: "",
            helpPublishableKey = helpPublishableKey?.trim() ?: "",
            consultationPublishableKey = consultationPublishableKey?.trim() ?: "",
            packIds = packIdsOf(packs),
        )

        /** The setting's pack ids: trimmed, blanks dropped, each once ("a, b,a" → [a, b]). */
        fun packIdsOf(raw: String?): List<String> =
            raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }
}
