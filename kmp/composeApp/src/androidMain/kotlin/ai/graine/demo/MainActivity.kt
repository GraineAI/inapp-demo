package ai.graine.demo

import ai.graine.demo.journey.AndroidReminders
import ai.graine.demo.journey.ReminderTaps
import ai.graine.demo.platform.NotificationAccess
import ai.graine.demo.ui.DemoApp
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.onRequestPermissionsResult
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = DemoBootstrap.start()
        setContent { DemoApp(model) }
        // Android 13+: without this the agent's calls cannot ring. Asked once, at first launch.
        if (savedInstanceState == null && NotificationAccess.missing(this).contains(NotificationAccess.Step.ALLOW_NOTIFICATIONS)) {
            NotificationAccess.requestNotifications(this)
        }
        if (savedInstanceState == null) checkInTapped(intent)
    }

    // A check-in reminder tapped while the app is open (the activity is singleTask).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkInTapped(intent)
    }

    /** The Journey's check-in reminder that opened the app: the chat, and the coach told which item. */
    private fun checkInTapped(intent: Intent?) {
        val id = intent?.getStringExtra(AndroidReminders.EXTRA_ID) ?: return
        intent.removeExtra(AndroidReminders.EXTRA_ID)
        ReminderTaps.deliver(id)
    }

    @Deprecated("Forwarded for the SDK's microphone prompts")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Graine.onRequestPermissionsResult(requestCode, grantResults)
    }
}
