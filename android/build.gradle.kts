plugins {
    // The same versions as the SDK's own build, so the local-SDK switch (settings.gradle.kts) can include it.
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    // Applied by app/ only when app/google-services.json exists.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
