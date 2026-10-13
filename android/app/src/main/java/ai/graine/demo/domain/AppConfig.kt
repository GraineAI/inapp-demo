package ai.graine.demo.domain

/**
 * The publishable key and base URL, from local.properties (git-ignored; see
 * local.properties.example) through BuildConfig.
 */
data class AppConfig(
    val baseUrl: String,
    val publishableKey: String,
    /** The help desk agent's key (graine.helpPublishableKey). Optional: without it the Help screen says how to add one. */
    val helpPublishableKey: String = "",
) {
    /** A key is present. Without one the app runs, and every agent feature reports that it is not set up. */
    val hasKey: Boolean get() = publishableKey.startsWith("pk_")

    /** The second agent is configured. */
    val hasHelpKey: Boolean get() = helpPublishableKey.startsWith("pk_")

    companion object {
        const val DEFAULT_BASE_URL = "https://www.graine.ai"

        fun of(baseUrl: String?, publishableKey: String?, helpPublishableKey: String? = null) = AppConfig(
            baseUrl = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_BASE_URL,
            publishableKey = publishableKey?.trim() ?: "",
            helpPublishableKey = helpPublishableKey?.trim() ?: "",
        )
    }
}
