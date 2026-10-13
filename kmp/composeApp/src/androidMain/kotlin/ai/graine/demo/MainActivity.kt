package ai.graine.demo

import ai.graine.demo.platform.NotificationAccess
import ai.graine.demo.ui.DemoApp
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.onRequestPermissionsResult
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
    }

    @Deprecated("Forwarded for the SDK's microphone prompts")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Graine.onRequestPermissionsResult(requestCode, grantResults)
    }
}
