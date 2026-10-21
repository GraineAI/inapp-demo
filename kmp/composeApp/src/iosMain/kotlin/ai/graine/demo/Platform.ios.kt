package ai.graine.demo.platform

import ai.graine.demo.domain.AppConfig
import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

actual object Platform {
    actual val name: String = "iOS"
    actual val callIcon: Int = 0
    actual val appVersion: String = info("CFBundleShortVersionString") ?: "1.0.0"
    actual val debug: Boolean = false

    /** From Info.plist, which reads them from Config.xcconfig (see iosApp/Config.example.xcconfig). */
    actual fun config(): AppConfig = AppConfig.of(info("GraineBaseURL"), info("GrainePublishableKey"), info("GraineHelpPublishableKey"))

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

    private fun info(key: String): String? = (NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String)?.takeIf { it.isNotBlank() }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

/** CallKit rings an iOS app by itself: nothing for the user to allow here. */
@Composable
actual fun CallReadiness() = Unit
