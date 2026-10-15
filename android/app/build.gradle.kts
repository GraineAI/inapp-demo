import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase (the agent's calls ring through FCM) needs app/google-services.json, which is never
// committed. Without it the app still builds and runs; only push ringing is off.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Per-developer values from local.properties (git-ignored; see local.properties.example).
val local = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun setting(key: String, env: String, default: String): String =
    local.getProperty(key)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() } ?: default

android {
    namespace = "ai.graine.demo"
    compileSdk = 36

    defaultConfig {
        applicationId = setting("graine.applicationId", "GRAINE_APPLICATION_ID", "com.example.grainedemo")
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "GRAINE_PUBLISHABLE_KEY", "\"${setting("graine.publishableKey", "GRAINE_PUBLISHABLE_KEY", "")}\"")
        // Optional: a second agent (the pack's help desk) for the Help tab.
        buildConfigField("String", "GRAINE_HELP_PUBLISHABLE_KEY", "\"${setting("graine.helpPublishableKey", "GRAINE_HELP_PUBLISHABLE_KEY", "")}\"")
        buildConfigField("String", "GRAINE_BASE_URL", "\"${setting("graine.baseUrl", "GRAINE_BASE_URL", "https://www.graine.ai")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            // The tests read the shipped packs where they live.
            it.systemProperty("demo.usecases", rootProject.file("../usecases").absolutePath)
            it.inputs.dir(rootProject.file("../usecases")).withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

/**
 * The shared use-case packs (the .json files in demo/usecases), bundled unchanged as assets/usecases.
 * One copy for every platform: edit the packs there, never here.
 */
abstract class BundleUseCases : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val packs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val out = outputDir.get().asFile.resolve("usecases")
        out.deleteRecursively()
        out.mkdirs()
        packs.files.forEach { it.copyTo(out.resolve(it.name), overwrite = true) }
    }
}

val bundleUseCases = tasks.register<BundleUseCases>("bundleUseCases") {
    packs.from(fileTree(rootProject.file("../usecases")) {
        include("*.json")
        exclude("*.schema.json")
    })
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(bundleUseCases, BundleUseCases::outputDir)
    }
}

dependencies {
    // The Graine in-app SDK, as published. The local-SDK switch in settings.gradle.kts replaces it.
    implementation("ai.graine:inapp-android:0.10.1")
    // WebRTC calls: with this module, in-app calls and answered rings carry their audio over WebRTC.
    implementation("ai.graine:inapp-android-rtc:0.10.1")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")

    testImplementation("junit:junit:4.13.2")
    // org.json is part of Android but stubbed in JVM unit tests: the real one here.
    testImplementation("org.json:json:20240303")
}
