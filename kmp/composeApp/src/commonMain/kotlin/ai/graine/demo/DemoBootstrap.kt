package ai.graine.demo

import ai.graine.demo.domain.UseCasePacks
import ai.graine.demo.graine.GraineService
import ai.graine.demo.platform.Platform
import ai.graine.demo.ui.AppModel

/**
 * The app's start, the same on both platforms: the SDK and its calls set up
 * first (a push that wakes a closed app rings only once this has run), then the
 * app's state. Android calls it from Application.onCreate, iOS when the app
 * starts. Main thread; once per launch.
 */
object DemoBootstrap {
    private var model: AppModel? = null

    fun start(): AppModel {
        model?.let { return it }
        val config = Platform.config()
        val store = Platform.store()
        val packs = UseCasePacks.load()
        val pack = UseCasePacks.chosen(packs, store.getString(UseCasePacks.CHOICE_KEY))
        val graine = GraineService.start(
            config = config,
            callName = pack?.call?.callName ?: "Graine Demo",
            appVersion = Platform.appVersion,
            debug = Platform.debug,
        )
        return AppModel(graine, config, packs, store).also { model = it }
    }
}
