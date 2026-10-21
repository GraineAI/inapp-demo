# Graine Demo — Kotlin Multiplatform

One Compose Multiplatform app, on Android (8.0+) and iOS (16+), that shows every in-app feature of the
Graine SDK from shared code: the assistant in a voice bar, the chat window with components, phone-style
calls (FCM on Android, VoIP pushes and CallKit on iOS), identity and memory, calls the user books in the
SDK's own booking sheet, screen context with actions, and a second agent on the Help tab. Every screen,
rule and SDK call is in `composeApp/src/commonMain`; the platforms add only what differs (keys, storage,
the push service, the notification permission). Every domain word comes from a use-case pack in
`../usecases/` (care coach by default, or banking or shopping — switch on the sign-in screen). All people
and records are fictional.

Home's switch, **Graine's screens / Your own design**, shows the same features twice: with the SDK's own
screens, drawn from the brand's design in Graine UI, or with every surface — chat, voice, the call screen,
booking and the agent's components — drawn by this app from the SDK's public state, plus a **Public state**
screen that lists every value live ([Your own design](#your-own-design)).

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
   Package Manager (`https://github.com/GraineAI/inapp-ios`, from 0.14.1), which the shared code calls.

The packs are bundled from `../usecases/*.json` at build time (task `generatePacks`, into the shared code,
so both platforms read the same files); edit them there.

**The published SDK, or its sources.** The app is pinned to SDK **0.14.1**: `ai.graine:inapp-kmp`,
`ai.graine:inapp-kmp-compose` and `ai.graine:inapp-android-rtc` from Maven Central, and GraineInApp and
GraineInAppRTC from Swift Package Manager (`iosApp/iosApp.xcodeproj`). With 0.14.1 published, nothing is set:
build and run as below.

Before a version is published (or to try a fix that is not released), build against the SDK's sources — the
multiplatform SDK with the Android SDK beside it (folders `kmp` and `android` side by side), and the iOS SDK with
its WebRTC module beside it (`ios` and `ios-rtc`), all of the same version:

```sh
# from the repository root — the Kotlin side, Android and iOS alike (or -Pgraine.kmpSdkDir=…, or GRAINE_KMP_SDK_DIR)
echo "graine.kmpSdkDir=/path/to/sdk/kmp" >> kmp/local.properties
# the iOS side: writes kmp/iosApp/iosAppLocal.xcodeproj (git-ignored), GraineInApp and GraineInAppRTC from the sources
GRAINE_IOS_SDK_PATH=/path/to/sdk/ios kmp/iosApp/use-local-sdk.sh     # GRAINE_IOS_RTC_SDK_PATH, if not ../ios-rtc
```

The public repositories are the same sources, tagged with each release — for example while Maven Central
catches up with a tag that is already on GitHub (iOS then takes the tag from Swift Package Manager as usual):

```sh
git clone --branch 0.14.1 https://github.com/GraineAI/inapp-kmp ../graine-sdk/kmp
git clone --branch 0.14.1 https://github.com/GraineAI/inapp-android ../graine-sdk/android
echo "graine.kmpSdkDir=../../graine-sdk/kmp" >> kmp/local.properties
```

Gradle then builds the Kotlin Multiplatform SDK, and the Android SDK beside it, in place of the published artifacts,
handing them `sdk.dir` from `local.properties` when `ANDROID_HOME` is not set; remove the line to go back to the
published artifacts. The iOS app's Gradle phase reads the same `local.properties` (or `GRAINE_KMP_SDK_DIR`).

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

With the SDK's sources (above), build `iosApp/iosAppLocal.xcodeproj` in place of `iosApp/iosApp.xcodeproj`. On an
iPhone: open the same Xcode project, choose your iPhone and **Run** (your team is in `iosApp/Config.xcconfig`).

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
| `…/ui/own/` | **Your own design**: chat, Talk, the call screen, booking, components and Public state, drawn by the app from the SDK's public state |
| `…/domain/OwnDesign.kt`, `OwnComponents.kt`, `PastSessions.kt` | How that state reads in the app's words, and what the app's component widgets answer (the SDK views' answers) |
| `…/graine/PastSessionsSdk.kt` | Past sessions read from the SDK (`chat.pastSessions`, `sessionTranscript`) into the app's own state |
| `composeApp/src/androidMain/…/OwnCallActivity.kt` | The own call screen on Android: the screen the SDK opens for every call while the app draws its own |
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
(`ai.graine:inapp-kmp` 0.14.1 on Android and iOS alike), and **Call activity** names the agent of every
ring and outcome.

## Your own design

Home's switch — **Graine's screens** or **Your own design**, kept on the device — changes who draws the agent's
surfaces. Graine's screens (the default) are the SDK's own chat, voice bar, call screen and booking sheet, in the
brand's design from Graine UI. **Your own design** draws every one of them in this app, with its own Compose
widgets, from the SDK's public state alone — what an app with a design system of its own does; no SDK screen is
used. A chat session closes 15 seconds after its screen closes: the demo sets `GraineConfig(chatCloseDelaySeconds = 15)`. Everything else stays as it is: the same buttons on Home and Help (Talk, Chat, Call, booking), the same
agents, actions, memory and screen context. Each screen is one file in `composeApp/src/commonMain/…/ui/own/`:

| Screen | Drawn from | What it does |
|---|---|---|
| Chat — `OwnChatScreen.kt` | `chat.turns`, laid out by `GraineChatRows.build(turns, newConversationBefore)`: day lines, one row per earlier call, "Disconnected · new conversation"; `status` (Disconnected included) and `typing` in the header; `quickReplies`; `ended` and `rating`; `upcomingCalls`; the agent's component | `send`, `sendQuickReply`, `rate`, `retry`, `newConversation`, `cancelUpcomingCall`; `shown(true)` and `shown(false)` as the screen appears and goes, so the conversation lives as the SDK's own window's does |
| Talk — `OwnTalkPanel.kt` | `voice.state`: `status` (and `error` when it failed), `captions`, `agentLevel` and `micLevel`, `transport` and its fallback reason | Opens ready; `start` only on its Start (the microphone with it), then `end`, `setMuted`, `setCaptionsOn` |
| Call screen — `OwnCallScreen.kt` | `calls.state`: where the call is, `agent`, `reason`, the length from `startedAt`, `agentLevel`, the caption, `muted` and `speakerOn`; a **Connection** card — `state.connection` while the call goes, the outcome's `connection` once it is over | `answer`, `decline`, `end`, `setMuted`, `setSpeaker`, `callBack`, `dismissMissedCall` |
| Booking — `OwnBookingScreen.kt` | `bookingAvailable`, and `bookingUnavailableReason` in plain words when it is off; `schedulingRules`; each day's `bookingSlots(day)`, by hour; the user's calls with that agent | `scheduleCall(at, reason)` — a refusal shows the SDK's sentence — and `cancelScheduledCall(id)` |
| Components — `OwnComponentCard.kt` | `component` (`kind`, `data`, `fallbackText`) and `componentPending`, in the chat and in Talk: chips, rows (one, or several at once), time slots, forms, else the component's own sentence | `chat.answerWidget(id, data, summary)`, with the answers the SDK's own views send, and `chat.dismissWidget(id)` |
| Past sessions — `PastSessionsScreen.kt` (from the chat's menu) | `chat.pastSessions`: the signed-in user's closed sessions with the agent, newest first — date and time, chat or call, length, how it ended, messages, the first words, the session id with Copy — and its `loading`, `hasMore` and `error`; a tap opens `chat.sessionTranscript(id)`, read only | `refreshPastSessions()` on open, `loadMorePastSessions()` at the end of the list; refusals in plain words ("Sign in to see past sessions", "Not available on this device") |
| Public state — `PublicStateScreen.kt` | Every value above, live, by surface — who, chat, voice, calls and the connection report, booking, components, events — for walking a team through what their own screens can read | Shows no message, caption or memory text and no variable values: roles, counts, lengths and names |

The call screen on Android is a screen of its own: the SDK opens one for every call, ring and missed call, and with
**Your own design** it opens the app's — `GraineCallsConfig(androidCallActivity = "ai.graine.demo.OwnCallActivity")`,
an activity declared in the app's manifest that draws the same shared composable iOS draws in place, over the lock
screen too, and forwards its permission answers to `Graine.onRequestPermissionsResult`. Switching back to Graine's
screens configures calls without it, and the SDK's own screen is back. Where a platform does not report what carries the voice conversation, Talk says "not reported".

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
