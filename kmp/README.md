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
   **Help** tab), `graine.consultationPublishableKey` (the Journey's first-call agent:
   [The Journey, step by step](#the-journey-step-by-step)), `graine.applicationId` and `graine.baseUrl`.
2. iOS: copy `iosApp/Config.example.xcconfig` to `iosApp/Config.xcconfig` (git-ignored) and set your team,
   bundle id and the same keys.
3. Recreate the agent from `../agent-setup/<pack>/` so its identity field, memory, actions and call
   settings match the pack you demo ([Memory, in a few minutes](#memory-in-a-few-minutes)).
4. Android: `./gradlew :composeApp:installDebug` (or run `composeApp` in Android Studio).
   iOS: open `iosApp/iosApp.xcodeproj` and run the `GraineDemoKMP` scheme. Its first build phase builds
   the shared code with Gradle (`embedAndSignAppleFrameworkForXcode`); Graine's iOS SDK comes from Swift
   Package Manager (`https://github.com/GraineAI/inapp-ios`, from 0.14.6), which the shared code calls.

The packs are bundled from `../usecases/*.json` at build time (task `generatePacks`, into the shared code,
so both platforms read the same files); edit them there.

**The published SDK, or its sources.** The app is pinned to SDK **0.14.6** for Kotlin Multiplatform and iOS:
`ai.graine:inapp-kmp` and `ai.graine:inapp-kmp-compose` 0.14.6 from Maven Central (on Android they build on the Android
SDK 0.14.6, with `ai.graine:inapp-android-rtc` 0.14.6 for WebRTC calls), and GraineInApp and GraineInAppRTC 0.14.6 from
Swift Package Manager (`iosApp/iosApp.xcodeproj`). They are published, so nothing is set: build and run as below.

**Your own app.** The multiplatform library calls Graine's iOS SDK and does not carry it, so an iOS app built
with it links GraineInApp itself, at the same version: add `https://github.com/GraineAI/inapp-ios` (and
`https://github.com/GraineAI/inapp-ios-rtc` for WebRTC calls) with Swift Package Manager to the **app target**, or
`pod 'GraineInApp', :git => 'https://github.com/GraineAI/inapp-ios.git', :tag => '0.14.6'` to its Podfile, and
build the shared framework static (`isStatic = true`, as `composeApp/build.gradle.kts` does). A link error naming
`_OBJC_CLASS_$_GraineObjC` means the app target does not link GraineInApp: add it to the target (General →
Frameworks, Libraries, and Embedded Content), clean the build folder and build again.

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
git clone --branch 0.14.6 https://github.com/GraineAI/inapp-kmp ../graine-sdk/kmp
git clone --branch 0.14.6 https://github.com/GraineAI/inapp-android ../graine-sdk/android   # the Android SDK KMP 0.14.6 builds on
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
| `composeApp/src/commonMain/kotlin/ai/graine/demo/security/GraineSecurity.kt` | **Security — copy this file.** Every line of it: the agents configured (with server tokens), the app proven at launch, who the user is with their identity token at sign-in and every launch, sign-out, the debug token, refusals ([Security — how this app is set up](#security--how-this-app-is-set-up)) |
| `…/security/SecurityText.kt` | A refusal in plain words for the user and a line for the log, read from the SDK's code; the Security card's lines |
| `…/ui/SecurityCard.kt` | The Security card (Profile; the Journey's Account → Testing tools): every agent's App, User and Server token lines, the last refusal, the debug token |
| `…/domain/DemoSignIn.kt`, `…/domain/ServerToken.kt` | **Demo only — never copy.** Stand-ins for your sign-in (an ID token) and your server (server tokens) |
| `composeApp/src/commonMain/kotlin/ai/graine/demo/graine/GraineService.kt` | The one file that calls the SDK for everything else (memory, screen context, actions, the booking sheet and scheduled calls, voice, chat, calls) — through `GraineSecurity` for who the user is |
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
| `…/journey/JourneySdk.kt` | **The Journey**: every SDK call of the journey, step by step, in one file ([The Journey, step by step](#the-journey-step-by-step)) |
| `…/journey/JourneyModel.kt` | The journey's state machine: onboarding, the first call and every way it can fail, the plan through the app's actions, check-ins, privacy |
| `…/journey/Plan.kt`, `JourneyStore.kt`, `JourneyRules.kt`, `Args.kt` | The plan and how an action's arguments are read; the app as the store (JSON files, one writer); the picker, reminders and figures |
| `…/ui/journey/` | The journey's screens, drawn by the app |
| `composeApp/src/androidMain/…/journey/`, `iosMain/…/journey/` | Check-in reminders (alarms and notifications; local notifications), files, the share sheet |
| `composeApp/src/commonTest/` | Tests against a fake SDK client: `./gradlew :composeApp:testDebugUnitTest` |

## Two agents in one app

Home, Chat and the Items screen talk to the main agent; the **Help** tab talks to the help desk —
`Graine.configure(config, name = "help")`, its own chat (`GraineChat(agent = "help")`), voice bar
(`GraineAgentBar(agent = "help")`) and call (`Graine.calls.startCall(reason, agent = "help")`). The user
signs in once and both agents know who they are; **Calls** lists the calls booked with either agent.
Each agent books in the SDK's own booking sheet (`presentBooking(reason)`): the pack's `bookLabel` on
**Calls** opens the main agent's, the one on **Help** the help desk's, each with the pack's first reason
filled in. Both platforms run both agents (`ai.graine:inapp-kmp` 0.14.6 on Android and iOS alike), and
**Call activity** names the agent of every ring and outcome.

**Each agent is locked on its own.** The help desk's publishable key ships in the app as the main agent's does, so it
needs its own **Your app and your users** (apps, proofs, switches, debug tokens) and its own server tokens — nothing is
shared between agents. **Profile → Security** lists both, and the **Help** screen says so when the help desk checks
neither the app nor its users ([Security — how this app is set up](#security--how-this-app-is-set-up)).

**Booking buttons follow `bookingState`.** Whether an agent can be booked from the app — Graine rings this phone,
the agent lets users book, and the user is identified with the agent's **Who is the user?** field — is the SDK's
`bookingState` (0.14.4): `Graine.bookingState` for the default agent, `Graine.agent("help")?.bookingState` for
another. The SDK reads it again when the agent's session arrives, after `identify` and `setMemory`, and when the
session could not load; the app collects it from launch and never asks again (`AppModel.collectBooking`). Do not
read `bookingAvailable` once at launch: until the session has arrived it is never available — for a signed-in user
the reason is `not_loaded`, "not yet", never "no" (with nobody signed in, the settings the last launch kept may
already say why not) — and a button drawn from that read stays off.

```kotlin
// Every booking button, hint and time list, from the agent's bookingState as it changes.
scope.launch {
    Graine.bookingState.collect { state ->
        bookButton.enabled = state.available
        hint = if (state.available) null else words(state.reason)   // not_loaded, rings_not_set_up, scheduling_off, not_identified
    }
}
```

While a button is off, the line under it says why, in plain words from `state.reason` (`OwnText.bookingUnavailable`):
"Loading this agent's settings…", "Graine cannot ring this phone yet…", "This agent does not let users book
calls…", "The signed-in user is not identified with the agent's "Who is the user?" field."

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
| Booking — `OwnBookingScreen.kt` | the agent's `bookingState` (collected; its `reason` in plain words when it is off, and the times read again when it changes); `schedulingRules`; each day's `bookingSlots(day)`, by hour; the user's calls with that agent | `scheduleCall(at, reason)` — a refusal shows the SDK's sentence — and `cancelScheduledCall(id)` |
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

## Security — how this app is set up

This app is the reference for in-app security with Graine: **every line of it is in one file you can copy,
[`security/GraineSecurity.kt`](composeApp/src/commonMain/kotlin/ai/graine/demo/security/GraineSecurity.kt)**, whose
comments map each line to the dashboard step it satisfies. Graine sets security up **per agent**, under Agent →
**Embed & Widgets** → **Install** → **Your app and your users** (six steps) and **Server tokens**. Each protection is off
until you turn it on, and counts what it would refuse before it refuses anything. The guides:
[Secure your agent](https://www.graine.ai/docs/in-app/secure-your-agent) ·
[Server tokens](https://www.graine.ai/docs/in-app/security/server-tokens) ·
[Rotating the publishable key](https://www.graine.ai/docs/in-app/security/keys).

**What you copy**

| File | |
|---|---|
| `composeApp/src/commonMain/kotlin/ai/graine/demo/security/GraineSecurity.kt` | All the wiring. Supply the two things only you have: your sign-in's ID token (`identityTokens`) and, with server tokens, your server's token for each agent (`tokenProvider`) |
| `…/security/SecurityText.kt` | Optional: a refusal in plain words for the user and a line for your log, read from the SDK's code; the Security card's lines |
| `…/ui/SecurityCard.kt` | Optional: the card your testers read |

Never copy `…/domain/DemoSignIn.kt` or `…/domain/ServerToken.kt`: they are **demo-only stand-ins** for your sign-in and
your server, and say so at the top.

### Dashboard step → where it is in this app

| Dashboard step | What the app needs | Where |
|---|---|---|
| **1 Your apps** | Nothing in code: the SDK says which app it is on every request. List each build: iPhone bundle ID and Team ID; Android package and the SHA-256 of **every** certificate that signs it | `iosApp/Config.xcconfig` (`PRODUCT_BUNDLE_IDENTIFIER`, `DEVELOPMENT_TEAM`); `local.properties` (`graine.applicationId`); fingerprints: [below](#android-play-integrity-and-signing-certificates) |
| **2 What we've seen** | Nothing: the dashboard counts what the SDK sends | — |
| **3 Prove it's your app** | iPhone: the App Attest capability. Android: the Play Integrity dependency. The app proven at launch, so the first conversation never waits | `iosApp/project.yml` (and `iosApp/iosApp/iosApp.entitlements`); `composeApp/build.gradle.kts`; `GraineSecurity.start` → `prepareAppVerification()` for every agent |
| **3 → Development** | A debug build sends a debug token instead of a proof; register it on each agent | `GraineSecurity.debugToken` → **Profile → Security**, with **Copy** (debug builds only) |
| **4 Prove who the user is** | Identify **with** the identity token, at sign-in **and at every launch** (the SDK holds both in memory only); calls start after it; a refresher for a fresh token; sign-out | `GraineSecurity.signIn`, from `AppModel.signIn` (the tour) and `JourneyModel.resume` (the Journey, every launch); `awaitSignIn` before a call (`AppModel`, `JourneyModel`) and every request (`guarded`); the refresher in `GraineSecurity.start`; `GraineSecurity.signOut` |
| **5 Turn it on** | Nothing new: a refusal arrives as the SDK's `GraineErrorCode` — show plain words, log the code, add no retry | `GraineSecurity.noteRefusal` and `SecurityRefusal` (`SecurityText.kt`), used by `GraineService`, `GraineJourneySdk` and `CallText.message` |
| **6 Send setup to your developer** | — | This section, and `GraineSecurity.kt` |
| **Server tokens** | Each agent's `GraineConfig.tokenProvider` asks **your** server, which mints with your secret API key | `GraineSecurity.start`, given the configs `GraineService.start` builds; the demo's stand-ins: **Profile → Server token (demo)** |

### In your app

```kotlin
// At launch, once — Application.onCreate on Android, the app's start on iOS — before anything else uses Graine.
GraineSecurity.start(
    agents = listOf(
        GraineSecurity.Agent(Graine.DEFAULT_AGENT, GraineConfig(baseUrl, publishableKey = mainKey,
            tokenProvider = { myServer.graineToken(agent = "main") })),   // server tokens: only with them on
        GraineSecurity.Agent("help", GraineConfig(baseUrl, publishableKey = helpKey,
            tokenProvider = { myServer.graineToken(agent = "help") })),   // one token per agent
    ),
    identityTokens = { userId -> mySignIn.currentIdToken() },          // your sign-in's ID token (a JWT), or null
    debugBuild = isDebugBuild,
)

// At sign-in — and at every launch while someone is signed in. The id goes under the field your agents'
// "Who is the user?" names (identityField: "patient_id" in this demo's care pack) and under user_id, the default.
// Identify WITH the token: pass the one you already hold. Without it, it is fetched before identify (10 s at most).
GraineSecurity.signIn(user.id, mapOf(identityField to user.id, "user_id" to user.id, "name" to user.firstName),
    identityToken = idTokenFromYourSignIn)

// A call or the voice bar right after: once the user is identified with their token (at once when nothing waits).
scope.launch { GraineSecurity.awaitSignIn(); Graine.calls.startCall(reason = "help") }

// Sign-out.
GraineSecurity.signOut()

// A request refused: plain words for the user; the code, the reason and the fix in your log.
try { agent.memories() } catch (e: GraineException) {
    showError(GraineSecurity.noteRefusal("default", e)?.message ?: e.message)
}
```

What it does with the SDK, should you write it yourself:

```kotlin
Graine.identityTokenRefresher = { mySignIn.currentIdToken() }        // before configure: one for the whole app
Graine.configure(GraineConfig(baseUrl, mainKey, tokenProvider = { myServer.graineToken("main") }))
Graine.configure(GraineConfig(baseUrl, helpKey, tokenProvider = { myServer.graineToken("help") }), name = "help")
Graine.agents.values.forEach { agent -> scope.launch { agent.prepareAppVerification() } }   // never throws
Graine.identify(traits, identityToken = mySignIn.currentIdToken())   // sign-in, every launch: the token BEFORE identify
Graine.identify(null)                                                 // sign-out: the token goes with the identity
Graine.agent("help")?.verification?.collect { v -> log("help: app ${v.app.word}, user ${v.user.word}") }
```

`Graine.debugTokenMode` stays `AUTOMATIC`: a debug build sends its debug token; a release build never makes, logs or
sends one. Never set `ALWAYS` in a build that reaches users.

### iPhone: App Attest

The app target has the **App Attest** capability: `com.apple.developer.devicecheck.appattest-environment` in
`iosApp/project.yml` and `iosApp/iosApp/iosApp.entitlements`. In your app: Xcode → the app target → **Signing &
Capabilities** → **+ Capability** → **App Attest**. Nothing to upload. Builds Xcode installs use App Attest's development
environment, accepted only while the agent's **Allow development builds** is on (turn it off before you release);
TestFlight and App Store builds use production. The Simulator cannot attest: it sends its debug token.

### Android: Play Integrity and signing certificates

The app depends on `com.google.android.play:integrity:1.4.0` (`composeApp/build.gradle.kts`, `androidMain`); the SDK
only compiles against it. In Play Console → the app → **Test and release → App integrity → Play Integrity API**, link a
Google Cloud project; enable the Play Integrity API in that project; create a service account with a JSON key and upload
it under **Prove it's your app** (checked when saved, stored sealed, never shown again).

Under **Your apps**, list the SHA-256 of every certificate that signs a build you run:

- **Debug builds** — each computer has its own debug key: `./gradlew :composeApp:signingReport` (under *Variant: debug*,
  the line *SHA-256*). Add each developer's, or rely on debug tokens.
- **Google Play** — Play Console → **Test and release → App integrity**: the **App signing key certificate** (what users'
  phones see, and the one most often missing after a Play release) and the **Upload key certificate**.

### Debug tokens

Simulators, emulators and debug builds cannot pass App Attest or Play Integrity. A debug build — on Android a
debuggable one, on iPhone one built with the Debug configuration or running on the Simulator — sends a **debug token**,
made once per install and logged once: `Graine debug token for this install: gdt_…`. **Profile → Security** shows it
with **Copy** (debug builds only: `GraineSecurity.debugToken`). Register it on **each** agent: **Prove it's your app →
Development → Debug tokens** (30 days unless you choose otherwise, 90 at most; revoke it there when the device is done).
A reinstall makes a new one. **Test users** — user ids accepted without an identity token, only from an install whose
debug token is registered — are on the same page.

### Which identity method, and why

This app proves its users with **your sign-in's ID token (JWT)** — the dashboard's recommended option, and the one that
needs no server of yours:

- **Graine holds no secret for it, and neither does the app.** Graine checks the token against the public keys your
  sign-in provider publishes (its **JWKS URL**), with its **issuer**, its **audience** and the **claim** that holds the
  user id.
- **One token for every agent.** The SDK holds one identity token for the whole app, and every agent checks it the same
  way. A signed user ID (HMAC) is made with one agent's secret, so it proves the user to that agent only — with several
  agents, use the ID token. Each agent still needs its own **Prove who the user is** settings: proving a user to one
  agent does not count for another.
- **The claim must hold the id the app identifies the user with** — the value of the agent's **Who is the user?** field
  (`patient_id` here). If your sign-in's user id is something else, put the id in a claim of its own and name that claim.

**Pass the token you already hold; if you must fetch it, fetch it before identify, and set the refresher.** The SDK
holds no token across launches. `GraineSecurity.signIn` identifies **with** the token: the one you pass at once;
otherwise it fetches the token first and waits for it, at most 10 seconds (`UserIdentity.TOKEN_WAIT_MS`, as long as the
SDK itself waits), so no request names the user without proving them; then it identifies without it, keeps fetching and hands it over with
`Graine.setIdentityToken` when it comes. A conversation the user starts in between is not answered anonymously: since
SDK 0.14.6, before a chat, the voice bar, a call or a request for the user (memories, past sessions, booked calls), the
SDK asks the refresher for a token it does not hold and waits for it, at most 10 seconds — the refresher joins the fetch
already on its way — so the app needs no wait of its own for the token. (On 0.14.5 and older, a conversation started
before the token arrived went out unproven: update.) Meanwhile nobody is identified — never the last user — calls and the voice bar
wait for it (`GraineSecurity.awaitSignIn`, at once when nothing waits), every request does too, a call that rings
identifies the user at once (it knows who it rings; the token follows), and an open chat is reconnected as the user by
the SDK when the identity arrives. A token fetched for one user is never handed over for the next, and sign-out cancels
everything on its way. The SDK then asks the refresher by itself when a token expires, when a request is refused
`identity_unverified` (then sends it once more), before a conversation whose JWT has less than a minute left, and when a
call rings — and the refresher joins the fetch already on its way.

The id is sent under the pack's field (`patient_id`) **and** under `user_id`, so the user is identified whichever field
**Who is the user?** names, and `GraineSecurity` logs a warning when an agent names a field the app does not send.

**This demo has no sign-in**: you pick a fictional person, and the ID token comes from Graine's **demo sign-in server**,
which Graine enables for its own demo workspace only. It signs for whatever user id it is given, so it is a stand-in,
never a proof (`domain/DemoSignIn.kt`). Your app proves its users with **its own** sign-in, whose tokens are signed only
for the person who signed in — and, for sensitive data such as health records, with **server tokens** minted by your own
server as well.

### Server tokens

The strongest mode: once an agent requires them, the publishable key alone opens nothing. Your server — which knows who
signed in — mints a short-lived token for **one agent and one user** with your **secret** API key, and that agent's
`GraineConfig.tokenProvider` fetches it. The SDK asks at start, shortly before each token expires, and when Graine
refuses one; a token that expires never ends a chat or call in progress.

```sh
curl -s https://api.graine.ai/v2/client-tokens -H "Authorization: Bearer $GRAINE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"agent_id":"<agent id>","user_id":"<the id the app identifies the user with>","scopes":["chat","call","memory","booking"],"ttl_seconds":3600}'
```

This demo has no server and never carries a secret, so **Profile → Server token (demo)** offers stand-ins — demo only,
**Off** by default, applied the next time the app starts (`domain/ServerToken.kt`):

- **Graine demo server** — Graine's demo token endpoint, enabled for Graine's demo agents only: it mints for whoever
  signs in, for **every** agent of the app, each with its own key. Any other agent's key is refused.
- **Local minter** — `GRAINE_API_KEY=… GRAINE_AGENT_ID=<agent id> node ../agent-setup/token-minter.mjs` on your own
  computer, with **your** key, for the main agent. The Android emulator reaches it at `http://10.0.2.2:8787`, the iOS
  simulator at `http://localhost:8787` (a debug build allows plain HTTP to those two addresses only). It mints for any
  user id it is asked for — your real server mints only for the user its sign-in proved.
- **Paste a token** — one you minted with the curl above, for the main agent (it lasts at most an hour).

### Every agent whose key ships in the app

Each agent — here the main agent, the help desk and the Journey's first-call agent — has its own **Your app and your
users** and **Server tokens**: list the apps, add the proofs, register the debug tokens and turn the switches on for
**each one**. A key nobody checks is the one anyone would use. **Profile → Security** lists every configured agent, and
the **Help** screen says so when the help desk checks neither the app nor its users.

### Roll it out: report-only, then on

1. Ship the SDK (0.14.4 or later; 0.14.6 for the wait below; this app is on 0.14.6) with App Attest, Play Integrity and `GraineSecurity`.
2. For each agent: **Your apps**, then **Check app proofs (report only)**, **Prove who the user is** (report only) and
   **Server tokens → Report only**.
3. Watch **What we've seen** for a few days, until *Not in your list*, *Not verified* and missing tokens are only traffic
   you do not recognise. **Profile → Security** shows what this build sends.
4. Turn on, one at a time and per agent: **Only my apps** → **Require a verified app** → **Require verified users** →
   **Server tokens → Enforce**. Each asks you to confirm, with what it would have refused. Turning one off takes effect
   within 30 seconds.

### Reading refusals

The SDK has done its own single retry before it reports any of these — it proves the app again, asks the identity token
refresher, or asks the `tokenProvider`, then sends the request once more — and never retries the final ones. So the app
adds **no retry of its own**: it shows the words below and logs the code with its fix (`GraineSecurity.noteRefusal`).
The Journey does not reconnect its chat by itself after a final refusal.

| Code (`GraineErrorCode`) | When | The user sees (this app) | Fix | Retried |
|---|---|---|---|---|
| `app_not_allowed` (`AppNotAllowed`) | *Only my apps* is on and this build is not listed | "This version of the app can't connect to the assistant. Please update the app." | List the build under **Your apps**, with every certificate | Never |
| `app_not_verified` (`AppNotVerified`) | *Require a verified app* is on and this install has no valid proof | "We couldn't confirm this copy of the app. Please try again later, or update the app." | App Attest and Play Integrity set up; a debug build's token registered | Once, by the SDK |
| `identity_unverified` (`IdentityUnverified`) | *Require verified users* is on and the user is not proven: memory, booking and past conversations refuse (chats and calls are answered anonymously) | "Please sign in again to continue." | The identity token: its issuer, audience, claim and expiry | Once, by the SDK |
| `token_required` (`TokenRequired`) | Server tokens are enforced and none was sent | "The assistant isn't available right now. Please try again later." | Your server mints one for the signed-in user | Once, by the SDK |
| `token_invalid` (`TokenInvalid`) | The token could not be checked (`reason`) | The same | Mint it with `POST /v2/client-tokens`; pass it as it came | Once, by the SDK |
| `token_expired` (`TokenExpired`) | A fresh token did not come in time | The same | Your server answers within a few seconds | Once, by the SDK |
| `token_scope` (`TokenScope`) | Another agent's or user's token, or a scope missing (`reason`) | "This isn't available in the app right now." | Mint it for this agent and user, with every scope the app uses | Never |
| `key_revoked` (`KeyRevoked`) | The publishable key was revoked | "This version of the app can't connect to the assistant any more. Please update the app." | Ship the agent's new key ([Rotating the publishable key](#rotating-the-publishable-key)) | Never |

Where they arrive: a suspending call (memories, booking, past sessions) throws `GraineException` with the `code`; a call
that could not start ends `FAILED` with the code first in its reason (`"key_revoked: …"`); a chat that cannot connect
says **FAILED** and its sentence arrives as `GraineEvent.Error`, which **Profile → Security** shows as *Last error* beside
the agent's verdicts.

### What a tester checks

**Profile → Security** (in the Journey: **Account → Testing tools**), for every configured agent:

| Line | What it says |
|---|---|
| *Name · configured name · agent id* | Which agent; *Who is the user?* names its identity field |
| **Settings** | *not loaded yet* until the agent's settings arrive — still so with a wrong key or no network, and the last error says why |
| **App** | *not checked* (the agent does not check proofs) · *checking…* · *verified* (App Attest or Play Integrity; a registered debug token; a development build) · *not verified* and why (*debug token not registered*, *proof expired*…) |
| **User** | *nobody signed in* · *not checked* · *verified* (identity token; a test user) · *not proven* and why (*no identity token*, *identity token expired*…) — answered anonymously while **Require verified users** is on |
| **Server token** | *off* (no `tokenProvider` for this agent) · *not asked yet* · *ok* · *missing* · *refused*, with the code |
| **Last refusal**, **Last error** | The last security refusal its requests met, with the fix; the last error the SDK reported with no code |
| **Debug token** | Debug builds only: this install's, with **Copy** |

## Rotating the publishable key

Agent → Embed & Widgets → Install → **Keys**:

1. **Create a new key.** The current key keeps working beside it — an agent has at most two working keys.
2. Put the new key in `local.properties` (`graine.publishableKey`) and the iOS `Config.xcconfig`, rebuild, and
   check the app works. **Last used** on each key shows which one your builds still send (it updates about every
   ten minutes).
3. **Revoke** the old key — **Report only** first: it keeps working and every use is counted, so you can see
   whether an old build still uses it.
4. When nothing uses it any more, **Refuse it now**: apps still on it get `key_revoked`
   (`GraineErrorCode.KeyRevoked`) and say so.

If a key is somewhere it should not be, **Replace now** makes a new key and refuses every other one at once.

## Memory, in a few minutes

**How it works.** At sign-in the app identifies the user by the pack's field (`patient_id`, `customer_id` or
`shopper_id`) with their memory choice — `Graine.identify(traits, memory, identityToken)`, through `GraineSecurity.signIn`
in `security/GraineSecurity.kt` — before any chat,
Talk or call opens. The same id also goes under `user_id`, so the user is recognised whether the agent's
**Who is the user?** is the pack's field or **User ID** (the dashboard's usual choice).
A phone call to or from the agent is recognised by the caller's number instead: the app user and the caller are one
person only when **Who is the user?** is Phone number and the app identifies the user by that number (with the
pack's field, they are two). At the start of each chat, Talk or call, what the agent remembers about the user goes into what the model reads,
before its first reply: after the agent's prompt (for a flow agent, into the flow's agent information). During a
live chat or call, when the user refers to an earlier conversation ("last time", "I told you", "pichli baar"…), the
agent also looks its memory up before answering (shipping with the next platform update). What is said is saved
every few messages and when the conversation ends, and is ready about a minute later — never contact details, ID or
card numbers, passwords or codes, dates of birth, or health conditions, medicines and diagnoses, whatever the
agent's memory rules say. How the agent uses what it remembers is up to its prompt: the Memory section of
`../agent-setup/<pack>/prompt.md`.

**Try it**, with the agent set up from `../agent-setup/<pack>/settings.md` (Memory):

1. Sign in as the first user, memory on. In **Chat**, tell the assistant the pack's two `memoryExamples` — for
   Graine Care, "My goal is to walk 30 minutes every evening, after dinner." and "Please call me for check-ins
   after 7 pm." Close the chat and wait about a minute.
2. Open **Chat** — it continues where it was — tap **New conversation** in its header and ask "What do you
   remember about me?": it answers with both. **Memory** lists them (pull to refresh), and so does the dashboard,
   Agent → Memory → **People**.
3. Sign out and pick another user: the assistant knows none of it. Sign in as the first again: it does.
4. **Erase everything** on **Memory**, or erase the user on the dashboard's **People** tab: in a new conversation
   it no longer knows.
5. Opt out: memory off on the sign-in screen or on **Memory** (`identify(…, memory = false)`, `setMemory(false)`):
   from the next conversation on, nothing is remembered or recalled.

## The Journey, step by step

The sign-in screen's **The full journey → Start** runs an app's whole in-app journey as a new user lives it, from
`composeApp/src/commonMain/…/journey/` (the model; **`JourneySdk.kt` holds every SDK call**) and `…/ui/journey/` (the
screens). Every word, profile field, unit, item kind and the sample plan come from the pack's `journey` block
(`../usecases/README.md`, *The journey*); the code is generic. Three agents: the **coach** is the default agent (the
main key), the **first-call** agent is configured as `consultation` (`graine.consultationPublishableKey`, a flow
agent), **support** is the help agent. Set them up from `../agent-setup/health-coach/journey/`.

**1. Onboarding** — after sign-up and at every launch, who the user is and what they said; then the permissions.

```kotlin
// Who the user is, proven by their identity token — at sign-up and at every launch (security/GraineSecurity.kt):
// Graine.identify(traits, identityToken = …), the token fetched BEFORE identify when none is in hand (10 s at most).
GraineSecurity.signIn(userId, mapOf("user_id" to userId, "name" to name))
// Each agent keeps its own variables: the journey sets them on every agent it talks to.
Graine.agents.values.forEach { agent ->
    agent.setVariables(mapOf("gender" to "female", "age" to "33", "weight_kg" to "64.5", "height_cm" to "163",
        "goal" to "Lose weight", "diet" to "vegetarian", "agent_language" to "hi", "language_style" to "Hinglish: …"))
}
Graine.calls.requestCallPermissions()   // the microphone screen (and notifications on Android 13+)
```

**2. The first call** — now, or at a time the user picks from the agent's own times.

```kotlin
GraineSecurity.awaitSignIn()             // a call takes who the user is at its start: after the sign-in's identify
Graine.calls.startCall(reason = "onboarding_consultation", agent = "consultation")   // Start now

val consultation = Graine.agent("consultation")!!
consultation.bookingState.collect { state -> /* Confirm on while state.available; state.reason in words */ }
consultation.bookingSlots(day)            // every time its rules allow that day; the picker shows 10:30, 13:00, 17:30, 20:00
consultation.scheduleCall(at, reason = "Your onboarding consultation")               // Confirm

Graine.calls.state.collect { call ->      // the app's own call screen
    // call.state: RINGING → "Your onboarding consultation", Answer / Decline; ACTIVE → the timer from call.startedAt,
    // the agent's last line (call.caption, call.captions) as the current topic
}
Graine.calls.setMuted(true); Graine.calls.setSpeaker(true); Graine.calls.end()
// Android draws it as an activity of the app's, which the SDK opens for every call; iOS over CallKit's.
Graine.calls.configure(GraineCallsConfig(androidCallActivity = "ai.graine.demo.OwnCallActivity"))
```

**3. "We couldn't connect"** — a ring missed, declined or failed, a call cut short before its save, or a booked call
that could not ring; then Call me now, Choose another time, or Continue in chat.

```kotlin
Graine.calls.outcomes.collect { o ->
    if (o.agent == "consultation") when (o.outcome) {
        GraineCallOutcome.Kind.MISSED, GraineCallOutcome.Kind.DECLINED, GraineCallOutcome.Kind.FAILED -> showCouldNotConnect()
        GraineCallOutcome.Kind.ENDED -> if (consultationSaved) buildPlan() else showCouldNotConnect()   // ended early
        else -> Unit
    }
}
Graine.calls.state.value.missedCall       // a ring nobody answered, kept until called back or dismissed
consultation.scheduledCalls()             // at launch: a booked call missed while the app was closed reads Missed
Graine.calls.callBack()                   // Call me now (no missed ring: startCall again)
consultation.chat.shown(true)             // Continue in chat: the same questions, saved the same way
consultation.component.collect { c -> /* "Select all that apply": choices or quick actions with select = many */ }
consultation.chat.answerWidget(c.id, data, summary = "Late dinners and Eating out")
```

**4. The plan** — the app is the store; the agents read and change it through actions, each answered at once
(within 6 seconds), with arguments as maps and lists.

```kotlin
consultation.registerAction("save_consultation") { args -> store.saveIntake(args); GraineActionResult.ok("Saved.") }
Graine.registerAction("get_consultation") { GraineActionResult.ok("The consultation.", store.intake) }
Graine.registerAction("save_plan") { args -> store.savePlan(args); GraineActionResult.ok("Saved: 7 days, 35 items.") }
// get_plan, swap_item and log_check_in the same way.

// After the call: the coach's chat connected, then told — it may speak first.
Graine.chat.shown(true)
Graine.chat.status.first { it == GraineChatStatus.CONNECTED }
Graine.reportEvent("consultation_finished", "Build the user's first 7-day plan")
// The coach calls get_consultation, builds the week, calls save_plan → "Your plan is ready", the card, My week.
```

**5. The chat, check-ins and your own screens.**

```kotlin
Graine.calls.startCall(reason = "coach_call")    // the call button in the chat's header (the coach is the default agent)
// A check-in reminder opened (the app's own local notification): the chat, then once CONNECTED —
Graine.reportEvent("meal_check_in", "The user opened the breakfast reminder: Vegetable oats with paneer, planned for 8:00 am on Tuesday.")
Graine.setScreen(GraineScreen("plan_item", title = item.title, fields = listOf(GraineField("day", "Day", "Tuesday"), …)))
Graine.track("item_swapped", mapOf("day" to "tue", "time" to "13:30", "from" to old, "to" to new))
Graine.track("weekly_check_in", mapOf("score" to 4))
```

**6. Privacy.**

```kotlin
// Download my data: one JSON file, to the share sheet.
Graine.memories()
agent.chat.refreshPastSessions(); agent.chat.pastSessions; agent.chat.loadMorePastSessions()
agent.chat.sessionTranscript(session.id)
// Delete account: each agent forgets, then the user is let go, then the phone's data is erased.
Graine.agents.values.forEach { it.forgetMemory() }
GraineSecurity.signOut()                  // Graine.identify(null): the identity token goes with the identity
// Log out: signed out the same way, and this phone's journey erased.
```

**What it holds to.** The actions are registered at launch, so a call that rings a closed app finds them, and a
signed-in user is identified at every launch. A plan asked for in a chat that then ended (the app in the background)
is asked for again when the chat is back; the progress screen offers **Ask again** and **Go to the chat** when the plan
is slow, and never spins for ever. No two first calls stay booked, and none rings after the plan. A missed ring is "We
couldn't connect" whether the app was open, woken by it, or closed. Reminders skip quiet hours and follow every change
to the plan. Log out and Delete account leave nothing of the user on the phone. The tests: `JourneyStateTest`,
`JourneyEdgeCasesTest`, `JourneyActionArgsTest`, `JourneyPlanStoreTest`, `JourneyPackTest`, `SelectSeveralTest`.

