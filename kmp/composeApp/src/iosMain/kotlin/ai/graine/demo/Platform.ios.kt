package ai.graine.demo.platform

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.journey.IosFiles
import ai.graine.demo.journey.IosReminders
import ai.graine.demo.journey.IosShare
import ai.graine.demo.journey.JourneyShare
import ai.graine.demo.journey.ReminderScheduler
import ai.graine.demo.journey.TextStore
import androidx.compose.runtime.Composable
import kotlin.experimental.ExperimentalNativeApi
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

actual object Platform {
    actual val name: String = "iOS"
    actual val callIcon: Int = 0
    actual val appVersion: String = info("CFBundleShortVersionString") ?: "1.0.0"
    actual val debug: Boolean = false

    /** From Info.plist, which reads them from Config.xcconfig (see iosApp/Config.example.xcconfig). */
    actual fun config(): AppConfig = AppConfig.of(
        info("GraineBaseURL"),
        info("GrainePublishableKey"),
        info("GraineHelpPublishableKey"),
        info("GraineConsultationPublishableKey"),
    )

    actual fun store(): KeyValueStore = object : KeyValueStore {
        private val defaults = NSUserDefaults.standardUserDefaults
        override fun getString(key: String): String? = defaults.stringForKey(key)
        override fun putString(key: String, value: String?) {
            if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
        }
    }

    actual fun log(message: String) {
        println("GraineDemo: $message")
    }

    /** The iOS SDK opens no call screen of its own (CallKit rings; the app shows its screen): the app's is drawn in place. */
    actual val ownCallScreenInPlace: Boolean = true

    /** The SDK's `androidCallActivity` is Android's: iOS draws its own call screen over CallKit's. */
    actual val ownCallActivity: String? = null

    actual fun files(): TextStore = IosFiles()

    actual fun reminders(): ReminderScheduler = IosReminders()

    actual fun share(): JourneyShare = IosShare()

    /** A debug framework (Xcode's Debug configuration). */
    @OptIn(ExperimentalNativeApi::class)
    actual val debugTools: Boolean = kotlin.native.Platform.isDebugBinary

    /** The call screen is drawn over the app: the app is already in front. */
    actual fun openApp() = Unit

    private fun info(key: String): String? = (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String)?.takeIf { it.isNotBlank() }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

@Composable
actual fun OnAppForeground(onForeground: () -> Unit) {
    val latest = androidx.compose.runtime.rememberUpdatedState(onForeground)
    androidx.compose.runtime.DisposableEffect(Unit) {
        val center = platform.Foundation.NSNotificationCenter.defaultCenter
        val token = center.addObserverForName(platform.UIKit.UIApplicationWillEnterForegroundNotification, null, platform.Foundation.NSOperationQueue.mainQueue) { _ ->
            latest.value()
        }
        onDispose { center.removeObserver(token) }
    }
}

/** CallKit rings an iOS app by itself: nothing for the user to allow here. */
@Composable
actual fun CallReadiness() = Unit
