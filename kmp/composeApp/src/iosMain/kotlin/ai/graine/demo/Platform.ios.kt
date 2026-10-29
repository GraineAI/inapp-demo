package ai.graine.demo.platform

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.journey.IosFiles
import ai.graine.demo.journey.IosReminders
import ai.graine.demo.journey.IosShare
import ai.graine.demo.journey.JourneyShare
import ai.graine.demo.journey.ReminderScheduler
import ai.graine.demo.journey.TextStore
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.experimental.ExperimentalNativeApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import org.jetbrains.skia.Image
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.NSBundle
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataTaskWithURL
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue

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

    @OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
    actual suspend fun httpPost(url: String, json: String): String? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            cont.resumeWith(Result.success(null))
            return@suspendCancellableCoroutine
        }
        // HTTPMethod, HTTPBody, setValue(_:forHTTPHeaderField:), dataUsingEncoding and the completion-handler
        // dataTask calls come from Objective-C categories: Kotlin has them as imported extensions, not members.
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        request.HTTPMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField = "Content-Type")
        request.HTTPBody = NSString.create(string = json).dataUsingEncoding(NSUTF8StringEncoding)
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
            val body = if (error == null && status in 200..299 && data != null) {
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
            } else null
            cont.resumeWith(Result.success(body))
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }

    @OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
    actual suspend fun httpGet(url: String): String? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            cont.resumeWith(Result.success(null))
            return@suspendCancellableCoroutine
        }
        val task = NSURLSession.sharedSession.dataTaskWithURL(nsUrl) { data, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
            val body = if (error == null && status in 200..299 && data != null) {
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
            } else null
            cont.resumeWith(Result.success(body))
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    actual suspend fun picture(url: String): ImageBitmap? = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            cont.resumeWith(Result.success(null))
            return@suspendCancellableCoroutine
        }
        // NSURLSession answers on its own queue, off the main thread: the picture is decoded there too.
        val task = NSURLSession.sharedSession.dataTaskWithURL(nsUrl) { data, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
            val bytes = if (error == null && status in 200..299 && data != null) data.bytes?.reinterpret<ByteVar>()?.readBytes(data.length.toInt()) else null
            val image = bytes?.let { runCatching { Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
            cont.resumeWith(Result.success(image))
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
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
