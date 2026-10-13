package ai.graine.demo

import ai.graine.demo.graine.GraineService
import ai.graine.demo.notify.NotificationAccess
import ai.graine.demo.ui.DemoApp
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private val model get() = (application as DemoApplication).model

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The voice engine is mounted once, idle (no microphone, nothing dialled) until "Talk".
        GraineService.get().attachVoice(this)
        model.activity = this
        setContent { DemoApp(model) }
        // Android 13+: without this the agent's calls cannot ring. Asked once, at first launch.
        if (savedInstanceState == null && NotificationAccess.missing(this).contains(NotificationAccess.Step.ALLOW_NOTIFICATIONS)) {
            NotificationAccess.requestNotifications(this)
        }
    }

    override fun onResume() {
        super.onResume()
        model.activity = this
    }

    override fun onDestroy() {
        if (model.activity === this) model.activity = null
        super.onDestroy()
    }

    @Deprecated("Forwarded for the SDK's microphone prompts")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        GraineService.get().onRequestPermissionsResult(requestCode, grantResults)
    }
}
