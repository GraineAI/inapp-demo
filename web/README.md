# Graine Demo — website

The Graine assistant embedded in a website, with everything the website loader can do: the visitor is
signed in and remembered, sees what the assistant remembers and can switch it off or erase it, the
assistant sees the screen and acts on the page, and a call can be booked that rings the visitor's
phone app. Plain HTML, CSS and JavaScript modules: no framework, no build step.

Every domain word comes from a use-case pack in `../usecases/` — the same files the mobile apps use:
**Graine Care** (care coach, the default), **Graine Bank** (account assistant) and **Graine Store**
(shopping assistant). All people and data are fictional.

## Run it

Serve the whole `demo/` folder, so the page can read the packs beside it, and open `/web/`:

```bash
cd demo
npx serve .                    # then open http://localhost:3000/web/
# or
python3 -m http.server 3000    # then open http://localhost:3000/web/
```

Opening `index.html` from disk does not work (browsers do not run modules from `file://`).

**Serving `web/` on its own** (to put it on a static host): copy the packs in first —
`cp -R usecases web/usecases`, then `npx serve web` and open `http://localhost:3000/`. The copy is
git-ignored.

## Connect an agent

1. Create the agent for the use case from `agent-setup/<pack>/` (prompt, components, actions,
   settings). Two settings must match the pack exactly: **Memory → Who is the user? → Custom field**
   is the pack's identifier (`patient_id`, `customer_id` or `shopper_id`), and **Calls & scheduling →
   Users, from your app** is on.
2. In the agent, **Embed & Widgets → Install → Allowed domains**: add `http://localhost:3000`. It is
   saved as `localhost` — the port does not matter — so open the site at `localhost`, not
   `127.0.0.1` (or add `127.0.0.1` too). Add your real host when you deploy.
3. Copy the configuration and fill it in from the same Install tab:

   ```bash
   cp web/config.example.js web/config.js     # git-ignored
   ```

   ```js
   export default {
     baseUrl: "https://www.graine.ai",
     agentId: "…",
     publishableKey: "pk_live_…",
     // One agent per use case, if you set up more than one:
     agents: { "banking-support": { agentId: "…", publishableKey: "pk_live_…" } },
   };
   ```

The publishable key is public by design: it ships in the page and works only from the allowed
domains. Reload the page after changing `config.js`.

**The help agent (optional).** Every pack has a second agent for its Help section — Billing &
appointments help, the Card security desk, the Returns desk. Create it from `agent-setup/<pack>/` (its
own prompt and settings, the same **Who is the user?** field), add this site to its Allowed domains, and
add it to `config.js`:

```js
help: { agentId: "…", publishableKey: "pk_live_…" },
```

Without it, the Help section says how to add one and the page stays on the main agent.

**Without an agent** — to click through every screen offline — point the page at the stand-in loader
the tests use. Nothing leaves the page; its panel shows what the page tells the assistant, runs the
page's actions and "remembers" what you type:

```js
export default { agentId: "offline", publishableKey: "pk_test_offline", loaderUrl: "test/fake-loader.js" };
```

## What each screen shows

