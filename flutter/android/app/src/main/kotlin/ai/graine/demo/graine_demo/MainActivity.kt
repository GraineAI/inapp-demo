package ai.graine.demo.graine_demo

import android.os.Bundle
import ai.graine.demo.graine_demo.graine.GraineMessagingService
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hand today's push token to the Graine SDK (hosted ringing).
        GraineMessagingService.syncToken(this)
    }
}
