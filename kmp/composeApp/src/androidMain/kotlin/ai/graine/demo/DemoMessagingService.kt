package ai.graine.demo

import ai.graine.inapp.kmp.Graine
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** The two lines the SDK asks for: the token, and every data message first. */
class DemoMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = Graine.calls.onNewToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        if (Graine.calls.handlePush(message.data)) return
        // The demo sends no messages of its own.
    }
}
