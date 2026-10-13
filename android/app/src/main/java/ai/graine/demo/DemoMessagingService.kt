package ai.graine.demo

import ai.graine.demo.graine.GraineService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM hands everything to the SDK first: the token (so Graine can ring this
 * install) and every data message (an agent's call rings; anything else is
 * the app's own).
 */
class DemoMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = GraineService.onNewToken(this, token)

    override fun onMessageReceived(message: RemoteMessage) {
        if (GraineService.handlePush(this, message.data)) return
        // The app's own messages would be handled here.
    }
}
