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
 * THE LOCAL-SDK SWITCH. The app depends on the published ai.graine:inapp-android:0.10.1. To build it
 * against SDK sources on this machine instead (a version not on Maven Central yet), set in
 * local.properties — for a checkout of https://github.com/GraineAI/inapp-android beside this repository:
 *
 *     graine.sdkDir=../../inapp-android
 *
 * (or -Pgraine.sdkDir=… / GRAINE_ANDROID_SDK_DIR). Gradle then builds the SDK from that checkout as part
 * of this build and uses it in place of the published artifact. Remove the line to go back.
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
        }
    }
}
