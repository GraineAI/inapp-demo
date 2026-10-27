# Your app and your users — the demo apps (SDK 0.14.4 and later)

Two protections, set per agent under **Agent → Embed & Widgets → Install → Your app and your users**. Each is
off until you turn it on, and each counts what it would refuse before it refuses anything:

- **Your apps** — which apps may use the agent's publishable key. Every request from the demo already says which
  app it is (iPhone: bundle ID and Team ID; Android: package name and the SHA-256 of its signing certificate). With
  **Require a verified app**, the app also proves it is yours: Apple App Attest on iPhone, Google Play Integrity on
  Android.
- **Verified users** — an identity token proves that the person the app names really signed in. The demo uses the
  **sign-in token (JWT)** method — the one to use with your own sign-in: your identity provider signs a short-lived
  ID token, publishes its public keys as a JWKS, and Graine checks the token against them, holding no secret. The demo
  apps never contain a secret either: they ask **Graine's demo sign-in server** for the ID token of whoever signed in
  (section 4) — a stand-in for your own sign-in.

**Every agent whose key ships in an app is set up on its own.** The demo apps use up to three agents — the main agent,
the help agent (Help) and, in the Kotlin Multiplatform Journey, the first-call agent — and each needs its own Your apps,
proofs, debug tokens and switches. Nothing is shared between agents, and a key nobody checks is the one anyone would use.

The Kotlin Multiplatform demo is the reference: every line of its security is in
`kmp/composeApp/src/commonMain/kotlin/ai/graine/demo/security/GraineSecurity.kt`, and `kmp/README.md` → *Security — how
this app is set up* maps each dashboard step to the code. It proves the app at launch, its Android app includes Play
Integrity and its iPhone app has the App Attest capability. **Profile → Security** shows, for every agent it talks to:

| Line | What it says |
|---|---|
| App | *not checked* (the agent does not check proofs) · *verified* (App Attest or Play Integrity, a registered debug token, or a development build) · *not verified* and why (*debug token not registered*, *debug token expired*, …) |
| User | *verified* — the demo sends the ID token from the demo sign-in server · *not proven* and why (*no identity token*, *identity token expired*, …) · *not checked* (the agent does not verify its users) |
| Server token | *off* · *ok* · *missing* · *refused* and the code (section 5) |
| Last refusal | The last refusal about the app, the user, the token or the key that the agent's requests met, with what fixes it |
| Debug token | Debug builds only: this install's debug token, with **Copy** (section 2) |

The **Help** screen also says when the help agent checks neither the app nor its users.

## 1. The demo's own values

Enter these under **Your apps** — on **each** agent the build talks to (Kotlin Multiplatform demo, `kmp/`):

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
   - Either: the demo's **Profile → Security → Debug token** (debug builds only) — **Copy**.
2. In the dashboard, on **each** agent: **Your app and your users → Prove it's your app → Development → Debug tokens**:
   a name you will recognise (for example *Asha's emulator*), the token, how long it stays valid (30 days unless you
   choose another, 90 at most) → **Register debug token**. It is not shown again; revoke it there when the device is
   done.
3. The build now counts as your app (**Debug token** in What we've seen; *verified — registered debug token* on
   Profile → Security).

A token belongs to one install: reinstalling the app, or clearing its data, makes a new one to register. An
unregistered or expired token is refused while **Require a verified app** is on (*not verified — debug token not
registered* or *debug token expired* on Profile → Security). **Test users** (accepted without an identity token, only
from an install with a registered debug token) are on the same page.

## 3. Turn it on — in this order, for each agent

1. **Your apps** — add the iPhone app and the Android app (section 1). Saving apps refuses nothing; counting starts.
2. **What we've seen** — run the demo on each platform and watch its requests appear under its app ID. Every app
   you use must show up as allowed; anything you do not recognise is what *Only my apps* would refuse. Each
   switch shows how many of the last days' requests it would refuse.
3. Optional proofs:
   - **Prove it's your app** — App Attest (the demo's iPhone app already has the capability; builds Xcode installs
     use the development environment, accepted only while **Allow development builds** is on; TestFlight and App
     Store builds use production) and Play Integrity (the demo's Android app already includes it; in Play Console
     → **Test and release** → **App integrity**, link a Google Cloud project and enable the Play Integrity API, then
     upload a service-account JSON key for that project when the dashboard asks, and the project's number if you
     linked a project of your own). Register the debug tokens of your test devices (section 2). Leave checking on
     report only, and watch What we've seen show *verified*, *Debug token* or *not verified* for each build.
   - **Prove who the user is** — **Sign-in token (JWT)**, with the demo sign-in server's values (section 4). Leave it
     on report only and watch What we've seen count every demo person as *verified*.
