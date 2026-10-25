# Recreate a demo agent in 10 minutes

One folder per use case — pick the one closest to your business; the steps are the same for each:

| Folder | Agent | The person is identified by |
|---|---|---|
| `health-coach/` (default) | Asha, care coach | `patient_id` |
| `banking-support/` | Kabir, account assistant | `customer_id` |
| `retail-shopping/` | Tara, shopping assistant | `shopper_id` |

Each folder has `prompt.md` (the agent's instructions), `actions.json` (what the agent may do in the app),
`components.json` (the cards it can show) and `settings.md` (every setting and its value) for the **main**
agent, and `help-prompt.md` and `help-settings.md` for the **help** agent — the second agent the apps use on
their Help screen. The apps read
the matching file in `../usecases/`; the names here must stay identical to it.

## 1. Create the agent — 2 minutes
1. Dashboard → **Agents → New agent**. Name it exactly as `settings.md` says (for example **Asha**).
2. **Prompt**: paste everything below the line in `prompt.md`. Set the **Welcome message** from
   `settings.md`.
3. **Custom Variables**: add the defaults listed in `settings.md` (including `who_called` = `customer`
   and an empty `call_reason`, which the platform fills in on calls).
4. Pick any voice in **Voice Settings**. Under **Calling Window**, allow 08:00–21:00.

## 2. Embed & Widgets — 3 minutes
1. **Agent → Embed & Widgets**. If the agent has no in-app set-up yet, create it.
2. **Install & domains → Allowed domains**: add `app://`. Without it every request from a mobile app is
   refused. For the website demo add `localhost` (it runs on your machine), and your site's domain when
   you host it.
3. **What visitors get**: **Call and chat**.
4. Copy the **publishable key** (`pk_live_…`). You will paste it into the apps.
5. **App actions → Import from JSON**: choose `actions.json`, then **Save**. Saving is what gives the
   agent its actions; an import that is not saved changes nothing.
6. **Components**: create the three components in `components.json`. For each: *New component* → the
   template named by `kind` (Quick actions, Product list, Calendar slots, Lead form) → fill in the title,
   items or slots exactly as in the file → **Tool name** = `toolName` → **When they tap** as in `onSelect`
   (*Open a screen* with the path, or leave *Tell the agent*) → **Look & feel** as in `style` → Save.
   Make sure all three are selected for this agent. The prompt already names them.

## 3. Memory — 1 minute
**Agent → Memory → Settings** (the same page as Embed & Widgets → **Users & memory**):
- **Remembers users across chats and calls**: on.
- **Who is the user?** → **Custom field** → the field from `settings.md` (`patient_id`, `customer_id` or
  `shopper_id`). The apps identify the user with that field and with the same id under `user_id`, so **User ID**
  works too; with any other field nobody is recognised and nothing is remembered. Keep it once real users are
  on it: a new field starts new people. Phone calls are recognised by the caller's number, so the app user and
  the caller are one person only with **Phone number** here (and an app that identifies people by their number).
- **Devices per user**: 2 — so the same person can use the website and a phone (or two phones); a third
  device still works, without that person's memory. **Shared with**: this agent only. **Keep memories for**:
  365 days.
- **What to remember — and what never to**: paste the text block from `settings.md` (at most 1,000
  characters; it replaces the default rules). Contact details, ID and card numbers, passwords and codes,
  dates of birth, and medical conditions, prescriptions and diagnoses are removed before anything is saved,
  whatever the rules say.
- **The prompt** from step 1 already has the lines that make the agent use what it remembers: its **Memory**
  section (the preferred language, a fact confirmed instead of asked again, a follow-up on what was agreed).
  Memory gives the agent facts, placed after its prompt at the start of each chat, Talk or call; the prompt
  decides what it does with them. For a flow agent, put those lines in the flow's **Agent information**
  (every node reads it, and the memory goes there too), and node-specific ones in that node.

## 4. Calls & scheduling — 1 minute
**Agent → Calls & scheduling**: the agent may book **on**, users may book **on** (the app's booking sheet —
for example *Schedule a consultation call* — needs it), limits 3 · 14 · 5,
**Name on the incoming call** exactly as in `settings.md` (for example **Asha · Graine Care**), ring
45 s, ring again after 10 min, 3 rings per day, **quiet hours 21:00–08:00**.

## 5. In-app calling — 2 minutes (only for calls Graine rings)
**Workspace settings → In-app calling**: upload your APNs key (`.p8`, Team ID, Key ID, environment
Sandbox for development builds) for the iOS bundle ID you build, and your Firebase service-account JSON
for the Android package. Wait for **verified**. Chat, voice and calls the user places work without this.

## 6. The help agent — 3 minutes (optional)
Each app talks to two agents: the main one on Home and Chat, and a help desk on the Help screen (for example
*Billing & appointments help* beside Asha). Create the second agent in the same workspace by repeating steps
1–4 with `help-prompt.md` and `help-settings.md`: the same identifier field (one sign-in identifies the user
for both), the same `actions.json`, its own name on incoming calls. Copy its publishable key into the apps'
**help** key. Without one, the Help screen says how to add it and everything else works.

## 7. Run an app — 1 minute
Put the publishable key into the app's configuration and start it (root `README.md`, *Run*). Sign in as
one of the fictional people, then run the QA checklist in the root README.

## Check your set-up
- **The app says the key was refused** → Allowed domains lacks `app://`, or the key is from another agent.
- **The agent says it cannot do something the app shows** → App actions were imported but not saved.
- **Memory stays empty** → Memory is off, *Who is the user?* is neither the pack's field nor User ID, or the
  person turned memory off on the sign-in screen. What is said is ready about a minute after the conversation
  ends: ask after **New conversation**, not in the same chat.
- **The agent remembers a fact but does not act on it** → the prompt's **Memory** section is missing.
- **Booking says "does not take bookings from the app"** → *Users, from your app* is off.
- **A booked call never rings** → In-app calling is not verified for this app's bundle ID / package, or
  the phone is a simulator (rings need a real device).
