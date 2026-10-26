# Your app and your users — the demo apps (SDK 0.14.4)

Two protections, set per agent under **Agent → Embed & Widgets → Install → Your app and your users**. Each is
off until you turn it on, and each counts what it would refuse before it refuses anything:

- **Your apps** — which apps may use the agent's publishable key. Every request from the demo already says which
  app it is (iPhone: bundle ID and Team ID; Android: package name and the SHA-256 of its signing certificate). With
  **Require a verified app**, the app also proves it is yours: Apple App Attest on iPhone, Google Play Integrity on
  Android.
- **Verified users** — an identity token proves that the person the app names really signed in. The token is made
  by the app's own server. The demo apps have no server, so they never send one and never contain the secret that
  signs it: keep **Prove who the user is** off, or on report only, for the demo agent.

The Kotlin Multiplatform demo is ready for both: it proves the app at launch, its Android app includes Play
Integrity and its iPhone app has the App Attest capability. **Profile → What Graine has proven** shows what Graine
said about this build and this person:

| Line | What it says |
|---|---|
| App | *not checked* (the agent does not ask) · *verified ✓* · *debug token ✓* · *development build ✓* · *not verified* (and why: *debug token not registered*, *debug token expired*, …) |
| User | *not checked* · *unverified (report only)* — the demo sends no identity token |
| Debug token | Debug builds only: this install's debug token, to copy (below) |

## 1. The demo's own values

Enter these under **Your apps** (Kotlin Multiplatform demo, `kmp/`):

| | Value |
|---|---|
| iPhone — Bundle ID | `com.graine.callassistant.kmpdemo` |
| iPhone — Team ID | `68KL3D4D8Z` |
| Android — Package name | The `applicationId` your build uses: `com.example.grainedemo.kmp` unless `graine.applicationId` in `kmp/local.properties` says otherwise (for example `com.graine.callassistant.kmpdemo`) |
| Android — SHA-256 fingerprints | One per key that signs the builds you run (below) |

If you build the iPhone app with your own team and bundle ID (`kmp/iosApp/Config.xcconfig`), enter yours instead.

**Android fingerprints.** Paste each with or without colons:

- **Debug builds** (every build Android Studio runs) — each computer has its own debug key:

  ```sh
  cd kmp
  ./gradlew :composeApp:signingReport      # under "Variant: debug", the line SHA-256
  ```

- **Release and Google Play builds** — in **Play Console** → the app → **Test and release** → **App integrity**:
  the **App signing key certificate** SHA-256 (what Google Play signs with), and the **Upload key certificate**
  SHA-256 for builds you sign yourself.

## 2. Debug tokens: testing with a verified app required

Simulators, emulators and debug builds cannot prove themselves with App Attest or Play Integrity. A debug build — on
Android a debuggable one, on iPhone one built with the Debug configuration or running on the Simulator — sends a
**debug token** instead, made once per install. It never does that in a release build.

1. Run the debug build once. It logs this line once per install:

   ```
   Graine debug token for this install: gdt_… — register it under Agent → Embed & Widgets → Install → Development → Debug tokens to use this build while "Require a verified app" is on.
   ```

   - Android: `adb logcat -s Graine` while the app starts.
   - iPhone Simulator: the Xcode console, or
     `xcrun simctl spawn booted log stream --predicate 'subsystem == "ai.graine.inapp"'` before the app starts.
   - Either: the demo's **Profile → What Graine has proven → Debug token** (debug builds only) — press and hold to
     select and copy it.
2. In the dashboard: **Your app and your users → Prove it's your app → Development → Debug tokens**: a name you will
   recognise (for example *Asha's emulator*), the token, how long it stays valid (30 days unless you choose another,
   90 at most) → **Register debug token**. It is not shown again; revoke it there when the device is done.
3. The build now counts as your app (**Debug token** in What we've seen; *debug token ✓* on Profile).

A token belongs to one install: reinstalling the app, or clearing its data, makes a new one to register. An
unregistered or expired token is refused while **Require a verified app** is on (*not verified (debug token not
registered)* or *(debug token expired)* on Profile). **Test users** (accepted without an identity token, only from
an install with a registered debug token) are on the same page.

## 3. Turn it on — in this order

1. **Your apps** — add the iPhone app and the Android app (section 1). Saving apps refuses nothing; counting starts.
2. **What we've seen** — run the demo on each platform and watch its requests appear under its app ID. Every app
   you use must show up as allowed; anything you do not recognise is what *Only my apps* would refuse. Each
   switch shows how many of the last days' requests it would refuse.
3. Optional proofs:
   - **Prove it's your app** — App Attest (the demo's iPhone app already has the capability; builds Xcode installs
     use the development environment, accepted only while **Allow development builds** is on; TestFlight and App
     Store builds use production) and Play Integrity (the demo's Android app already includes it; in Play Console
     → **Test and release** → **App integrity**, link a Google Cloud project and enable the Play Integrity API, then
     give the dashboard the package's Play Integrity credentials when it asks, and the project's number if you
     linked a project of your own). Register the debug tokens of your test devices (section 2). Leave checking on
     report only, and watch What we've seen show *verified*, *Debug token* or *not verified* for each build.
   - **Prove who the user is** — not for the demo apps: they have no server to make identity tokens. Leave it off,
     or on report only to see every demo person counted as *unverified*.
4. **Turn it on → Only my apps.** From now on a request from an app that is not listed — another package name, Team
   ID or certificate — is refused (`AppNotAllowed`; the SDK's log says *This agent accepts only the apps listed under its Your apps settings*).
5. **Turn it on → Require a verified app** — once What we've seen shows every build you use as *verified* or
   *Debug token*. A build without a valid proof or a registered, unexpired debug token is refused.
6. **Turn it on → Require verified users** — not for the demo agent. With it on, a person without a valid identity
   token is anonymous: no memory, no name in Call History, and memories, past sessions and booking are refused. Use
   it in your own app once your server makes identity tokens.

Each switch is confirmed before it takes effect, with the requests it would have refused; turning one off takes
effect at once.

## Check your set-up

- **Nothing connects and the log says the app is not one of the agent's apps** (`AppNotAllowed`) → *Only my apps*
  is on and this build's package name, bundle ID, Team ID or certificate is not under Your apps (a debug build is
  signed with your computer's debug key: add its fingerprint).
- **Chat says it couldn't connect and Profile says *not verified (debug token not registered)*** → *Require a
  verified app* is on: register this install's debug token, or turn the switch off while you test.
- ***not verified (debug token expired)*** → register the token again, or a new one.
- **App says *not checked*** → the agent does not ask for a verified app, or its settings have not arrived yet.
