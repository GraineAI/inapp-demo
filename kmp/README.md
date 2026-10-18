# Graine Demo — Kotlin Multiplatform

One Compose Multiplatform app, on Android (8.0+) and iOS (16+), that shows every in-app feature of the
Graine SDK from shared code: the assistant in a voice bar, the chat window with components, phone-style
calls (FCM on Android, VoIP pushes and CallKit on iOS), identity and memory, calls the user books in the
SDK's own booking sheet, screen context with actions, and a second agent on the Help tab. Every screen,
rule and SDK call is in `composeApp/src/commonMain`; the platforms add only what differs (keys, storage,
the push service, the notification permission). Every domain word comes from a use-case pack in
`../usecases/` (care coach by default, or banking or shopping — switch on the sign-in screen). All people
and records are fictional.

It uses `ai.graine:inapp-kmp` (the API) and `ai.graine:inapp-kmp-compose` (the SDK's own chat, call screen
and voice bar as composables).

## Set up

1. Copy `local.properties.example` to `local.properties` (git-ignored) and set
   `graine.publishableKey` — the agent's publishable key (Agent → Embed & Widgets; add `app://` to the
   agent's **Allowed domains**) — and, optionally, `graine.helpPublishableKey` (a second agent for the
   **Help** tab), `graine.applicationId` and `graine.baseUrl`.
2. iOS: copy `iosApp/Config.example.xcconfig` to `iosApp/Config.xcconfig` (git-ignored) and set your team,
   bundle id and the same keys.
3. Recreate the agent from `../agent-setup/<pack>/` so its identity field, actions and call settings
   match the pack you demo.
4. Android: `./gradlew :composeApp:installDebug` (or run `composeApp` in Android Studio).
   iOS: open `iosApp/iosApp.xcodeproj` and run the `GraineDemoKMP` scheme. Its first build phase builds
   the shared code with Gradle (`embedAndSignAppleFrameworkForXcode`); Graine's iOS SDK comes from Swift
   Package Manager (`https://github.com/GraineAI/inapp-ios`, from 0.10.3), which the shared code calls.

The packs are bundled from `../usecases/*.json` at build time (task `generatePacks`, into the shared code,
so both platforms read the same files); edit them there.

**Optional: build against the SDK's sources** instead of the published artifacts (for example a fix that is not
released yet) — the public repositories, tag `0.10.3`, side by side: the multiplatform SDK builds its Android
side from the Android SDK in the folder `android` beside it, and the iOS app can take GraineInApp from a
checkout too:

```sh
# from the repository root
git clone --branch 0.10.3 https://github.com/GraineAI/inapp-kmp ../graine-sdk/kmp
git clone --branch 0.10.3 https://github.com/GraineAI/inapp-android ../graine-sdk/android
git clone --branch 0.10.3 https://github.com/GraineAI/inapp-ios ../graine-sdk/ios
echo "graine.kmpSdkDir=../../graine-sdk/kmp" >> kmp/local.properties
```

(or `-Pgraine.kmpSdkDir=…`, or `GRAINE_KMP_SDK_DIR`). Gradle then builds the Kotlin Multiplatform SDK, and
the Android SDK beside it, in place of the published artifacts, handing them `sdk.dir` from `local.properties`
when `ANDROID_HOME` is not set; remove the line to go back to the published artifacts. The iOS app's Gradle phase reads the
same `local.properties`; `iosApp/use-local-sdk.sh` generates `iosApp/iosAppLocal.xcodeproj`, the same app with
GraineInApp from `../graine-sdk/ios` (`GRAINE_IOS_SDK_PATH` names another checkout).

