# Run the demo, end to end

From an empty workspace to a client meeting: the two agents in the dashboard, calls that ring, each app
running, and a 10-minute script to show it all. The examples use the default use case, **Graine Care**
(`health-coach`); the other two packs work the same way with their own folders and words.

1. [The dashboard: two agents](#1-the-dashboard-two-agents)
2. [Ringing: push credentials](#2-ringing-push-credentials)
3. [Run each app](#3-run-each-app)
4. [Kotlin Multiplatform, on Android and the iOS simulator](#4-kotlin-multiplatform-on-android-and-the-ios-simulator)
5. [The 10-minute demo](#5-the-10-minute-demo)
6. [Troubleshooting](#6-troubleshooting)

## 1. The dashboard: two agents

Every app talks to two agents of one pack: the **main** agent (Home, Chat, the list screen) and the **help**
agent (the Help screen). Each is built from `agent-setup/<pack>/`, step by step in
[`agent-setup/SETUP.md`](agent-setup/SETUP.md); every value is in the pack's `settings.md` (main) and
`help-settings.md` (help).

**The main agent** (Graine Care: **Asha**), from `agent-setup/health-coach/`:

1. **Agents → New agent**, named **Asha**. **Prompt**: everything below the line in `prompt.md`;
   **Welcome message** and **Custom Variables** from `settings.md` (including `who_called` = `customer` and
   an empty `call_reason`). **Calling Window**: 08:00–21:00.
2. **Memory → Settings**: remembers users **on**; **Who is the user?** → **Custom field** → `patient_id` —
   the pack's field (or **User ID**: the apps send the same id under `user_id` too); with any other field
   nobody is recognised; Devices per user 2; **What to remember — and what never to**: the text block in
   `settings.md`. The **Memory** section of `prompt.md`, already in the prompt, makes Asha use what she
   remembers.
3. **Embed & Widgets**:
   - **Install & domains → Allowed domains**: `app://` (every app) and the website's host — `localhost` on
     your machine, your own domain when you host it.
   - **What visitors get**: Call and chat.
   - **App actions → Import from JSON**: `actions.json`, then **Save** (an import that is not saved does
     nothing).
   - **Components**: the three in `components.json` (daily check-in, doses taken, check-in call times), each
     with its **Tool name**, selected for this agent.
   - Copy the **publishable key** (`pk_live_…`). The website also needs the **agent id** from the same tab.
4. **Calls & scheduling**: the agent may book **on**, users may book **on**, limits 3 · 14 · 5, **Name on the
   incoming call** `Asha · Graine Care`, ring 45 s, quiet hours 21:00–08:00.

**The help agent** (**Billing & appointments help**): repeat with `help-prompt.md` and `help-settings.md` — the
same **Who is the user?** field (`patient_id`: one sign-in identifies the person for both agents), the same
`actions.json`, the same Allowed domains, its own call name **Graine Care · Billing**. Copy its publishable key
(and agent id, for the website).

You now have two keys per pack. One main key for all three packs works for a quick look; give each pack its own
agents to demo all three.

## 2. Ringing: push credentials

Chat, voice and calls the person places work without this. For **Graine to ring** a booked call — and for
**Ring now** in the dashboard — upload your push credentials once: **Workspace settings → In-app calling**.
How to create them, step by step (the APNs key, the Firebase service account):
[www.graine.ai/docs/in-app/calling/hosted#create-your-credentials](https://www.graine.ai/docs/in-app/calling/hosted#create-your-credentials).

- **iOS**: one APNs key (`.p8`, Key ID, Team ID) serves every app of your team, but each app you build is
  registered by its own **bundle ID**, which must be one of your team's with Push Notifications enabled.
  Set the bundle ID in the app first (the table below), then enter that bundle ID with the key; Environment
  **Automatic**.
- **Android**: a Firebase project with an Android app whose **package name** is the demo's application id;
  its `google-services.json` goes into the demo (table below), and its service-account JSON into the dashboard
  with the same package name.
- Wait until each shows **Verified**. Rings reach **real devices only**: a simulator or emulator never rings.

| App | iOS bundle ID (set it in) | Android package (set it in) | `google-services.json` goes in |
|---|---|---|---|
| iOS | `ios/Config.xcconfig` → `PRODUCT_BUNDLE_IDENTIFIER` | — | — |
| Android | — | `android/local.properties` → `graine.applicationId` | `android/app/` |
| React Native | `react-native/.env` → `GRAINE_DEMO_IOS_BUNDLE_ID` | `react-native/.env` → `GRAINE_DEMO_ANDROID_PACKAGE` | `react-native/` |
| Flutter | the Runner target in Xcode (`ai.graine.demo.flutter`) | `flutter/android/app/build.gradle.kts` → `applicationId` | `flutter/android/app/` |
| Kotlin Multiplatform | `kmp/iosApp/Config.xcconfig` → `PRODUCT_BUNDLE_IDENTIFIER` | `kmp/local.properties` → `graine.applicationId` | `kmp/composeApp/` |

## 3. Run each app

**Where the SDKs come from today** — the committed manifests name 0.14.3 for iOS, Android, Flutter and Kotlin
Multiplatform (past sessions, and a chat that closes 15 seconds after its screen) and React Native 0.44.1 with calls 0.5.1. Every app includes the SDK's WebRTC module — iOS `GraineInAppRTC`, Android `inapp-android-rtc`, Flutter `GRAINE_WEBRTC` / `graineWebRtc`, React Native the calls plugin's `webrtc: true` — so calls and answered rings carry their audio over WebRTC. The apps build from the registries; the local-SDK switch
below is only for building against SDK sources on your machine:

| Registry | Live | Used by |
|---|:-:|---|
| Swift Package Manager (`https://github.com/GraineAI/inapp-ios`, from 0.14.3) | yes | iOS; the Kotlin Multiplatform app's iOS side |
| npm (`@graineai/inapp-react-native` 0.44.1, `@graineai/inapp-react-native-calls` 0.5.1) | yes | React Native |
| Maven Central (`ai.graine:inapp-android` 0.14.3, `ai.graine:inapp-kmp` 0.14.3) | yes | Android, Kotlin Multiplatform |
| pub.dev (`graine_inapp` 0.14.3) | yes | Flutter |
| CocoaPods trunk (`GraineInApp` 0.13.1) | yes | not used by these apps |

The local-SDK switches build against the SDK's public source repositories, tag `0.14.3` once it is published
(or any checkout of the SDK's sources). Clone them beside this repository (commands below, run from the
repository root); each switch is undone by removing it, and `node scripts/check-manifests.mjs` fails while a
React Native or Flutter manifest still points at local sources.

What works where: chat, components, memory, screen context, actions, the voice bar and booking work in a
simulator or emulator. Phone-style calls and rings need a **real phone**: the iOS simulator has no CallKit or
VoIP push, and hosted ringing needs a device registered with push credentials. In the emulator, voice needs the
host microphone turned on (Extended controls → Microphone).

### iOS

Xcode 16+. Copy `ios/Config.example.xcconfig` to `ios/Config.xcconfig` and set `GRAINE_PUBLISHABLE_KEY`,
`GRAINE_HELP_PUBLISHABLE_KEY`, and for a phone `DEVELOPMENT_TEAM` and `PRODUCT_BUNDLE_IDENTIFIER`. Until
Swift Package Manager has 0.14.3, the switch (XcodeGen; it writes `GraineDemoLocal.xcodeproj`, git-ignored —
use it in place of `GraineDemo.xcodeproj` below):

```sh
git clone --branch 0.14.3 https://github.com/GraineAI/inapp-ios ../inapp-ios
ios/use-local-sdk.sh

cd ios
# Simulator (any iOS 16+ one: xcrun simctl list devices available)
xcodebuild -project GraineDemo.xcodeproj -scheme GraineDemo \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build CODE_SIGNING_ALLOWED=NO build
open -a Simulator
xcrun simctl install booted build/Build/Products/Debug-iphonesimulator/GraineDemo.app
xcrun simctl launch booted com.example.grainedemo        # your PRODUCT_BUNDLE_IDENTIFIER
xcodebuild test -project GraineDemo.xcodeproj -scheme GraineDemo \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build CODE_SIGNING_ALLOWED=NO
```

**iPhone**: open `ios/GraineDemo.xcodeproj`, choose your iPhone, **Run**. Without a workspace at hand, the
offline demo draws every screen in the simulator: [`ios/README.md`](ios/README.md#offline-demo).

### Android

Android Studio, JDK 17. Copy `android/local.properties.example` to `android/local.properties` and set
`sdk.dir`, `graine.publishableKey`, `graine.helpPublishableKey`, `graine.applicationId`. The SDK comes from Maven
Central (0.14.3). Optional — to build against SDK sources on your machine instead, the switch:

```sh
git clone --branch 0.14.3 https://github.com/GraineAI/inapp-android ../inapp-android
echo "graine.sdkDir=../../inapp-android" >> android/local.properties

cd android
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :app:installDebug          # an emulator running (Device Manager), or a phone with USB debugging on
adb shell am start -n com.example.grainedemo/ai.graine.demo.MainActivity   # your graine.applicationId
./gradlew testDebugUnitTest          # checks
```

### React Native

Node 18+, Xcode with CocoaPods, Android Studio with JDK 17; an Expo development build (not Expo Go). Copy
`react-native/.env.example` to `react-native/.env` and set the keys (`EXPO_PUBLIC_GRAINE_PUBLISHABLE_KEY`,
`EXPO_PUBLIC_GRAINE_HELP_KEY`, or one of each per pack). `npm install` takes the published SDK 0.44.1 and calls
0.5.1. (To build against the SDK's sources instead, `GRAINE_SDK_RN_DIR=/path/to/sdk scripts/use-local-sdks.sh
local rn` packs them into git-ignored tarballs in `react-native/.local-sdk/` and points `package.json` at them;
`scripts/use-local-sdks.sh published rn` switches back before you commit.)

```sh
cd react-native
npm install
npx expo run:ios                     # the iOS simulator
npx expo run:ios --device            # an iPhone (pick it from the list)
npx expo run:android                 # the running emulator, or a connected phone
npx expo start --dev-client          # later: reload JavaScript only
npx tsc --noEmit && npx jest         # checks
```

### Flutter

Flutter 3.24+. Copy `flutter/dart_defines.example.json` to `flutter/dart_defines.json` and set
`GRAINE_PUBLISHABLE_KEY` and `GRAINE_HELP_KEY` (or the per-pack keys). Until pub.dev has 0.14.3, the switch — a
path override in `flutter/pubspec_overrides.yaml`, never committed:

```sh
git clone --branch 0.14.3 https://github.com/GraineAI/inapp-flutter ../inapp-flutter
scripts/use-local-sdks.sh local flutter

cd flutter
flutter pub get
flutter devices                                               # the simulator, emulator or phone ids
flutter run -d <id> --dart-define-from-file=dart_defines.json
flutter analyze && flutter test                               # checks
```

`scripts/use-local-sdks.sh published flutter` removes the override once pub.dev has the version.

### Website

The page uses the website loader from `www.graine.ai`, which is live. Copy `web/config.example.js` to
`web/config.js` and set `agentId` and `publishableKey`, and `help: { agentId, publishableKey }`. Then, from
the repository root:

```sh
npx serve .                          # or: python3 -m http.server 3000
open http://localhost:3000/web/      # localhost, not 127.0.0.1 — it must match Allowed domains
```

The website has no phone-style calls; a call booked there rings the person's phone app.

## 4. Kotlin Multiplatform, on Android and the iOS simulator

One Compose Multiplatform app in `kmp/`, on `ai.graine:inapp-kmp` and `ai.graine:inapp-kmp-compose` **0.14.3**
(every state the SDK's own screens read is public: Home's **Your own design** draws them all in the app), with
GraineInApp 0.14.3 on iOS — the multiplatform SDK and the iOS SDK must be the same version.

**You need** a Mac with Xcode 16 or newer and an iOS simulator, JDK 17 (`/usr/libexec/java_home -v 17` finds it),
the Android SDK with platform 36 (Android Studio puts it in `~/Library/Android/sdk`), and an emulator (Android
Studio → Device Manager) or a phone with USB debugging on.

**Configure** — from the repository root:

```sh
cp kmp/local.properties.example kmp/local.properties
cp kmp/iosApp/Config.example.xcconfig kmp/iosApp/Config.xcconfig
```

In `kmp/local.properties` set `sdk.dir`, `graine.publishableKey` (and `graine.helpPublishableKey` for the Help tab)
and `graine.applicationId` (the commands below use the default, `com.example.grainedemo.kmp`). In
`kmp/iosApp/Config.xcconfig` set the same keys, `PRODUCT_BUNDLE_IDENTIFIER`, and `DEVELOPMENT_TEAM` for an iPhone.

**Optional: the SDK from its sources** (the app normally takes 0.14.3 from Maven Central and Swift Package Manager;
until 0.14.3 is published there, this is how it builds) — the multiplatform SDK with the Android SDK its Android side
builds on (`kmp` and `android` side by side), and the iOS SDK with its WebRTC module (`ios` and `ios-rtc` side by
side), all of one version:

```sh
echo "graine.kmpSdkDir=/path/to/sdk/kmp" >> kmp/local.properties   # or GRAINE_KMP_SDK_DIR=/path/to/sdk/kmp
GRAINE_IOS_SDK_PATH=/path/to/sdk/ios kmp/iosApp/use-local-sdk.sh    # writes kmp/iosApp/iosAppLocal.xcodeproj
```

The tagged public repositories work for the Kotlin side, cloned beside this repository:

```sh
git clone --branch 0.14.3 https://github.com/GraineAI/inapp-kmp ../graine-sdk/kmp
git clone --branch 0.14.3 https://github.com/GraineAI/inapp-android ../graine-sdk/android
echo "graine.kmpSdkDir=../../graine-sdk/kmp" >> kmp/local.properties
```

The SDK's own builds read the Android SDK's location from `ANDROID_HOME`, or else from `sdk.dir` in
`kmp/local.properties`, which the switch hands them.

**Android** — an emulator running, or a phone connected:

```sh
cd kmp
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:installDebug
adb shell am start -n com.example.grainedemo.kmp/ai.graine.demo.MainActivity   # your graine.applicationId
```

**iOS simulator** — still in `kmp/`; Xcode's first build phase runs Gradle with the same `local.properties`, and it
needs JDK 17 or newer as the `java` it finds:

```sh
SIM='iPhone 17'                    # any iOS 16+ simulator: xcrun simctl list devices available
xcodebuild -project iosApp/iosApp.xcodeproj -scheme GraineDemoKMP \
  -destination "platform=iOS Simulator,name=$SIM" -derivedDataPath build/ios CODE_SIGNING_ALLOWED=NO build
xcrun simctl boot "$SIM"; open -a Simulator
xcrun simctl install "$SIM" build/ios/Build/Products/Debug-iphonesimulator/GraineDemoKMP.app
xcrun simctl launch "$SIM" com.example.grainedemo.kmp    # your PRODUCT_BUNDLE_IDENTIFIER
```

`iosApp/iosApp.xcodeproj` takes GraineInApp 0.14.3 from Swift Package Manager — no checkouts, no `graine.kmpSdkDir`.
With the SDK's sources (above), build the `iosApp/iosAppLocal.xcodeproj` that `iosApp/use-local-sdk.sh` generates
instead. On an iPhone: open `iosApp/iosApp.xcodeproj` in Xcode, choose
the iPhone, **Run** (calls and rings need a real iPhone).

**What to check**: sign in (a fictional user; the memory switch is yours), **Chat** → a starter → the agent answers;
**Talk** → the voice bar connects and the agent greets you (an emulator's microphone is silent: you hear the
greeting); **Calls** → the pack's booking button opens the SDK's own booking sheet when that agent can be booked
here, or is off with "Booking needs, in Graine UI: users may book (Calls & scheduling), this app's push keys (Workspace settings → In-app calling), and Who is the user? set to this pack's ID." (`bookingAvailable`: Graine rings this phone, the
agent lets users book, and the user is identified with the agent's **Who is the user?** field — the pack's or User ID);
**Memory** → the test in [`kmp/README.md`](kmp/README.md#memory-in-a-few-minutes) (two facts in **Chat**, close it,
a minute later **New conversation** → "What do you remember about me?").
Then Home → **Your own design** and the same again, every screen drawn by the app: **Chat** (send, close, reopen — it
continues), **Talk** (it starts only on its **Start**), **Call** (the app's call screen, its **Connection** card live
and, after the call, final), the booking button (the app's own picker of the SDK's times), and **Public state** (every
value, live). Back to **Graine's screens**, and the SDK's own screens are there as before.

## 5. The 10-minute demo

**Before the meeting** (once): both agents set up (section 1), credentials **Verified** (section 2), the app
installed on a **real phone** from your own build, signed in once as **Riya Sharma** (allow the microphone and
notifications). The day before, tell Asha *"My goal is to walk 30 minutes every evening, after dinner."* and
*"Please call me for check-ins after 7 pm."* in the chat and close the app, so memory has something to show (a
medicine or a symptom would not do: medical details are never kept). Keep the dashboard open on Asha's **Calls &
scheduling**, **Memory → People** and **Call History**.

| Time | Show | Do | What they see |
|---|---|---|---|
| 0:00 | Sign in | Graine Care → **Riya Sharma**, memory switch on → Continue | Home, with **Memory on** and the next booked call |
| 1:00 | Memory | Open **Memory**: what Asha remembers about Riya. Then **Chat** → **New conversation** → *"What do you remember about me?"* | Asha answers from memory, in a fresh conversation: the walking goal and check-ins after 7 pm; the dashboard's **People** tab shows the same |
| 2:30 | A component | In the chat: *"Let's do my daily check-in."* | The daily check-in card; a tap answers it |
| 3:30 | An action | *"Open my medicines"*, then *"I have taken my metformin"* | The app moves to Medicines; Metformin 500 mg turns **Taken today** |
| 4:30 | Call the agent | Home → **Call Asha** | A phone-style call; Asha knows who is calling and where they were |
| 5:30 | Booking | **Calls** → **Schedule a consultation call** → a day and a time in the booking sheet → **Book call** → **Done** | The SDK's own booking sheet in Asha's design: the day, the time in 5-minute steps inside Asha's hours, the time zone, "What's it about?" already filled; the call **Scheduled**, with Cancel |
| 6:30 | Ring now | Dashboard → Asha → **Calls & scheduling** → **Ring a user now** → `MRN-000981` → **Ring now**. Lock the phone | It rings like a phone call, named *Asha · Graine Care*; answer — Asha says she is calling as booked |
| 7:30 | The second agent | Open **Help** (Billing & appointments help) → *"Why was I charged twice?"* | Another agent, its own chat — and it already knows it is Riya |
| 8:30 | Call History | Dashboard → **Call History** | Each conversation and call, with who it was: *Patient ID · MRN-000981* |
| 9:15 | Continuity | Close the app completely, open it again, open **Chat** | The chat where they left it: the earlier messages under a day line, the call as one "Voice call" row; **New conversation** in the header |
| 9:40 | Past sessions | In the chat, **Past sessions** (Kotlin Multiplatform: in **Your own design**, the chat's menu) → tap the newest | Riya's earlier chats, voice conversations and calls with Asha, newest first — when, how long, how it ended, the first words — and the tapped one's transcript, read only: the same sessions as the dashboard's **Call History** |

## 6. Troubleshooting

1. **"The app's publishable key was refused"**, or the assistant never starts — Allowed domains lacks
   `app://` (apps) or `localhost` (website), or the key belongs to another agent (the main and help keys
   swapped).
2. **"No publishable key is set"** and Continue stays off — the configuration file is missing or the app was
   not rebuilt: `Config.xcconfig` and `local.properties` are read at build time; React Native reads `.env` when
   the bundler starts (`npx expo start --clear`); Flutter reads `--dart-define-from-file` at `flutter run`.
3. **`Could not find ai.graine:inapp-android:0.7.0`** (or `inapp-kmp`) — Maven Central does not have it yet:
   set `graine.sdkDir` / `graine.kmpSdkDir` (section 3, 4). For Kotlin Multiplatform (`ai.graine:inapp-kmp:0.14.3`)
   the Android SDK must be in the folder `android` beside the multiplatform one (`../graine-sdk/android`); in
   Xcode, the Gradle phase also needs JDK 17+ as `java`.
4. **`flutter pub get`: version solving failed for `graine_inapp`** — pub.dev does not have 0.7.0 yet: run
   `scripts/use-local-sdks.sh local flutter` with `../inapp-flutter` cloned.
5. **Memory stays empty, or the agent does not recognise the person** — Memory is off for the agent, **Who is
   the user?** is neither the pack's field (`patient_id`, `customer_id`, `shopper_id`) nor **User ID**, the
   person turned memory off at sign-in, or they are past **Devices per user**. And ask after
   **New conversation**, about a minute after the earlier conversation ended: a conversation that continues
   knows what was said in it, memory or not, and what is said is ready for the next one about a minute after
   it ends.
6. **The booking button is off ("Booking needs, in Graine UI: users may book (Calls & scheduling), this app's push keys (Workspace settings → In-app calling), and Who is the user? set to this pack's ID."), or the sheet says "Booking a call
   isn't available right now"** — Calls & scheduling → **Users, from your app** is off, Graine cannot ring
   this phone yet (Workspace settings → In-app calling has no credentials for this platform), or the person
   is not identified with the agent's **Who is the user?** trait. Inside the sheet, a refused time says why in
   one line: too soon, too far ahead, outside the agent's calling hours, or too many calls already booked.
7. **A booked call or Ring now never rings, or ends as No device** — the credentials are not **Verified** for
   this exact bundle ID or package; it is a simulator or emulator; `google-services.json` is missing or for
   another package; the app has not been opened while signed in since it was installed (that is when the phone
   registers); notifications, or on Android 14+ full-screen calls, are off.
8. **Installing one Android app removes the other** — the Android and Kotlin Multiplatform apps have the same
   `graine.applicationId` (their defaults differ: `com.example.grainedemo` and `com.example.grainedemo.kmp`); give
   each its own (and use that package in Firebase and the dashboard).
9. **Kotlin Multiplatform: `SDK location not found` … `graine-sdk/android/local.properties`** (or `…/kmp/…`) — the
   SDK's own builds find the Android SDK through `ANDROID_HOME` or the `sdk.dir` the switch hands them from
   `kmp/local.properties`: set `sdk.dir` there (Android Studio writes it when it opens `kmp/`), or
   `export ANDROID_HOME=$HOME/Library/Android/sdk`.
10. **Xcode: `no versions of 'inapp-ios' match the requirement 0.14.3..<1.0.0`** — the Kotlin Multiplatform app's
   `iosApp/iosApp.xcodeproj` asks Swift Package Manager for GraineInApp 0.14.3, and the tag is not there yet: run
   `kmp/iosApp/use-local-sdk.sh` with `GRAINE_IOS_SDK_PATH` at the iOS SDK's sources (its WebRTC module beside it,
   in `ios-rtc`, or `GRAINE_IOS_RTC_SDK_PATH`) and build `kmp/iosApp/iosAppLocal.xcodeproj`.
11. **iOS: the booking button stays off, or `presentBooking` fails with `Unavailable`, though the agent can be
   booked** — the app links a GraineInApp older than the multiplatform SDK (0.7.0 has no booking sheet): link the
   same version, 0.14.3.
