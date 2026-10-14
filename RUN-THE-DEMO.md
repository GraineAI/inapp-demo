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
   exactly the pack's field, or nobody is recognised; Devices per user 2.
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

**Where the SDKs come from today** — the committed manifests name 0.7.0 (React Native 0.41.0 and calls 0.2.0):

| Registry | Live | Used by |
|---|:-:|---|
| Swift Package Manager (`https://github.com/GraineAI/inapp-ios`, from 0.7.0) | **yes** | iOS, and the Kotlin Multiplatform app's iOS side |
| npm (`@graineai/inapp-react-native` 0.41.0, `@graineai/inapp-react-native-calls` 0.2.0) | **yes** | React Native |
| Maven Central (`ai.graine:inapp-android`, `ai.graine:inapp-kmp`) | not yet | Android, Kotlin Multiplatform → use the local-SDK switch |
| pub.dev (`graine_inapp`) | not yet | Flutter → use the local-SDK switch |
| CocoaPods trunk (`GraineInApp`) | not yet | not used by these apps |

The local-SDK switches build against the SDK's public source repositories, tag `0.7.0`. Clone them beside this
repository (commands below, run from the repository root); each switch is undone by removing it, and
`node scripts/check-manifests.mjs` fails while a React Native or Flutter manifest still points at local sources.

What works where: chat, components, memory, screen context, actions, the voice bar and booking work in a
simulator or emulator. Phone-style calls and rings need a **real phone**: the iOS simulator has no CallKit or
VoIP push, and hosted ringing needs a device registered with push credentials. In the emulator, voice needs the
host microphone turned on (Extended controls → Microphone).

### iOS

Xcode 16+. Copy `ios/Config.example.xcconfig` to `ios/Config.xcconfig` and set `GRAINE_PUBLISHABLE_KEY`,
`GRAINE_HELP_PUBLISHABLE_KEY`, and for a phone `DEVELOPMENT_TEAM` and `PRODUCT_BUNDLE_IDENTIFIER`. No switch:
the SDK comes from Swift Package Manager.

```sh
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
`sdk.dir`, `graine.publishableKey`, `graine.helpPublishableKey`, `graine.applicationId`. Until Maven Central
has 0.7.0, the switch:

```sh
git clone --branch 0.7.0 https://github.com/GraineAI/inapp-android ../inapp-android
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
`EXPO_PUBLIC_GRAINE_HELP_KEY`, or one of each per pack). No switch: npm has the SDKs.

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
`GRAINE_PUBLISHABLE_KEY` and `GRAINE_HELP_KEY` (or the per-pack keys). Until pub.dev has 0.7.0, the switch — a
path override in `flutter/pubspec_overrides.yaml`, never committed:

```sh
git clone --branch 0.7.0 https://github.com/GraineAI/inapp-flutter ../inapp-flutter
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

One Compose Multiplatform app in `kmp/`. Copy `kmp/local.properties.example` to `kmp/local.properties`
(`sdk.dir`, `graine.publishableKey`, `graine.helpPublishableKey`, `graine.applicationId`) and
`kmp/iosApp/Config.example.xcconfig` to `kmp/iosApp/Config.xcconfig` (team, bundle ID, the same keys).

Until Maven Central has `ai.graine:inapp-kmp` 0.7.0 — and `ai.graine:inapp-android`, which its Android side
builds on — build against the two public repositories side by side: the multiplatform SDK takes its Android side
from the folder `android` beside it.

```sh
git clone --branch 0.7.0 https://github.com/GraineAI/inapp-kmp ../graine-sdk/kmp
git clone --branch 0.7.0 https://github.com/GraineAI/inapp-android ../graine-sdk/android
echo "graine.kmpSdkDir=../../graine-sdk/kmp" >> kmp/local.properties

cd kmp
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Android: an emulator running, or a phone with USB debugging on
./gradlew :composeApp:installDebug
adb shell am start -n com.example.grainedemo/ai.graine.demo.MainActivity   # your graine.applicationId
./gradlew :composeApp:testDebugUnitTest                                    # checks

# iOS simulator: Xcode's first build phase runs Gradle with kmp/local.properties, so the same switch applies;
# GraineInApp comes from Swift Package Manager.
xcodebuild -project iosApp/iosApp.xcodeproj -scheme GraineDemoKMP \
  -destination 'platform=iOS Simulator,name=iPhone 17' -derivedDataPath build/ios CODE_SIGNING_ALLOWED=NO build
