# Graine Demo — Android

A Kotlin app (Jetpack Compose, Android 8.0+, target 36) that shows every in-app feature of the Graine
SDK: the assistant in a voice bar, the chat window with components, phone-style calls rung by FCM,
identity and memory, calls the user books, and screen context with actions. Every domain word comes from
a use-case pack in `../usecases/` (care coach by default, or banking or shopping — switch on the sign-in
screen). All people and records are fictional.

## Set up

1. Copy `local.properties.example` to `local.properties` (git-ignored) and set:
   - `graine.publishableKey` — the agent's publishable key (Agent → Embed & Widgets). Add `app://` to the
     agent's **Allowed domains**.
   - `graine.applicationId` — your application id (default `com.example.grainedemo`).
   - `graine.baseUrl` — leave out unless you were given another server.
   - `graine.helpPublishableKey` — optional: a second agent (the pack's help desk) for the **Help** tab.
     Without it, the Help tab says how to add one.
2. For calls: copy your Firebase `google-services.json` to `app/` (git-ignored; see
   `app/google-services.json.example`). Without it the app builds and runs; only push ringing is off.
3. Recreate the agent from `../agent-setup/<pack>/` so its identity field, actions and call settings
   match the pack you demo.
4. `./gradlew :app:installDebug`, or run the `app` configuration in Android Studio.

The packs are bundled from `../usecases/*.json` at build time (task `bundleUseCases`); edit them there.

**Before the SDK version is published**, build against SDK sources on your machine — in `local.properties`:

```properties
graine.sdkDir=../../sdk-android/sdk/android
```

(or `-Pgraine.sdkDir=…`). Gradle then builds the SDK from that checkout in place of the published
`ai.graine:inapp-android:0.7.0`. Remove the line to go back.

## What is where

| | |
|---|---|
| `app/src/main/java/ai/graine/demo/graine/GraineService.kt` | The one file that calls the SDK (identify, memory, screen context, actions, scheduling, voice, chat, calls, FCM) |
| `…/domain/` | The pack model, the Items list, call and booking presentation — no SDK rules re-implemented |
| `…/ui/AppModel.kt` | The app's state and the agent's actions, run by effect in the pack's words |
| `…/ui/*Screen.kt` | Sign in · Home · Chat · Items · Scheduled calls · Memory · Profile · Call activity |
| `…/DemoMessagingService.kt` | Hands the FCM token and every data message to the SDK first |
| `…/notify/NotificationAccess.kt` | Notification permission (Android 13+) and full-screen calls (Android 14+) |
| `app/src/test/` | Unit tests against a fake SDK client: `./gradlew testDebugUnitTest` |

## Two agents in one app

Home, Chat and the Items screen talk to the main agent; the **Help** tab talks to the help desk — its own
chat (`GraineChatView.source = GraineInApp.get("help").chat`), voice bar (`GraineAgentBarView.graine`) and
phone-style call (`calls.startCall(…, agent = "help")`). The user signs in once and both agents know who
they are; **Calls** lists the calls booked with either agent, each with the agent's name, and the booking
sheet asks which agent should call. `GraineService.kt` initialises the help desk with
`GraineInApp.init(app, config, "help")`.

## Calls on a real device

1. In the dashboard, under **Workspace settings → In-app calling**, add the Android app: your
   application id and the Firebase service-account JSON. Graine then registers this device by itself
   when the app starts and FCM hands it a token.
2. Allow notifications when asked (Android 13+). On Android 14+, Home shows **Calls on this phone** if
   full-screen calls are off: **Open settings** goes straight to the switch.
3. Sign in as one of the pack's users, open **Calls**, tap the pack's booking button and book a call a few
   minutes ahead (the quick times help). Lock the phone: it rings full-screen with the pack's call name.
   Answer, talk, hang up; **Call activity** shows what the SDK reported, and the call's status chip changes.
4. **Call** on Home places a call the other way: the user calls the assistant.

## Memory, in two minutes

Sign in with memory on, tell the assistant one of the pack's `memoryExamples` (`tell`) in the chat, start a
new chat and ask the `ask` question: it answers from memory, and the **Memory** tab lists it. Sign in as
another user: nothing is shared. Switch back: it is there. **Erase everything** removes it.