| Screen | What it demonstrates | Loader calls (all through `graine.js`) |
|---|---|---|
| Sign in | Pick a use case (remembered in this browser; switching loads that pack's agent) and a person; the memory consent switch | `identify({ <identifier>: id, name, …traits }, { memory })` |
| Home | Memory: on / off, the next booked call, the pack's cards; **Talk to …** opens the assistant | `open()`, `scheduledCalls()` |
| Chat | The chat opens in the assistant's panel; the pack's suggestions copy into it | `open()` |
| The list (Medicines · Cards · Orders) | The visitor's rows with status chips and buttons; the assistant can open a screen, open a row or set a row's status | `defineAction(name, handler)` for every pack action, `setScreen(context)` |
| Scheduled calls (Check-in calls · Callbacks · Delivery calls) | The pack's booking button (`call.bookLabel`, e.g. "Schedule a consultation call") opens the widget's own booking sheet — the day, the time, the visitor's time zone and an optional note, in the agent's design — its "What's it about?" starting with the pack's first reason. The button is on while the loader says this visitor can book; otherwise it stays, disabled, with "Booking needs, in Graine UI: users may book (Calls & scheduling) and Who is the user? set to this pack's ID." After a booking the list is read again. Status chips; cancel with a confirmation. The call rings the **phone app**, never the browser | `bookingAvailable`, `openBooking({ reason })`, `scheduledCalls()`, `cancelScheduledCall(id)` |
| Memory | What the assistant remembers about this person (newest first), the consent switch, erase everything with a confirmation, and the pack's try-it script (with the minute to wait, and what is never kept) | `memories()`, `setMemory(enabled)`, `forgetMemory()` |
| Profile | The person's details, memory, this browser's install ID and loader version, theme, sign out | `identify(null)` |
| Help | The **second agent**: while Help is open the page's assistant is the help agent — its voice, chat and suggestions, and booking with its own button and reason in its own booking sheet; every other section puts the main agent back. The same person stays signed in. Without a help agent in `config.js` it says how to add one | `load()` again with the other agent, which switches the page's assistant |

**Scheduled calls lists both agents' calls**, each with its agent's name. The website loader holds one
agent at a time, so the calls of the agent that is not on the page are read (and cancelled) through the
same public calls route the loader uses, with that agent's key, this browser's install ID and the
signed-in identifier — the only place the page speaks to Graine without the loader.

Every screen reports what is on it with `setScreen` (the same fields on every platform —
`usecases/README.md` → Screen context). Memory text never goes into it, only the count.

The assistant's actions answer in the pack's words, for example `mark_done {item: "metformin"}` →
"Metformin 500 mg: Taken today." and `open_screen {screen: "settings"}` → "There is no screen called
"settings". Screens: home, chat, medicines, calls, memory, profile."

## Notices at the top of the page

`graine.js` is the only file that calls the loader. When the loader cannot do something, it says so on
the page instead of failing quietly:

| Notice | Means |
|---|---|
| The assistant is not configured | `config.js` is missing or has no agent id / key. Everything else works. |
| The assistant is unavailable | The loader did not load from the URL shown (an extension, a network filter, no connection). |
| The assistant did not start | The loader loaded but refused to start: usually the key, or this host is not in Allowed domains. It quotes the loader's reason when it gave one. |
| This page needs a newer Graine loader | The loaded loader lacks a method this page uses — or cannot switch to the help agent — and each one is listed with what it does here. A loader that cannot switch is never asked to: Help stays on the main agent and says so. |

## Memory QA script

Use a key for the agent, memory on in the dashboard and on the sign-in screen. The page identifies
the visitor with the pack's field (`identify({ patient_id: … }, { memory })`), which must be the
agent's **Who is the user?**. At the start of each chat or Talk, what the agent remembers about them
goes into what the model reads, after its prompt; what is said is ready about a minute after the
conversation ends, and contact details, ID and card numbers, codes and medical details are never kept.
The Memory screen shows the pack's own sentences under **Try it**; for Graine Care:

1. Sign in as **Riya Sharma**. Open the chat and tell Asha "My goal is to walk 30 minutes every
   evening, after dinner." and "Please call me for check-ins after 7 pm." End the chat and wait
   about a minute.
2. Reload the page and open the chat: it continues where it was. Press **New conversation** in the
   chat's header — the chat starts fresh, so what Asha knows now comes from memory — and ask "What
   do you remember about me?" — Asha answers with the walking goal and check-ins after 7 pm.
3. Open **Memory** and press **Refresh**: both are listed, as on the dashboard's Agent → Memory →
   **People**.
4. Profile → **Sign out**, sign in as **Arjun Mehta**, ask the same question — Asha does not know.
   Memory shows Arjun's own list, without Riya's.
5. Sign out, sign in as Riya again, press **New conversation** and ask again — Asha knows.
6. Memory → **Erase everything Asha remembers** → **Erase**, or erase Riya on the dashboard's
   **People** tab. The list is empty; a new chat does not know.
7. Switch **Let Asha remember our conversations** off, tell Asha something new, end the chat, wait a
   minute and start again — it is not remembered.

The same steps work for Graine Bank and Graine Store with their **Try it** sentences.

## When something does not work

| What you see | Why |
|---|---|
| No launcher | The page's host is not in Allowed domains, or the key is wrong — see the notice. |
| Memory: "signed in on the most devices allowed" | The person is already bound to as many devices as **Devices per user** allows (the agent-setup kit sets 2: the website and one phone). Sign out on one, raise the limit, or reset their devices in the dashboard. |
| The booking button is off | "Booking needs, in Graine UI: users may book (Calls & scheduling) and Who is the user? set to this pack's ID.": the agent does not take bookings from users (Calls & scheduling → Users, from your app), or nobody is signed in. A notice at the top naming `openBooking()` means the loaded loader has no booking sheet yet. |
| A booking is refused | The booking sheet says why, in one line: too soon or too far for the agent's window, outside its calling hours, or too many calls waiting. |
| A booked call never rings | It rings the phone app, signed in as the same person. With no such phone seen in the last 30 days it ends as "No device". |

## Checks

From `web/` (Node 22; no packages to install):

```bash
node test/check-syntax.mjs           # node --check on every script
node --test test/*.test.mjs          # the rules, the packs and graine.js against fake loaders
node test/headless-check.mjs         # headless Chrome; CHROME_PATH=… for another Chrome, SHOTS_DIR=… for screenshots
```

The headless check needs no agent and no key, and nothing it loads leaves the machine: Chrome resolves
no host but 127.0.0.1, and any other request is failed before it is sent. It loads the site with the
loader blocked, with no `config.js`, with a stand-in for the released loader and with a stand-in that
has every method, and signs in to every pack.

## Files

| File | |
|---|---|
| `index.html`, `styles.css` | The page and its look (light and dark, phone width) |
| `graine.js` | The adapter: every call to the loader |
| `js/app.js` | Start-up, state, navigation, the pack's actions |
| `js/views.js`, `js/dom.js` | The screens, built from the pack |
| `js/logic.js` | Rules with no page attached: screens, rows, actions, times, words |
| `js/packs.js` | Which packs the website offers (`PACK_IDS`) and loading them |
| `config.example.js` | Copy to `config.js` |
| `test/` | Unit tests, the stand-in loaders and the headless check |

Adding a use case: put its file in `usecases/` (see `usecases/README.md`) and its id in
`js/packs.js` → `PACK_IDS`.
