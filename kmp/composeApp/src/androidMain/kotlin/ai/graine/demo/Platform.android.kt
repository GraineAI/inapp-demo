package ai.graine.demo.platform

import ai.graine.demo.BuildConfig
import ai.graine.demo.DemoApplication
import ai.graine.demo.OwnCallActivity
import ai.graine.demo.R
import ai.graine.demo.domain.AppConfig
import ai.graine.demo.journey.AndroidFiles
import ai.graine.demo.journey.AndroidReminders
import ai.graine.demo.journey.AndroidShare
import ai.graine.demo.journey.JourneyShare
import ai.graine.demo.journey.ReminderScheduler
import ai.graine.demo.journey.TextStore
import ai.graine.demo.MainActivity
import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ai.graine.demo.ui.DemoCard

actual object Platform {
    actual val name: String = "Android"
    actual val callIcon: Int = R.drawable.ic_stat_call
    actual val appVersion: String = BuildConfig.VERSION_NAME
    actual val debug: Boolean = BuildConfig.DEBUG

    actual fun config(): AppConfig = AppConfig.of(
        BuildConfig.GRAINE_BASE_URL,
        BuildConfig.GRAINE_PUBLISHABLE_KEY,
        BuildConfig.GRAINE_HELP_PUBLISHABLE_KEY,
        BuildConfig.GRAINE_CONSULTATION_PUBLISHABLE_KEY,
    )

    actual fun store(): KeyValueStore {
        val prefs = DemoApplication.instance.getSharedPreferences("graine-demo", Context.MODE_PRIVATE)
        return object : KeyValueStore {
            override fun getString(key: String): String? = prefs.getString(key, null)
            override fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()
        }
    }

    actual fun log(message: String) {
        Log.i("GraineDemo", message)
    }

    /** The SDK opens a call screen for every call: the app's own is OwnCallActivity, named by [ownCallActivity]. */
    actual val ownCallScreenInPlace: Boolean = false

    actual val ownCallActivity: String? = OwnCallActivity::class.java.name

    actual fun files(): TextStore = AndroidFiles(DemoApplication.instance)

    actual fun reminders(): ReminderScheduler = AndroidReminders(DemoApplication.instance)

    actual fun share(): JourneyShare = AndroidShare(DemoApplication.instance)

    actual val debugTools: Boolean = BuildConfig.DEBUG

    actual fun openApp() {
        val app = DemoApplication.instance
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun OnAppForeground(onForeground: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest = androidx.compose.runtime.rememberUpdatedState(onForeground)
    DisposableEffect(lifecycle) {
        var started = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (started) latest.value() else started = true
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
actual fun CallReadiness() {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    var missing by remember { mutableStateOf(NotificationAccess.missing(context)) }
    // Checked again each time the app comes back from the settings.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) missing = NotificationAccess.missing(context) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (missing.isEmpty()) return
    DemoCard("Calls on this phone") {
        missing.forEach { step ->
            Text(NotificationAccess.explain(step), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                when (step) {
                    NotificationAccess.Step.ALLOW_NOTIFICATIONS -> NotificationAccess.requestNotifications(activity)
                    NotificationAccess.Step.ALLOW_FULL_SCREEN -> NotificationAccess.openFullScreenSettings(activity)
                }
            }) { Text(NotificationAccess.action(step)) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * WHAT THIS PHONE NEEDS BEFORE THE AGENT'S CALLS CAN RING IT. Android 13+
 * asks for permission to post notifications (without it the phone cannot ring
 * at all). Android 14+ lets the user switch off full-screen intents, and then a
 * call rings as a heads-up notification instead of the full-screen call screen.
 */
object NotificationAccess {
    const val REQUEST_CODE = 0x4E54

    enum class Step { ALLOW_NOTIFICATIONS, ALLOW_FULL_SCREEN }

    fun missing(sdkInt: Int, notificationsAllowed: Boolean, fullScreenAllowed: Boolean): List<Step> = buildList {
        if (sdkInt >= 33 && !notificationsAllowed) add(Step.ALLOW_NOTIFICATIONS)
        if (sdkInt >= 34 && !fullScreenAllowed) add(Step.ALLOW_FULL_SCREEN)
    }

    fun missing(context: Context): List<Step> = missing(Build.VERSION.SDK_INT, notificationsAllowed(context), fullScreenAllowed(context))

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun fullScreenAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
    }

    fun requestNotifications(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 33) activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
    }

    fun openFullScreenSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < 34) return
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${activity.packageName}"))
        runCatching { activity.startActivity(intent) }.onFailure {
            // Some phones have no page for it: the app's notification settings are the next best place.
            runCatching { activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)) }
        }
    }

    fun explain(step: Step): String = when (step) {
        Step.ALLOW_NOTIFICATIONS -> "Allow notifications so the assistant's calls can ring this phone."
        Step.ALLOW_FULL_SCREEN -> "Allow full-screen calls so a call shows the call screen, even on the lock screen."
    }

    fun action(step: Step): String = when (step) {
        Step.ALLOW_NOTIFICATIONS -> "Allow notifications"
        Step.ALLOW_FULL_SCREEN -> "Open settings"
    }
}