**Run** — on an Android emulator or phone, and on an iOS simulator (the commands
[RUN-THE-DEMO.md](../RUN-THE-DEMO.md#4-kotlin-multiplatform-on-android-and-the-ios-simulator) runs):

```sh
cd kmp
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Android: an emulator running (Android Studio → Device Manager), or a phone with USB debugging on
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:installDebug
adb shell am start -n com.example.grainedemo.kmp/ai.graine.demo.MainActivity   # your graine.applicationId

# iOS simulator: the build phase runs Gradle, so JDK 17 or newer must be the java Xcode finds
SIM='iPhone 17'                    # any iOS 16+ simulator: xcrun simctl list devices available
xcodebuild -project iosApp/iosApp.xcodeproj -scheme GraineDemoKMP \
  -destination "platform=iOS Simulator,name=$SIM" -derivedDataPath build/ios CODE_SIGNING_ALLOWED=NO build
xcrun simctl boot "$SIM"; open -a Simulator
xcrun simctl install "$SIM" build/ios/Build/Products/Debug-iphonesimulator/GraineDemoKMP.app
xcrun simctl launch "$SIM" com.example.grainedemo.kmp    # your PRODUCT_BUNDLE_IDENTIFIER
```

On an iPhone: open the same Xcode project, choose your iPhone and **Run** (your team is in
`iosApp/Config.xcconfig`).

## What is where

| | |
|---|---|
| `composeApp/src/commonMain/kotlin/ai/graine/demo/graine/GraineService.kt` | The one file that calls the SDK (identify, memory, screen context, actions, the booking sheet and scheduled calls, voice, chat, calls) |
| `…/domain/` | The pack model, the Items list, how calls read — no SDK rules re-implemented |
| `…/ui/AppModel.kt` | The app's state and the agent's actions, run by effect in the pack's words |
| `…/ui/*Screen.kt`, `DemoApp.kt` | Sign in · Home · Chat · Items · Scheduled calls · Memory · Profile · Help · Call activity |
| `…/platform/Platform.kt` | What differs per platform: keys, storage, back gesture, call readiness |
| `composeApp/src/androidMain/` | `DemoApplication`, `MainActivity`, notification permission (pushes: the SDK's own messaging service) |
| `composeApp/src/iosMain/` | `MainViewController` (the shared UI for SwiftUI), Info.plist keys, UserDefaults |
| `iosApp/` | The iOS app shell (XcodeGen `project.yml`; `iosApp.xcodeproj` committed) |
| `composeApp/src/commonTest/` | Tests against a fake SDK client: `./gradlew :composeApp:testDebugUnitTest` |

## Two agents in one app

Home, Chat and the Items screen talk to the main agent; the **Help** tab talks to the help desk —
`Graine.configure(config, name = "help")`, its own chat (`GraineChat(agent = "help")`), voice bar
(`GraineAgentBar(agent = "help")`) and call (`Graine.calls.startCall(reason, agent = "help")`). The user
signs in once and both agents know who they are; **Calls** lists the calls booked with either agent.
Each agent books in the SDK's own booking sheet (`presentBooking(reason)`): the pack's `bookLabel` on
**Calls** opens the main agent's, the one on **Help** the help desk's, each with the pack's first reason
filled in. A button is on only while the SDK's `bookingAvailable` is true for its agent (Graine rings this
phone, the agent lets users book, and the user is identified with the agent's Identity field); otherwise it
is off, with one line: "Booking needs, in Graine UI: users may book (Calls & scheduling), this app's push keys (Workspace settings → In-app calling), and Who is the user? set to this pack's ID". Both platforms run both agents
(`ai.graine:inapp-kmp` 0.10.3 on Android and iOS alike), and **Call activity** names the agent of every
ring and outcome.

## Calls on a real device

1. Under **Workspace settings → In-app calling**, add the apps: the Android application id with the
   Firebase service-account JSON, and the iOS bundle id with your APNs key. Graine then registers each
   device by itself when the app starts.
2. Android: put your Firebase `google-services.json` in `composeApp/` (git-ignored). iOS: the app has the
   Push Notifications capability and the Audio and Voice over IP background modes already
   (`iosApp/project.yml`); sign it with your team.
3. Book a call on **Calls**, lock the phone, and it rings at that time — the SDK's call screen, in the
   brand's design.

## Memory, in a few minutes

Sign in as the first user, tell the assistant one of the pack's `memoryExamples` in the chat and close the
app. Open it again — the chat continues where it was — tap **New conversation** in the chat's header and ask
its question — it knows. **Memory** lists what it remembers; sign out and pick another user —
it does not; switch back — it does; **Erase everything** — gone.