4. **Turn it on → Only my apps.** From now on a request from an app that is not listed — another package name, Team
   ID or certificate — is refused with `app_not_allowed` (`GraineErrorCode.AppNotAllowed`; the Kotlin Multiplatform
   demo says "This version of the app can't connect to the assistant", and Profile → Security shows it as the last
   refusal). The SDK never retries it.
5. **Turn it on → Require a verified app** — once What we've seen shows every build you use as *verified* or
   *Debug token*. A build without a valid proof or a registered, unexpired debug token is refused
   (`app_not_verified`).
6. **Turn it on → Require verified users** — once What we've seen shows every demo person as *verified*. From then on
   a person without a valid ID token is anonymous: no memory, no name in Call History, and memories, past sessions
   and booking are refused (`identity_unverified`).

Each switch is confirmed before it takes effect, with the requests it would have refused; turning one off takes
effect within 30 seconds.

## 4. Verified users with the demo sign-in server

The demo apps get their user's ID token from a **server**, as your app gets one from your own sign-in — never from a
secret built into the app:

- At sign-in, and at every launch while someone is signed in, the app asks
  `POST https://www.graine.ai/api/demo/identity-token` with
  `{ "publishableKey": "<the app's key>", "userId": "<the person who signed in>" }` and hands the answer's `token` to the
  SDK with who the user is (`Graine.identify(…, identityToken = …)`, or `Graine.setIdentityToken` once it arrives).
  `Graine.identityTokenRefresher` asks again whenever the SDK needs a fresh one (the token lasts ten minutes).
- **The demo sign-in server is Graine's, for Graine's demo workspace only.** Graine enables it for its own demo
  workspace; any other workspace's publishable key is refused. It signs with a private key that never leaves Graine's
  server, and the JWKS below publishes its public half.
- **It is a stand-in, never a proof.** It signs for whatever user id it is given — that is what makes it a demo. Your
  own app proves its users with **its own sign-in**: an ID token your identity provider signs only for the person who
  signed in, checked against that provider's JWKS URL — or, recommended for sensitive data, with **server tokens** your
  own server mints for the user its sign-in proved. See
  [Secure your agent](https://www.graine.ai/docs/in-app/secure-your-agent).
- The demo agents' **Verified users** — on each agent: Agent → Embed & Widgets → Install → Your app and your users →
  **Prove who the user is** → **Sign-in token (JWT)**:

  | Field | Value |
  |---|---|
  | JWKS URL | `https://www.graine.ai/api/demo/idp/jwks` |
  | Issuer | `https://www.graine.ai/api/demo/idp` |
  | Audience | `graine-demo` |
  | User ID claim | `sub` |

  **Test a token** with one from the endpoint, then **Report only**, then **Enforce**. **Profile → Security** shows
  **User: verified** for each agent that checks it.

## 5. Server tokens

The strongest mode, set per agent under **Server tokens**: once enforced, every request needs a short-lived token for
that agent and the signed-in user, minted by your server with your secret API key (`POST /v2/client-tokens`). The demo
apps never carry a secret: **Profile → Server token (demo)** picks a stand-in — Graine's demo token endpoint (Graine's
demo agents only, every agent of the app), a minter you run on your own computer with your own key, or a token you paste.
Details: [`SETUP.md`](SETUP.md), section 9, and `kmp/README.md` → *Server tokens*. Profile → Security shows **Server
token: ok** for each agent that gets one; a refused token shows its code (`token_required`, `token_invalid`,
`token_expired`, `token_scope`).

## Check your set-up

- **Nothing connects, and Profile → Security's last refusal is `app_not_allowed`** → *Only my apps* is on and this
  build's package name, bundle ID, Team ID or certificate is not under that agent's Your apps (a debug build is signed
  with your computer's debug key: add its fingerprint).
- **Chat says it couldn't connect and Profile → Security says *not verified — debug token not registered*** →
  *Require a verified app* is on: register this install's debug token on that agent, or turn the switch off while you
  test.
- ***not verified — debug token expired*** → register the token again, or a new one.
- **App says *not checked*** → the agent does not check app proofs, or its settings have not arrived yet.
- **One agent says *verified* and another *not checked*** → each agent is set up on its own: repeat sections 1–4 on the
  other agent too.
- **User says *not proven — no identity token*** → the identity token did not arrive: check the network, and that the
  agent uses the demo sign-in server's values (section 4).
