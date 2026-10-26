pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "graine-demo-kmp"
include(":composeApp")

/*
 * THE LOCAL-SDK SWITCH. The app depends on the published ai.graine:inapp-kmp and
 * ai.graine:inapp-kmp-compose 0.14.4. To build it against the SDK's sources on this machine instead
 * (a version not on Maven Central yet), set in local.properties — for checkouts of
 * https://github.com/GraineAI/inapp-kmp and inapp-android in ../graine-sdk/kmp and ../graine-sdk/android:
 *
 *     graine.kmpSdkDir=../../graine-sdk/kmp
 *
 * (or -Pgraine.kmpSdkDir=… / GRAINE_KMP_SDK_DIR). Gradle then builds the SDK from that checkout as part
 * of this build — its Android side with the Android SDK beside it — and uses it in place of the
 * published artifacts. Remove the line to go back.
 */
val localProps = java.util.Properties().apply {
    val f = file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val kmpSdkDir: String? = providers.gradleProperty("graine.kmpSdkDir").orNull
    ?: localProps.getProperty("graine.kmpSdkDir")
    ?: System.getenv("GRAINE_KMP_SDK_DIR")
if (!kmpSdkDir.isNullOrBlank()) {
    val dir = file(kmpSdkDir)
    require(dir.resolve("inapp-kmp").isDirectory) { "graine.kmpSdkDir=$kmpSdkDir has no inapp-kmp module" }
    // The SDK's builds find Android's SDK through ANDROID_HOME or their own local.properties — never this
    // build's sdk.dir, which is all Android Studio writes (and Xcode's Gradle phase has no ANDROID_HOME).
    // Without ANDROID_HOME, hand them this build's sdk.dir, the way Android's Gradle plugin also reads it.
    val sdkDir = localProps.getProperty("sdk.dir")
    if (System.getenv("ANDROID_HOME").isNullOrBlank() && System.getenv("ANDROID_SDK_ROOT").isNullOrBlank() && !sdkDir.isNullOrBlank()) {
        System.setProperty("android.home", sdkDir)
    }
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("ai.graine:inapp-kmp")).using(project(":inapp-kmp"))
            substitute(module("ai.graine:inapp-kmp-compose")).using(project(":inapp-kmp-compose"))
        }
    }
}
