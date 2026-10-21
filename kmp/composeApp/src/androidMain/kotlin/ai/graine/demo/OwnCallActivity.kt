package ai.graine.demo

import ai.graine.demo.ui.DemoTheme
import ai.graine.demo.ui.own.OwnCallHost
import ai.graine.inapp.kmp.Graine
import ai.graine.inapp.kmp.onRequestPermissionsResult
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

/**
 * "YOUR OWN DESIGN"'S CALL SCREEN ON ANDROID. The Android SDK opens a call
 * screen for every call — placed, ringing (over the lock screen too) or missed
 * — and while the app draws its own, that screen is this activity
 * (GraineCallsConfig.androidCallActivity names it). It draws the shared OwnCallHost, the
 * same composable iOS draws in place, from Graine.calls.state only, and closes
 * itself once there is nothing left to show. Back leaves the screen, never the call.
 */
class OwnCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        val model = DemoBootstrap.start()
        setContent {
            DemoTheme(accent = model.pack?.accent) {
                OwnCallHost(model, Modifier.fillMaxSize(), onGone = { finishAndRemoveTask() })
            }
        }
    }

    // The SDK reads the intent it opened this screen with (answer from the notification, call back): keep the newest.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    @Deprecated("Forwarded for the SDK's microphone prompt, which a call screen asks from")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Graine.onRequestPermissionsResult(requestCode, grantResults)
    }

    @Deprecated("Back leaves the call screen, never the call")
    override fun onBackPressed() {
        if (!moveTaskToBack(true)) finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
