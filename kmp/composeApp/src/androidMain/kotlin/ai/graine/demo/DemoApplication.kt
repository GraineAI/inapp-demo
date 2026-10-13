package ai.graine.demo

import ai.graine.inapp.kmp.Graine
import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Sets up the SDK and its calls on every launch — a push that wakes a closed
 * app rings only once this has run (DemoBootstrap, shared with iOS).
 */
class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        DemoBootstrap.start()
        forwardPushToken()
    }

    /**
     * Hand the current FCM token to the SDK too (onNewToken only fires when it changes). Without
     * composeApp/google-services.json there is no Firebase app: the demo runs, and nothing can ring it.
     */
    private fun forwardPushToken() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            Log.i(TAG, "No google-services.json in this build: push ringing is off.")
            return
        }
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> Graine.calls.onNewToken(token) }
            .addOnFailureListener { e -> Log.w(TAG, "No FCM token: ${e.message}") }
    }

    companion object {
        private const val TAG = "GraineDemo"
        lateinit var instance: DemoApplication
            private set
    }
}
