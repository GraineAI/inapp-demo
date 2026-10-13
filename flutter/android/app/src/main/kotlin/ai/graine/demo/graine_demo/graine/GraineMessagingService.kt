package ai.graine.demo.graine_demo.graine

import android.util.Log
import ai.graine.inapp.GraineCallsHost
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Hosted ringing on Android: hands this install's FCM token and every push to the Graine call engine,
 * natively — a call Graine rings arrives as a data message and rings even when the app was closed, before
 * any Dart runs. The same two lines the React Native demo's config plugin generates.
 *
 * An app that already has a FirebaseMessagingService (or uses firebase_messaging) forwards from that one
 * instead: Android delivers each push to one service. From Dart that is
 * GraineCalls.instance.registerPushToken(token) and GraineCalls.instance.handlePush(message.data).
 */
class GraineMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        GraineCallsHost.onNewToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (GraineCallsHost.handlePush(this, message.data)) return
        // Not a call. This app sends no other pushes; an app that does handles them here.
    }

    companion object {
        /** The current token: an install whose token predates this service never sees onNewToken again. */
        fun syncToken(context: android.content.Context) {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) return // no google-services.json: nothing to do
                FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                    if (!token.isNullOrEmpty()) GraineCallsHost.onNewToken(context.applicationContext, token)
                }
            } catch (e: Exception) {
                Log.w("GraineCalls", "No push token yet: ${e.message}")
            }
        }
    }
}
