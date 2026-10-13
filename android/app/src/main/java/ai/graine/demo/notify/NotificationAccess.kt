package ai.graine.demo.notify

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * WHAT THIS PHONE NEEDS BEFORE THE AGENT'S CALLS CAN RING IT.
 *
 * Android 13+ asks for permission to post notifications (without it the
 * phone cannot ring at all). Android 14+ lets the user switch off full-screen
 * intents, and then a call rings as a heads-up notification instead of the
 * full-screen call screen; the switch lives in the system settings.
 */
object NotificationAccess {
    const val REQUEST_CODE = 0x4E54

    /** One thing still to do. */
    enum class Step { ALLOW_NOTIFICATIONS, ALLOW_FULL_SCREEN }

    /** Pure, for the tests: what is missing, given the Android version and what the user allowed. */
    fun missing(sdkInt: Int, notificationsAllowed: Boolean, fullScreenAllowed: Boolean): List<Step> = buildList {
        if (sdkInt >= 33 && !notificationsAllowed) add(Step.ALLOW_NOTIFICATIONS)
        if (sdkInt >= 34 && !fullScreenAllowed) add(Step.ALLOW_FULL_SCREEN)
    }

    fun missing(context: Context): List<Step> =
        missing(Build.VERSION.SDK_INT, notificationsAllowed(context), fullScreenAllowed(context))

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun fullScreenAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
    }

    /** Android 13+: the system's permission prompt. The answer arrives in onRequestPermissionsResult. */
    fun requestNotifications(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        }
    }

    /** Android 14+: this app's full-screen call setting, in the system settings. */
    fun openFullScreenSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < 34) return
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${activity.packageName}"))
        runCatching { activity.startActivity(intent) }.onFailure {
            // Some phones have no page for it: the app's notification settings are the next best place.
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
            }
        }
    }

    /** What each step says to the user. */
    fun explain(step: Step): String = when (step) {
        Step.ALLOW_NOTIFICATIONS -> "Allow notifications so the assistant's calls can ring this phone."
        Step.ALLOW_FULL_SCREEN -> "Allow full-screen calls so a call shows the call screen, even on the lock screen."
    }

    fun action(step: Step): String = when (step) {
        Step.ALLOW_NOTIFICATIONS -> "Allow notifications"
        Step.ALLOW_FULL_SCREEN -> "Open settings"
    }
}
