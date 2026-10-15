package ai.graine.demo

import ai.graine.demo.domain.AppConfig
import ai.graine.demo.domain.UseCasePack
import ai.graine.demo.domain.UseCasePacks
import ai.graine.demo.graine.GraineService
import ai.graine.demo.ui.AppModel
import ai.graine.demo.ui.KeyValueStore
import android.app.Application
import android.content.Context

/**
 * Sets up the SDK and its calls on every launch — a push that wakes a closed
 * app rings only once this has run — and holds the app's state. Pushes need no
 * code here: with app/google-services.json in the build, the SDK's own messaging
 * service receives the token and the agent's calls, and the SDK reads the token
 * when it starts.
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
    }

    /** assets/usecases/<id>.json, bundled from demo/usecases by the build. */
    private fun readPack(id: String): String? =
        runCatching { assets.open("usecases/$id.json").bufferedReader().use { it.readText() } }.getOrNull()
}
