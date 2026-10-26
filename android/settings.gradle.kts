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

rootProject.name = "graine-demo-android"
include(":app")

/*
 * THE LOCAL-SDK SWITCH. The app depends on the published ai.graine:inapp-android:0.14.4 and its WebRTC
 * module, ai.graine:inapp-android-rtc:0.14.4. To build it against SDK sources on this machine instead (a
 * version not on Maven Central yet), set in local.properties — for a checkout of
 * https://github.com/GraineAI/inapp-android beside this repository:
 *
 *     graine.sdkDir=../../inapp-android
 *
 * (or -Pgraine.sdkDir=… / GRAINE_ANDROID_SDK_DIR). Gradle then builds the SDK and its WebRTC module from
 * that checkout as part of this build and uses them in place of the published artifacts — both, so the two
 * are always the same version. Remove the line to go back.
 */
val localProps = java.util.Properties().apply {
    val f = file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val sdkDir: String? = providers.gradleProperty("graine.sdkDir").orNull
    ?: localProps.getProperty("graine.sdkDir")
    ?: System.getenv("GRAINE_ANDROID_SDK_DIR")
if (!sdkDir.isNullOrBlank()) {
    val dir = file(sdkDir)
    require(dir.resolve("graine-inapp").isDirectory) { "graine.sdkDir=$sdkDir has no graine-inapp module" }
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("ai.graine:inapp-android")).using(project(":graine-inapp"))
            substitute(module("ai.graine:inapp-android-rtc")).using(project(":graine-inapp-rtc"))
        }
    }
}
