# Graine Demo

**Start here.** This page: what the demo shows, what the SDK does for you, set up, run and test.
[RUN-THE-DEMO.md](RUN-THE-DEMO.md): the dashboard, ringing, every platform step by step, and a 10-minute demo
script. Kotlin Multiplatform: [kmp/README.md](kmp/README.md). The full guide, offline:
[docs/integration-guide.pdf](docs/integration-guide.pdf). The documentation:
[www.graine.ai/docs/in-app](https://www.graine.ai/docs/in-app).

**This repository holds no keys, and the apps need no server of yours.** Each app holds only an agent's
publishable key, read from a git-ignored file — see [Keys and security](#keys-and-security).

One generic app, built for every platform the Graine in-app SDKs support, that shows everything they do:
an assistant that knows who the user is and remembers them (only with their consent), sees the screen
they are on and acts on it, chats with cards and lists, talks in a voice bar, takes a phone-style call,
and calls the user back at a time they book — ringing like a phone call even when the app is closed.

The app code contains no business domain. Every word that makes it a care coach, a banking assistant or a
shopping assistant comes from a **use-case pack** in [`usecases/`](usecases/README.md):

| Pack | App | Main agent (Home, Chat) | Help agent (Help screen) | Users identified by | List screen | Booking button |
|---|---|---|---|---|---|---|
| `health-coach` (default) | Graine Care | Asha, care coach | Billing & appointments help | `patient_id` | Medicines | Schedule a consultation call |
| `banking-support` | Graine Bank | Kabir, account assistant | Card security desk | `customer_id` | Cards | Request a callback |
| `retail-shopping` | Graine Store | Tara, shopping assistant | Returns desk | `shopper_id` | Orders | Book a delivery-update call |

Every app talks to **two agents**: the main agent on Home and Chat, and a help desk on the **Help** screen —
each its own agent with its own publishable key. The user signs in once; both agents know who they are, and
the Scheduled calls screen lists the calls booked with either, named.

All people, cards and orders are fictional.

<p>
  <img src="docs/screenshots/ios-sign-in.webp" width="200" alt="Sign in on iOS: the use-case picker, the three fictional patients and the memory switch">
  <img src="docs/screenshots/ios-home.webp" width="200" alt="Home on iOS: the memory chip, Talk, Chat and Call, and the next scheduled call">
  <img src="docs/screenshots/ios-chat-check-in.webp" width="200" alt="The chat on iOS: the daily check-in card above the conversation">
  <img src="docs/screenshots/ios-scheduled-calls.webp" width="200" alt="Check-in calls on iOS: Scheduled with Cancel, Answered, Missed and No device">
</p>
<!-- SCREENSHOT: the SDK's booking sheet — day chips, the time, "Asia/Kolkata · Thu 15 Oct, 6:00 PM", "What's it about?" filled with the pack's reason (Android) -->
<img src="docs/screenshots/web-memory-erase.webp" width="820" alt="The website: what Asha remembers about you, with the erase confirmation">

<sub>Taken with the offline demo (`scripts/offline-server.mjs`; for iOS see <a href="ios/README.md#offline-demo">ios/README.md</a>): no workspace and no key. More in <a href="docs/screenshots/">docs/screenshots</a>.</sub>

## What it shows

| Feature | React Native | Flutter | iOS | Android | Website | Kotlin Multiplatform |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| Three use cases, chosen on the sign-in screen and remembered | ✓ | ✓ ¹ | ✓ | ✓ | ✓ | ✓ ⁴ |
| Sign in as a fictional person, identified by the pack's field, with a memory consent switch | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Memory on / off chip on Home | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| "What the assistant remembers about you" — list, pull to refresh, consent switch, erase with confirmation | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Chat with components (check-in card, lists, time slots) | ✓ ² | ✓ | ✓ | ✓ | ✓ | ✓ |
| A conversation that continues: the chat reopens where the person left it (earlier messages under day lines, an earlier call as one row), with **New conversation** | ✓ ² | ✓ | ✓ | ✓ | ✓ | ✓ |
| Voice in the assistant bar | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Screen context on every screen | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| App actions the assistant runs: open a screen, open an item, change an item's status | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Call the assistant (a phone-style call) | ✓ | ✓ | ✓ | ✓ | — | ✓ |
| Book a call — the SDK's booking sheet (day, time, the time zone, an optional note) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Scheduled calls with status chips (scheduled, ringing, answered, completed, declined, missed, expired, no device) and Cancel | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Hosted ringing — Graine rings the booked call | ✓ ³ | ✓ ³ | ✓ ³ | ✓ ³ | rings the user's phone app | ✓ ³ |
| Call outcomes logged in the app, with the agent each call was with | ✓ | ✓ | ✓ | ✓ | — | ✓ |
| Two agents in one app: a Help screen on a second agent (chat, voice, calls, booking), one sign-in for both, both agents' calls listed by name | ✓ ⁵ | ✓ ⁵ | ✓ | ✓ | ✓ | ✓ |
| Your own design: the chat, voice, the call screen (with its connection report), booking and components drawn by the app from the SDK's public state, and a live Public state screen | — | — | — | — | — | ✓ ⁶ |

1. Flutter starts the chosen use case's two agents at launch: choosing a use case whose agents have other
   publishable keys takes effect the next time the app is opened (the app says so).
2. React Native draws the chat itself — in the Chat tab and under the Help screen's buttons — from the SDK's
   conversation state and components, laid out by the SDK's own rules: a conversation that continues shows its
   earlier messages under day lines and an earlier call as one "Voice call" row, and offers **New
   conversation**, as the SDK's chat windows do. An app that gives the chat a screen of its own can use the
   SDK's `GraineChatScreen`.
3. Real devices only (see [Testing hosted ringing](#testing-hosted-ringing)).
4. Kotlin Multiplatform: one Compose Multiplatform app in [`kmp/`](kmp/README.md) — Android 8.0+ and iOS 16+,
   every screen and SDK call in shared code, the SDK's own chat, call screen and voice bar as composables.
5. The help agent's key is optional: without it the Help screen says how to add one.
6. Home's switch, **Graine's screens / Your own design** ([`kmp/README.md`](kmp/README.md#your-own-design)): the
   same features with no SDK screen at all, for apps that draw every screen in their own design system.

## What the SDK does, and what your app does

None of these apps contains call, push or audio code: the SDK does all of it. What each app adds is the setup
below and the buttons that open the SDK's screens.

| | The SDK does it | The app does it |
|---|---|---|
| **iPhone calls** | CallKit — the phone's own incoming-call screen (lock screen and closed app included), answer, decline, end, mute, speaker, Bluetooth — with the ringtone and icon from the agent's design; registers for VoIP pushes (PushKit) and gives the token to Graine; the call's audio session | Push Notifications and Background Modes (Audio, Voice over IP) capabilities, `NSMicrophoneUsageDescription`, `enableVoIPPushes()` at every launch. No AppDelegate code |
| **Android calls** | The incoming call as a full-screen call notification that opens the SDK's call screen over the lock screen; the call's foreground service (type microphone) and audio; its own Firebase messaging service receives the call pushes and the token; its manifest brings the permissions and components | Firebase in the app (`google-services.json`, the Google services plugin, `firebase-messaging`); the SDK started in `Application.onCreate`, so a push can wake a closed app |
| **Permissions** | Asks for the microphone, notifications (Android 13+) and the full-screen ring (Android 14+); `callSetupProblems` lists what is still missing, with the fix | Calls `requestCallPermissions()` once (these apps do it at sign-in) |
| **Screens** | The chat, the voice bar, the call screen and the booking sheet, in the agent's design from the dashboard | Opens them from its buttons; while the app is open, shows the SDK's call screen during a call (`GraineCallScreen()` / `GraineCallView()` while `showsCallScreen`) |
| **Calls on WebRTC** | Both directions, with a fallback inside the same call when a network blocks it | Adds the WebRTC module (`GraineInAppRTC`, `inapp-android-rtc`, or the React Native / Flutter build switch) |
| **Who the user is** | Registers this phone for the signed-in user, releases it at sign-out; memory and conversations follow the user across phones | `identify(...)` after sign-in and at every launch, `identify(null)` at sign-out |

The same split on every platform, with each one's exact setup, is in the [integration guide](https://www.graine.ai/docs/in-app/integration-guide)
and [docs/integration-guide.pdf](docs/integration-guide.pdf).

## What you need

- A Graine workspace with dashboard access.
- For the apps: a real iPhone and/or Android phone (calls and rings do not work in simulators).
- React Native: Node.js 18+, Xcode 16+ with CocoaPods, Android Studio with JDK 17.
- Flutter: Flutter 3.24+ (Dart 3.5+), plus Xcode and Android Studio as above.
- iOS: Xcode 16+ (iOS 16+). Android: Android Studio, JDK 17 (Android 8.0+). Website: any static server.
- For hosted ringing: an Apple Developer account (an APNs `.p8` key) and a Firebase project (a service-account
  JSON and the app's `google-services.json`).

## Set up

1. **Create the agent** for the use case you want — [`agent-setup/SETUP.md`](agent-setup/SETUP.md) walks
   through it in ten minutes from `agent-setup/<pack>/` (prompt, actions, components, settings). Two settings
   must match the pack: **Memory → Who is the user? → Custom field** (`patient_id`, `customer_id` or
   `shopper_id`) — or **User ID**: the apps send the same id under `user_id` too — and
   **Calls & scheduling → Users, from your app** (on).
2. **Allow the apps and the website**: Agent → Embed & Widgets → Allowed domains: `app://` for the apps,
   `localhost` for the website on your machine.
3. **Copy the publishable key** (`pk_live_…`, Agent → Embed & Widgets) into each app's configuration:

| App | Configuration (git-ignored) | Copy from |
|---|---|---|
| React Native | `react-native/.env` — `EXPO_PUBLIC_GRAINE_PUBLISHABLE_KEY` and `EXPO_PUBLIC_GRAINE_HELP_KEY`, or one key of each per pack | `react-native/.env.example` |
| Flutter | `flutter/dart_defines.json` — `GRAINE_PUBLISHABLE_KEY` and `GRAINE_HELP_KEY`, or one key of each per pack | `flutter/dart_defines.example.json` |
| iOS | `ios/Config.xcconfig` — the keys, your Apple team and bundle ID | `ios/Config.example.xcconfig` |
| Android | `android/local.properties` — the keys and your application id | `android/local.properties.example` |
| Kotlin Multiplatform | `kmp/local.properties` (shared code and Android: the keys, your application id) and `kmp/iosApp/Config.xcconfig` (iOS: the same keys, your Apple team and bundle ID) | `kmp/local.properties.example`, `kmp/iosApp/Config.example.xcconfig` |
| Website | `web/config.js` | `web/config.example.js` |

Each pack is two agents (main and help). One main key for all three packs works for a quick look; give each
pack its own keys to demo all three properly. The help key is optional.

## Keys and security

- **No keys are committed.** Every app reads its publishable key from the git-ignored file in the table above;
  `.gitignore` also keeps out `google-services.json`, `GoogleService-Info.plist` and `*.p8`. No signing team is
  committed either: set your own (`DEVELOPMENT_TEAM` in `Config.xcconfig`, or in Xcode).
- **The publishable key (`pk_live_…`) is the only Graine credential an app holds.** It belongs to one agent,
  works only where that agent's **Allowed domains** let it (`app://` for apps), and is made to ship inside your
  app. Keep it out of public repositories all the same: whoever has it can talk to your agent, on your
  workspace's credits. If one leaks, **Agent → Embed & Widgets → rotate the key**: the old key stops working at
  once, so ship the new one in your app's next build.
- **No secret goes in an app, and none is needed.** Chat, voice, calls, booking, memory and ringing all run on the
  publishable key plus the signed-in user's ID that your app passes to the SDK.
- **Push credentials** — the APNs `.p8` key and the Firebase service-account JSON — are uploaded only in the
  dashboard (**Workspace settings → In-app calling**), where they are stored encrypted and never shown again.
  They never go in an app or a repository.

## Run

**React Native** — an Expo development build (not Expo Go: the calls package carries native code):

```bash
cd react-native
npm install
npx expo run:ios --device          # or: npx expo run:android --device
npx expo start --dev-client        # later, to reload JavaScript only
npx tsc --noEmit && npx jest       # checks
```

**Flutter**:

```bash
cd flutter
flutter pub get
flutter run --dart-define-from-file=dart_defines.json
flutter analyze && flutter test    # checks
```

The Flutter app's `ios/Podfile` already enables the microphone and notification prompts and sets iOS 15.

**iOS** — open `ios/GraineDemo.xcodeproj`, run the **GraineDemo** scheme ([`ios/README.md`](ios/README.md)).

**Android** — `cd android && ./gradlew :app:installDebug` ([`android/README.md`](android/README.md)).

**Kotlin Multiplatform** — Android: `cd kmp && ./gradlew :composeApp:installDebug`. iOS: open
`kmp/iosApp/iosApp.xcodeproj`, choose your iPhone and run the **GraineDemoKMP** scheme
([`kmp/README.md`](kmp/README.md)).

**Website** — from `demo/`: `npx serve .`, then open `http://localhost:3000/web/` ([`web/README.md`](web/README.md)).

### Where the SDKs come from

Every app uses the published release, **0.10.1** (React Native 0.44.1, calls 0.5.1). The sources of the iOS (and its
WebRTC module), Android, Kotlin Multiplatform and Flutter SDKs are public repositories (MIT licence), tagged with each
release.

| App | Dependency | Registry |
|---|---|---|
| iOS | `GraineInApp` from `https://github.com/GraineAI/inapp-ios` and `GraineInAppRTC` from `https://github.com/GraineAI/inapp-ios-rtc`, both from 0.14.3 | Swift Package Manager (the SDK is on CocoaPods too, as `GraineInApp`) |
| Android | `ai.graine:inapp-android:0.14.3` and `ai.graine:inapp-android-rtc:0.14.3` | Maven Central |
| Kotlin Multiplatform | `ai.graine:inapp-kmp` and `ai.graine:inapp-kmp-compose` 0.14.3 (+ `inapp-android-rtc`); iOS: `GraineInApp` and `GraineInAppRTC` 0.14.3 | Maven Central, Swift Package Manager |
| Flutter | `graine_inapp: ^0.13.1` | pub.dev |
| React Native | `@graineai/inapp-react-native` ^0.44.1, `@graineai/inapp-react-native-calls` ^0.5.1 | npm |
| Website | the loader, `https://www.graine.ai/embed/v1.js` | — |

To build against the SDK's sources instead (for example a fix that is not released yet), Flutter and React Native
switch with one script, from the repository root:

```bash
git clone --branch 0.13.1 https://github.com/GraineAI/inapp-flutter ../inapp-flutter
scripts/use-local-sdks.sh local flutter    # flutter/pubspec_overrides.yaml → ../inapp-flutter
GRAINE_SDK_RN_DIR=/path/to/sdk scripts/use-local-sdks.sh local rn
                                          # the folder holding the React Native SDK's inapp/ and inapp-calls/
scripts/use-local-sdks.sh published       # back to the published versions, before you commit
node scripts/check-manifests.mjs          # fails while a manifest still points at local sources
```

Then `flutter pub get` in `flutter/` and `npm install` in `react-native/`. `GRAINE_SDK_FLUTTER_DIR` points the
Flutter switch at another checkout. iOS has its own switch (`ios/use-local-sdk.sh`); Android and Kotlin
Multiplatform switch in their `local.properties`.

## Testing hosted ringing

A booked call is rung by Graine with a VoIP push (iOS) or a high-priority FCM message (Android). This only
works on **real devices**, with the app installed from your own build:

1. **Workspace settings → In-app calling**: upload your APNs key for the iOS bundle ID you build (React Native
   default `ai.graine.demo.rn`, Flutter `ai.graine.demo.flutter`; environment *Sandbox* for development builds)
   and your Firebase service-account JSON for the Android package. Wait until each shows **verified**.
2. **Android**: put your Firebase `google-services.json` in `react-native/` (or set
   `GRAINE_DEMO_GOOGLE_SERVICES_FILE`) and in `flutter/android/app/`, then rebuild. That is all: the apps
   have no messaging service of their own — the SDK registers for pushes itself (its own messaging service
   receives the push token and the call pushes, natively). React Native gets Firebase Cloud Messaging from
   the calls package's Expo plugin (calls 0.5.1+, listed in `app.config.ts`); Flutter declares it in
   `flutter/android/app/build.gradle.kts`. An app that already has a Firebase messaging service forwards
   from that one instead (`handlePush` and the push token, as the SDK's docs show).
3. On the phone, sign in (allow the microphone and, on Android 13+, notifications), open **Calls →
   Schedule a consultation call**: the SDK's booking sheet opens, its "What's it about?" already filled with
   the pack's reason. Pick today and a time a few minutes ahead, tap **Book call**, then **Done**. The call
   appears as **Scheduled** with **Cancel**.
4. Lock the phone or close the app. At the booked time it rings like a phone call, named as set under Calls &
   scheduling (for example "Asha · Graine Care"). Answer: the assistant opens with the booking's reason.
5. Back in the app, the call moves to **Answered**, then **Completed**; *Call activity on this phone* lists the
   ring and its outcome. Book again and decline → **Declined**; let it ring out → **Missed** (and one re-ring
   if set).
6. Book a call, then sign out before it is due → **No device** (signing out releases the phone).

## QA checklist

- [ ] The sign-in screen offers three use cases; switching changes the people, the agent's name, the list
      screen and the booking button.
- [ ] Sign in with memory on → Home shows **Memory on**. Turn the switch off on the Memory screen → **Memory off**.
- [ ] **Talk to Asha** opens the voice bar and the assistant greets the user by name; ending closes it.
- [ ] **Chat**: the assistant shows a component (for example the daily check-in); a tap answers it.
- [ ] A conversation continues: close the app in the middle of a chat and open it again — the chat shows the
      earlier messages under a day line (an earlier call as one "Voice call" row) and no starters; **New
      conversation** in the chat's header starts a fresh one, and what the assistant remembers stays.
- [ ] Screen context: on the list screen, ask "what is due today?" — the assistant answers from the screen.
- [ ] Actions: "open my medicines" navigates; "I have taken my metformin" marks it **Taken today**; asking
      again says it is already done; another person's item is refused.
- [ ] **Call Asha** places a phone-style call; the assistant knows the user called.
- [ ] Booking: the pack's booking button opens the SDK's booking sheet in the agent's design — day chips,
      the time in 5-minute steps inside what the agent allows, the time zone, "What's it about?" filled with
      the pack's reason; **Book call** → "Booked — …" → **Done**; the call appears with **Scheduled** and
      **Cancel**; Cancel → **Cancelled**.
- [ ] With Calls & scheduling → **Users, from your app** off, the booking button stays on screen, disabled,
      with "Booking needs, in Graine UI: users may book (Calls & scheduling), this app's push keys (Workspace settings → In-app calling), and Who is the user? set to this pack's ID."
- [ ] A refused booking is explained in the sheet, in one line under **Book call** (for example a time outside
      the agent's calling hours).
- [ ] Hosted ringing, as above.
- [ ] Two agents: **Help** opens the help agent (for example *Billing & appointments help*) — it already
      knows who the user is (no second sign-in); its chat and voice are its own; opening Help ends the main
      agent's voice and chat. Book a call from Help (its own sheet, starting with the help agent's reason) →
      it appears under Scheduled calls named after the help agent, next to the main agent's calls; Cancel
      works on either. *Call activity*
      names the agent of every ring and outcome.
- [ ] Memory never crosses people — the script below.

### Memory QA script

**How memory reaches the agent.** The app identifies the person with the pack's field (`patient_id`,
`customer_id` or `shopper_id`) before any chat or call opens. The same id also goes under `user_id`, so the person
is recognised whether the agent's **Who is the user?** is the pack's field or **User ID** (the dashboard's usual
choice): with no identity, or another field, every conversation is a stranger. A phone call is recognised by the
caller's number: the app user and the caller are one person only when **Who is the user?** is Phone number (and the
app identifies people by their number); with the pack's field they are two. At the start of each chat, Talk
or call, what the agent remembers about the person goes into what the model reads, before its first reply: after
the agent's prompt (for a flow agent, into the flow's agent information). What is said is saved every few messages
and when the conversation ends, and is ready about a minute later. Contact details, ID and card numbers, passwords
and codes, dates of birth, and medical conditions, prescriptions and diagnoses are removed before saving, whatever
the agent's rules say. How the agent uses what it remembers is up to its prompt (the **Memory** section of
`agent-setup/<pack>/prompt.md`).

A person who comes back finds their conversation where they left it, so to show what **memory** knows — not the
conversation — tap **New conversation** in the chat's header before asking: the chat starts fresh, and memory
stays. Each pack has two facts to tell (`memoryExamples` in its file):

| Pack | Tell, in one chat | Expect, in a new conversation |
|---|---|---|
| Care coach | "My goal is to walk 30 minutes every evening, after dinner." · "Please call me for check-ins after 7 pm." | The walking goal, and check-ins after 7 pm |
| Banking | "I travel to Singapore every March for work." · "Please call me only after 6 pm on weekdays." | Singapore every March, and calls after 6 pm on weekdays |
| Shopping | "I wear size 7 in running shoes." · "Please leave my parcels with the building security desk." | Size 7, and parcels with the security desk |

1. Sign in as the first person (memory on). In **Chat**, tell the assistant both sentences. End the
   conversation — close the app completely, or sign out and back in — and wait about a minute.
2. Open the chat as the same person: it continues where it was. Tap **New conversation** and ask "What do you
   remember about me?" — the assistant answers with both facts.
3. **Memory** lists them (pull to refresh). So does the dashboard: Agent → Memory → **People**.
4. Sign out, sign in as the second person, ask the same — the assistant does not know, and their Memory list
   does not show it.
5. Sign in as the first person again, tap **New conversation** and ask — the assistant knows again.
6. Erase: **Erase everything** → confirm, or erase the person on the dashboard's **People** tab. The list is
   empty, and in a new conversation the assistant no longer knows.
7. Opt out: turn memory off (on the sign-in screen or the Memory screen), tell it something new, end the
   conversation and wait a minute — in a new conversation it does not know it.
8. Phone calls, if the agent has a phone number: call it, say a fact, hang up, wait a minute and call again from
   the same number — it knows.

## Troubleshooting

- **"The app's publishable key was refused"** — Allowed domains lacks `app://` (apps) or `localhost` (website),
  or the key belongs to another agent.
- **Memory stays empty** — Memory is off for the agent, *Who is the user?* is neither the pack's field nor
  User ID, the user turned memory off, or the user is already on two other devices (Devices per user). Or it is
  too soon: what is said is ready about a minute after the conversation ends.
- **The assistant never remembers a medicine or a symptom** — by design: medical details are removed before
  anything is saved. It remembers goals, routines and preferences.
- **The assistant remembers a fact but does not act on it** — the agent's prompt lacks the pack's **Memory**
  section (`agent-setup/<pack>/prompt.md`).
- **The booking button is disabled (the line under it says what to set)** — Calls & scheduling → Users,
  from your app is off, the workspace has no in-app calling for this platform (Workspace settings → In-app
  calling), or **Who is the user?** is neither the pack's field nor User ID.
- **A booked call never rings** — In-app calling is not verified for this bundle ID or package, the phone is a
  simulator, or (Android) the build has no `google-services.json`.
- **Flutter keeps the previous use case** — that pack's agents have other keys; close and reopen the app.
- **The Help screen asks for a key** — add the help agent's publishable key (see Set up), created from
  `agent-setup/<pack>/help-settings.md`.
- **The assistant cannot hear you** — the microphone was not allowed. The apps ask through the SDK at sign-in;
  turn it on under the phone's Settings → the app → Microphone. An emulator's microphone is silent: you hear
  the agent, it hears nothing.
- More, for every platform: [RUN-THE-DEMO.md → Troubleshooting](RUN-THE-DEMO.md#6-troubleshooting) and
  [the documentation's testing page](https://www.graine.ai/docs/in-app/calling/testing).

## In this repository

| Folder | What |
|---|---|
| [`usecases/`](usecases/README.md) | The three use-case packs and their JSON Schema — shared by every platform |
| [`agent-setup/`](agent-setup/SETUP.md) | Recreate each pack's agent: prompt, actions, components, settings |
| `react-native/` | Expo development build (TypeScript); every SDK call in `src/graine.ts` |
| `flutter/` | Flutter app; every SDK call in `lib/graine_service.dart` |
| `ios/` | SwiftUI app |
| `android/` | Kotlin app |
| `kmp/` | Kotlin Multiplatform (Compose Multiplatform) app for Android and iOS; every SDK call in shared code (`composeApp/src/commonMain`) |
| `web/` | The website |
| `scripts/` | Pack and kit checker (`check-usecases.mjs`), agent actions generator, SDK source switch |
| `docs/` | The integration guide (`integration-guide.pdf`; online at [www.graine.ai/docs/in-app/integration-guide](https://www.graine.ai/docs/in-app/integration-guide)) and the screenshots |

## Documentation and references

| Topic | Where |
|---|---|
| In-app agents: overview, quickstart, set up from scratch | [Overview](https://www.graine.ai/docs/in-app) · [Quickstart](https://www.graine.ai/docs/in-app/quickstart) · [Set up everything from scratch](https://www.graine.ai/docs/in-app/setup-from-scratch) |
| The integration guide, every platform | [Integration guide: apps and website](https://www.graine.ai/docs/in-app/integration-guide) |
| Platforms | [Kotlin Multiplatform](https://www.graine.ai/docs/in-app/kotlin-multiplatform) · [iOS](https://www.graine.ai/docs/in-app/ios) · [Android](https://www.graine.ai/docs/in-app/android) · [Flutter](https://www.graine.ai/docs/in-app/flutter) · [React Native](https://www.graine.ai/docs/in-app/react-native) |
| Who the user is, memory, variables | [Identity and users](https://www.graine.ai/docs/in-app/identity) · [Identify the user](https://www.graine.ai/docs/in-app/how-to/identity-and-memory) · [Memory](https://www.graine.ai/docs/in-app/memory) · [Variables](https://www.graine.ai/docs/in-app/variables) |
| Calls | [In-app calling](https://www.graine.ai/docs/in-app/calling) · [How calls work](https://www.graine.ai/docs/in-app/calling/how-it-works) · [WhatsApp-style calls](https://www.graine.ai/docs/in-app/how-to/calls-from-the-app) · [Hosted ringing (push)](https://www.graine.ai/docs/in-app/calling/hosted) · [Scheduled calls](https://www.graine.ai/docs/in-app/calling/scheduled) · [Test and release](https://www.graine.ai/docs/in-app/calling/testing) |
| Booking | [Booking from your app with the SDK's sheet](https://www.graine.ai/docs/in-app/how-to/booking-sheet) |
| More than one agent | [Use more than one agent in your app](https://www.graine.ai/docs/in-app/multiple-agents) |
| Store review, privacy, going live | [Permissions](https://www.graine.ai/docs/in-app/permissions) · [Privacy and security](https://www.graine.ai/docs/in-app/privacy) · [Going live](https://www.graine.ai/docs/in-app/go-live) |
| Reference | [SDK reference](https://www.graine.ai/docs/in-app/api-reference) · [Changelog](https://www.graine.ai/docs/in-app/changelog) |
| SDK sources | [inapp-ios](https://github.com/GraineAI/inapp-ios) · [inapp-ios-rtc](https://github.com/GraineAI/inapp-ios-rtc) · [inapp-android](https://github.com/GraineAI/inapp-android) · [inapp-kmp](https://github.com/GraineAI/inapp-kmp) · [inapp-flutter](https://github.com/GraineAI/inapp-flutter) |
