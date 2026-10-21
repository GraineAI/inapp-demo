package ai.graine.demo.platform

import ai.graine.demo.domain.AppConfig
import androidx.compose.runtime.Composable

/** Where the app keeps its few on-device choices (the pack, the call log): SharedPreferences on Android, UserDefaults on iOS. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

/** What differs between Android and iOS — and only that; every screen and rule is shared. */
expect object Platform {
    /** "Android" or "iOS". */
    val name: String
    /** Android: the monochrome status-bar icon for calls. iOS: 0 (CallKit draws its own). */
    val callIcon: Int
    val appVersion: String
    val debug: Boolean
    /** The publishable keys and base URL this build was given. */
    fun config(): AppConfig
    fun store(): KeyValueStore
    fun log(message: String)

    /**
     * Where "Your own design" draws the call screen: in place, over the app (iOS — the SDK opens no screen of its
     * own), or false where it is a screen the SDK opens for every call, ring and missed call (Android: the app's
     * OwnCallActivity, [ownCallActivity], which draws the same shared composable over the lock screen too).
     */
    val ownCallScreenInPlace: Boolean

    /**
     * The app's own call screen on Android, by its full class name, for `GraineCallsConfig.androidCallActivity`: the
     * SDK opens it instead of its own for every call, ring and missed call while the app draws its own. Null on iOS.
     */
    val ownCallActivity: String?
}

/** The system back gesture (Android). iOS has none: every screen here has its own Back or Close. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * Whether this phone can ring for the agent's calls: on Android, notifications
 * (13+) and full-screen calls (14+), each with a button to fix it. iOS rings
 * through CallKit and needs nothing here.
 */
@Composable
expect fun CallReadiness()
