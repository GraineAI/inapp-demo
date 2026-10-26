package ai.graine.demo.journey

import kotlinx.cinterop.BetaInteropApi
import kotlinx.datetime.Clock
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** The Journey's files, in Application Support: each written whole to a new file, then put in place. */
@OptIn(BetaInteropApi::class)
class IosFiles : TextStore {
    private val dir: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String
            ?: NSTemporaryDirectory()
        val d = "$base/journey"
        NSFileManager.defaultManager.createDirectoryAtPath(d, withIntermediateDirectories = true, attributes = null, error = null)
        d
    }

    override fun read(name: String): String? = NSString.stringWithContentsOfFile("$dir/$name", encoding = NSUTF8StringEncoding, error = null)

    override fun write(name: String, text: String) {
        NSString.create(string = text).writeToFile("$dir/$name", atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    override fun delete(name: String) {
        NSFileManager.defaultManager.removeItemAtPath("$dir/$name", error = null)
    }
}

/**
 * CHECK-IN REMINDERS ON iOS: local notifications (UNUserNotificationCenter), each at its time; a tap reaches the
 * app through [JourneyNotifications], with the reminder's id.
 */
class IosReminders : ReminderScheduler {
    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun replaceAll(reminders: List<Reminder>) {
        val old = defaults.stringArrayForKey(KEY).orEmpty().filterIsInstance<String>()
        if (old.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(old.map { PREFIX + it })
        val now = Clock.System.now().toEpochMilliseconds()
        val scheduled = mutableListOf<String>()
        for (r in reminders) {
            val seconds = (r.at - now) / 1000.0
            if (seconds < 1) continue
            val content = UNMutableNotificationContent()
            content.setTitle(r.title)
            content.setBody(r.body)
            content.setSound(UNNotificationSound.defaultSound)
            content.setUserInfo(mapOf<Any?, Any?>(JourneyNotifications.ID to r.id))
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(PREFIX + r.id, content, trigger), withCompletionHandler = null)
            scheduled += r.id
        }
        defaults.setObject(scheduled, forKey = KEY)
    }

    override suspend fun requestPermission(): Boolean = suspendCoroutine { cont ->
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            dispatch_async(dispatch_get_main_queue()) { cont.resume(granted) }
        }
    }

    private companion object {
        const val KEY = "graine.journey.reminders"
        const val PREFIX = "journey."
    }
}

/**
 * THE APP'S NOTIFICATION DELEGATE: a check-in reminder tapped is handed to the journey (ReminderTaps), and one that
 * arrives while the app is open still shows as a banner. Installed at launch, before any tap can arrive.
 */
object JourneyNotifications {
    const val ID = "graine_journey_check_in"

    /** The delegate is weak in UIKit: kept here for the app's life. */
    private var installed: JourneyNotificationDelegate? = null

    fun install() {
        if (installed != null) return
        val delegate = JourneyNotificationDelegate()
        installed = delegate
        UNUserNotificationCenter.currentNotificationCenter().delegate = delegate
    }
}

class JourneyNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val id = didReceiveNotificationResponse.notification.request.content.userInfo[JourneyNotifications.ID] as? String
        if (id != null) dispatch_async(dispatch_get_main_queue()) { ReminderTaps.deliver(id) }
        withCompletionHandler()
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound)
    }
}

/** The share sheet, over the screen in front: the plan as text, the user's data as a JSON file. */
@OptIn(BetaInteropApi::class)
class IosShare : JourneyShare {
    override fun shareText(title: String, text: String) = present(listOf(text))

    override fun shareFile(fileName: String, text: String) {
        val path = NSTemporaryDirectory() + fileName
        NSString.create(string = text).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        present(listOf(NSURL.fileURLWithPath(path)))
    }

    private fun present(items: List<Any>) {
        val top = top() ?: return
        // The app is iPhone only (TARGETED_DEVICE_FAMILY 1): the sheet needs no popover anchor.
        val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
        top.presentViewController(sheet, animated = true, completion = null)
    }

    @Suppress("DEPRECATION")
    private fun top(): UIViewController? {
        val app = UIApplication.sharedApplication
        var c = (app.keyWindow ?: app.windows.firstOrNull() as? UIWindow)?.rootViewController
        while (c?.presentedViewController != null) c = c.presentedViewController
        return c
    }
}
