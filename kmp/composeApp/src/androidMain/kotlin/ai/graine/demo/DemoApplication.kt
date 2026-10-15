package ai.graine.demo

import android.app.Application

/**
 * Sets up the SDK and its calls on every launch — a push that wakes a closed
 * app rings only once this has run (DemoBootstrap, shared with iOS). Pushes need
 * no code here: with composeApp/google-services.json in the build, the Android
 * SDK's own messaging service receives the token and the agent's calls.
 */
class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        DemoBootstrap.start()
    }

    companion object {
        lateinit var instance: DemoApplication
            private set
    }
}
