# Graine Demo — Kotlin Multiplatform

One Compose Multiplatform app, on Android (8.0+) and iOS (16+), that shows every in-app feature of the
Graine SDK from shared code: the assistant in a voice bar, the chat window with components, phone-style
calls (FCM on Android, VoIP pushes and CallKit on iOS), identity and memory, calls the user books, screen
context with actions, and a second agent on the Help tab. Every screen, rule and SDK call is in
`composeApp/src/commonMain`; the platforms add only what differs (keys, storage, the push service, the
notification permission). Every domain word comes from a use-case pack in `../usecases/` (care coach by
default, or banking or shopping — switch on the sign-in screen). All people and records are fictional.

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
   Package Manager (`https://github.com/GraineAI/inapp-ios`), which the shared code calls.

The packs are bundled from `../usecases/*.json` at build time (task `generatePacks`, into the shared code,
so both platforms read the same files); edit them there.

**Before the SDK versions are published**, build against SDK sources on your machine — in
`local.properties`:

```properties
graine.kmpSdkDir=../../sdk-kmp/sdk/kmp
```

(or `-Pgraine.kmpSdkDir=…`). Gradle then builds the Kotlin Multiplatform SDK, and the Android SDK beside it,
from that checkout in place of the published artifacts. For iOS, also run `iosApp/use-local-sdk.sh`
(GraineInApp from an iOS SDK checkout) and open the generated `iosApp/iosAppLocal.xcodeproj`.

## What is where

| | |
|---|---|
| `composeApp/src/commonMain/kotlin/ai/graine/demo/graine/GraineService.kt` | The one file that calls the SDK (identify, memory, screen context, actions, scheduling, voice, chat, calls) |
| `…/domain/` | The pack model, the Items list, call and booking presentation — no SDK rules re-implemented |
| `…/ui/AppModel.kt` | The app's state and the agent's actions, run by effect in the pack's words |
| `…/ui/*Screen.kt`, `DemoApp.kt` | Sign in · Home · Chat · Items · Scheduled calls · Memory · Profile · Help · Call activity |
| `…/platform/Platform.kt` | What differs per platform: keys, storage, back gesture, call readiness |
| `composeApp/src/androidMain/` | `DemoApplication`, `MainActivity`, `DemoMessagingService` (FCM → the SDK), notification permission |
| `composeApp/src/iosMain/` | `MainViewController` (the shared UI for SwiftUI), Info.plist keys, UserDefaults |
| `iosApp/` | The iOS app shell (XcodeGen `project.yml`; `iosApp.xcodeproj` committed) |
| `composeApp/src/commonTest/` | Tests against a fake SDK client: `./gradlew :composeApp:testDebugUnitTest` |

## Two agents in one app

Home, Chat and the Items screen talk to the main agent; the **Help** tab talks to the help desk —
`Graine.configure(config, name = "help")`, its own chat (`GraineChat(agent = "help")`), voice bar
(`GraineAgentBar(agent = "help")`) and call (`Graine.calls.startCall(reason, agent = "help")`). The user
signs in once and both agents know who they are; **Calls** lists the calls booked with either agent, and
the booking sheet asks which agent should call. Where the platform's SDK runs one agent at a time, the
Help tab says so and everything else works.

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

Sign in as the first user, tell the assistant one of the pack's `memoryExamples` in the chat, open a new
chat and ask its question — it knows. **Memory** lists what it remembers; sign out and pick another user —
it does not; switch back — it does; **Erase everything** — gone.