open -a Simulator
xcrun simctl install booted build/ios/Build/Products/Debug-iphonesimulator/GraineDemoKMP.app
xcrun simctl launch booted com.example.grainedemo.kmp    # your PRODUCT_BUNDLE_IDENTIFIER
```

On an iPhone: open `kmp/iosApp/iosApp.xcodeproj`, choose the iPhone, **Run**; Xcode needs JDK 17 or newer as
its `java` for the Gradle phase. The Android app and this one both default to the application id
`com.example.grainedemo`: give one of them another `graine.applicationId` to keep both on one phone.

## 5. The 10-minute demo

**Before the meeting** (once): both agents set up (section 1), credentials **Verified** (section 2), the app
installed on a **real phone** from your own build, signed in once as **Riya Sharma** (allow the microphone and
notifications). The day before, tell Asha *"I take metformin at 8 pm, after my evening walk."* in the chat and
close the app, so memory has something to show. Keep the dashboard open on Asha's **Calls & scheduling** and on
**Call History**.

| Time | Show | Do | What they see |
|---|---|---|---|
| 0:00 | Sign in | Graine Care → **Riya Sharma**, memory switch on → Continue | Home, with **Memory on** and the next booked call |
| 1:00 | Memory | Open **Memory**: what Asha remembers about Riya. Then **Chat** → **New conversation** → *"What do I take in the evening?"* | Asha answers from memory, in a fresh conversation |
| 2:30 | A component | In the chat: *"Let's do my daily check-in."* | The daily check-in card; a tap answers it |
| 3:30 | An action | *"Open my medicines"*, then *"I have taken my metformin"* | The app moves to Medicines; Metformin 500 mg turns **Taken today** |
| 4:30 | Call the agent | Home → **Call Asha** | A phone-style call; Asha knows who is calling and where they were |
| 5:30 | Booking | **Calls** → **Schedule a consultation call** → a reason, then the date and time pickers | 5-minute steps inside Asha's hours, the time zone shown; **Scheduled** with Cancel |
| 6:30 | Ring now | Dashboard → Asha → **Calls & scheduling** → **Ring a user now** → `MRN-000981` → **Ring now**. Lock the phone | It rings like a phone call, named *Asha · Graine Care*; answer — Asha says she is calling as booked |
| 7:30 | The second agent | Open **Help** (Billing & appointments help) → *"Why was I charged twice?"* | Another agent, its own chat — and it already knows it is Riya |
| 8:30 | Call History | Dashboard → **Call History** | Each conversation and call, with who it was: *Patient ID · MRN-000981* |
| 9:15 | Continuity | Close the app completely, open it again, open **Chat** | The chat where they left it: the earlier messages under a day line, the call as one "Voice call" row; **New conversation** in the header |

## 6. Troubleshooting

1. **"The app's publishable key was refused"**, or the assistant never starts — Allowed domains lacks
   `app://` (apps) or `localhost` (website), or the key belongs to another agent (the main and help keys
   swapped).
2. **"No publishable key is set"** and Continue stays off — the configuration file is missing or the app was
   not rebuilt: `Config.xcconfig` and `local.properties` are read at build time; React Native reads `.env` when
   the bundler starts (`npx expo start --clear`); Flutter reads `--dart-define-from-file` at `flutter run`.
3. **`Could not find ai.graine:inapp-android:0.7.0`** (or `inapp-kmp`) — Maven Central does not have it yet:
   set `graine.sdkDir` / `graine.kmpSdkDir` (section 3, 4). For Kotlin Multiplatform the Android SDK must be in
   the folder `android` beside the multiplatform one; in Xcode, the Gradle phase also needs JDK 17+ as `java`.
4. **`flutter pub get`: version solving failed for `graine_inapp`** — pub.dev does not have 0.7.0 yet: run
   `scripts/use-local-sdks.sh local flutter` with `../inapp-flutter` cloned.
5. **Memory stays empty, or the agent does not recognise the person** — Memory is off for the agent, **Who is
   the user?** is not the pack's field (`patient_id`, `customer_id`, `shopper_id`), the person turned memory
   off at sign-in, or they are past **Devices per user**. And ask after **New conversation**: a conversation
   that continues knows what was said in it, memory or not.
6. **A booking is refused** — "does not take bookings from the app": Calls & scheduling → **Users, from your
   app** is off; otherwise the time is outside the agent's Calling Window or quiet hours, or the person
   already has the most calls waiting. The app says which.
7. **A booked call or Ring now never rings, or ends as No device** — the credentials are not **Verified** for
   this exact bundle ID or package; it is a simulator or emulator; `google-services.json` is missing or for
   another package; the app has not been opened while signed in since it was installed (that is when the phone
   registers); notifications, or on Android 14+ full-screen calls, are off.
8. **Installing one Android app removes the other** — the Android and Kotlin Multiplatform apps share the
   default application id `com.example.grainedemo`; set another `graine.applicationId` in one of them (and use
   that package in Firebase and the dashboard).
