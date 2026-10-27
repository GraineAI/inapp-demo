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
   Package Manager (`https://github.com/GraineAI/inapp-ios`, from 0.14.4), which the shared code calls.

The packs are bundled from `../usecases/*.json` at build time (task `generatePacks`, into the shared code,
so both platforms read the same files); edit them there.

**The published SDK, or its sources.** The app is pinned to SDK **0.14.4**: `ai.graine:inapp-kmp`,
`ai.graine:inapp-kmp-compose` and `ai.graine:inapp-android-rtc` from Maven Central, and GraineInApp and
GraineInAppRTC from Swift Package Manager (`iosApp/iosApp.xcodeproj`). With 0.14.4 published, nothing is set:
build and run as below.

**Your own app.** The multiplatform library calls Graine's iOS SDK and does not carry it, so an iOS app built
with it links GraineInApp itself, at the same version: add `https://github.com/GraineAI/inapp-ios` (and
`https://github.com/GraineAI/inapp-ios-rtc` for WebRTC calls) with Swift Package Manager to the **app target**, or
`pod 'GraineInApp', :git => 'https://github.com/GraineAI/inapp-ios.git', :tag => '0.14.4'` to its Podfile, and
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
git clone --branch 0.14.4 https://github.com/GraineAI/inapp-kmp ../graine-sdk/kmp
git clone --branch 0.14.4 https://github.com/GraineAI/inapp-android ../graine-sdk/android
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
filled in. Both platforms run both agents (`ai.graine:inapp-kmp` 0.14.4 on Android and iOS alike), and
**Call activity** names the agent of every ring and outcome.

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

## Server tokens (optional, the strongest mode)

With an agent's **Require server tokens** on (Agent → Embed & Widgets → Install → Server tokens), every request
needs a short-lived token your server mints for the signed-in user — the publishable key alone opens nothing.
In your app that is one line, `GraineConfig(tokenProvider = { myServer.fetchGraineToken() })`; your server calls
`POST /v2/client-tokens` with your **secret** API key. The SDK asks again shortly before each token expires, and a
token that expires never ends a chat or call in progress.

This demo has no server and never carries a secret, so **Profile → Server token (demo)** offers three stand-ins
(clearly marked, for development only; **Off** by default — verified users already come from the sign-in token; the
choice takes effect when the app starts again):

- **Graine demo server** — for Graine's demo agents only: Graine's demo token endpoint mints for whoever signs in,
  with the demo workspace's secret key held on Graine's server (`../agent-setup/SETUP.md`, section 9). Any other
  agent's key is refused.

- **Paste a token** — mint one yourself and paste it in (it lasts at most an hour):

  ```sh
  curl -s https://api.graine.ai/v2/client-tokens -H "Authorization: Bearer $GRAINE_API_KEY" \
    -H "Content-Type: application/json" \
    -d '{"agent_id":"<agent id>","user_id":"<the id you sign in with>","scopes":["chat","call","memory","booking"],"ttl_seconds":3600}'
  ```

- **Local minter** — run the demo minter on your own computer with **your** key, and the app fetches a token for
  whoever signs in, again before each one expires:

  ```sh
  GRAINE_API_KEY=gat_… GRAINE_AGENT_ID=<agent id> node ../agent-setup/token-minter.mjs
  ```

  The Android emulator reaches it at `http://10.0.2.2:8787`, the iOS simulator at `http://localhost:8787` (the
  app allows plain HTTP to those two addresses only). On a phone, use **Paste a token**. The minter mints for
  any user id it is asked for — your real server must mint only for the user its own sign-in proved.

Try it: set the agent to **Report only**, run the demo with each mode and watch Server tokens → What we've seen
count valid and missing tokens; then **Enforce** — Off can no longer open a chat, the others still can. The
help desk agent runs without tokens (the minter mints for one agent).

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
`shopper_id`) with their memory choice — `Graine.identify(traits, memory)` in `GraineService.kt` — before any chat,
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
Graine.identify(mapOf("user_id" to userId, "name" to name))
// Each agent keeps its own variables: the journey sets them on every agent it talks to.
Graine.agents.values.forEach { agent ->
    agent.setVariables(mapOf("gender" to "female", "age" to "33", "weight_kg" to "64.5", "height_cm" to "163",
        "goal" to "Lose weight", "diet" to "vegetarian", "agent_language" to "hi", "language_style" to "Hinglish: …"))
}
Graine.calls.requestCallPermissions()   // the microphone screen (and notifications on Android 13+)
```

**2. The first call** — now, or at a time the user picks from the agent's own times.

```kotlin
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
Graine.chat.status.first { it == GraineChatStatus.ONLINE }
Graine.reportEvent("consultation_finished", "Build the user's first 7-day plan")
// The coach calls get_consultation, builds the week, calls save_plan → "Your plan is ready", the card, My week.
```

**5. The chat, check-ins and your own screens.**

```kotlin
Graine.calls.startCall(reason = "coach_call")    // the call button in the chat's header (the coach is the default agent)
// A check-in reminder opened (the app's own local notification): the chat, then once ONLINE —
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
Graine.identify(null)
// Log out: identify(null), and this phone's journey erased.
```

**What it holds to.** The actions are registered at launch, so a call that rings a closed app finds them, and a
signed-in user is identified at every launch. A plan asked for in a chat that then ended (the app in the background)
is asked for again when the chat is back; the progress screen offers **Ask again** and **Go to the chat** when the plan
is slow, and never spins for ever. No two first calls stay booked, and none rings after the plan. A missed ring is "We
couldn't connect" whether the app was open, woken by it, or closed. Reminders skip quiet hours and follow every change
to the plan. Log out and Delete account leave nothing of the user on the phone. The tests: `JourneyStateTest`,
`JourneyEdgeCasesTest`, `JourneyActionArgsTest`, `JourneyPlanStoreTest`, `JourneyPackTest`, `SelectSeveralTest`.

