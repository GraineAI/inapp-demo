package ai.graine.demo

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.domain.UseCasePacks
import ai.graine.demo.graine.GraineService
import ai.graine.demo.ui.AppModel
import ai.graine.demo.ui.KeyValueStore
import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Sets up the SDK and its calls on every launch — a push that wakes a closed
 * app rings only once this has run — and holds the app's state.
 */
class DemoApplication : Application() {
    lateinit var model: AppModel
        private set

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("graine-demo", Context.MODE_PRIVATE)
        val store = object : KeyValueStore {
            override fun getString(key: String): String? = prefs.getString(key, null)
            override fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()
        }
        val config = AppConfig.of(BuildConfig.GRAINE_BASE_URL, BuildConfig.GRAINE_PUBLISHABLE_KEY, BuildConfig.GRAINE_HELP_PUBLISHABLE_KEY)
        val packs = UseCasePacks.load { id -> readPack(id) }
        val pack: UseCasePack? = UseCasePacks.chosen(packs, store.getString(UseCasePacks.CHOICE_KEY))

        val graine = GraineService.start(
            app = this,
            config = config,
            callName = pack?.call?.callName ?: getString(R.string.app_name),
            appVersion = BuildConfig.VERSION_NAME,
            debug = BuildConfig.DEBUG,
        )
        model = AppModel(graine, config, packs, store)
        forwardPushToken()
    }

    /** assets/usecases/<id>.json, bundled from demo/usecases by the build. */
    private fun readPack(id: String): String? =
        runCatching { assets.open("usecases/$id.json").bufferedReader().use { it.readText() } }.getOrNull()

    /**
     * Hand the current FCM token to the SDK too (onNewToken only fires when it changes). Without
     * app/google-services.json there is no Firebase app: the demo runs, and nothing can ring it.
     */
    private fun forwardPushToken() {
        if (FirebaseApp.getApps(this).isEmpty()) {
            Log.i(TAG, "No google-services.json in this build: push ringing is off.")
            return
        }
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> GraineService.onNewToken(this, token) }
            .addOnFailureListener { e -> Log.w(TAG, "No FCM token: ${e.message}") }
    }

    companion object {
        private const val TAG = "GraineDemo"
    }
}
