# Settings — Billing & appointments help (Graine Care), the Help screen's agent

The second agent of the Graine Care demo: the Help screen's chat, voice and calls use it, while Home and
Chat use Asha (`settings.md`). Create it in the same workspace, the same way, with these differences.
Values in **bold** must match the apps (they read them from `usecases/health-coach.json` → `agents.help`).

| Where | Setting | Value |
|---|---|---|
| Agents → New agent | Name | **Billing & appointments help** |
| Agent → Prompt | Prompt | everything below the line in `help-prompt.md` |
| Agent → Prompt | Welcome message | Hello {name}, this is Billing & appointments help. How can I help? |
| Agent → Custom Variables | Defaults | the same as Asha's, including `who_called` = `customer` and an empty `call_reason` |
| Agent → Calling Window | Hours | on, 08:00–21:00 every day |
| Embed & Widgets → Install & domains → Allowed domains | | **`app://`**, `localhost` (the website demo) and your website's domain |
| Embed & Widgets | Publishable key | the apps' **help** key (see the root README, Set up) |
| Embed & Widgets → What visitors get | | **Call and chat** |
| Embed & Widgets → App actions → Import from JSON | | the same **`actions.json`** as Asha, then **Save** |
| Embed & Widgets → Components | | optional: the calendar-slots component from `components.json`, for offering call times |
| Embed & Widgets → Appearance → Chat window → Suggestions | | optional: “Why was I charged twice?”, “Move my appointment”, “Is my next visit covered?”, “Book a call with billing” |
| Memory → Settings → Remembers users across chats and calls | | **On** |
| Memory → Settings → Who is the user? | | **Custom field → `patient_id`** — the same field as Asha, so one sign-in identifies the user for both |
| Memory → Settings → Devices per user | | 2 |
| Memory → Settings → Shared with | | This agent only (each agent keeps what it learned). Choose *Agents that share memory* on both agents to let them share it |
| Memory → Settings → Keep memories for | | 365 days |
| Memory → Settings → What to remember — and what never to | | the text below, as it is |
| Calls & scheduling → Who may book a call | | The agent, during a conversation: **On** · Users, from your app: **On** |
| Calls & scheduling → limits | | 3 upcoming calls per user · 14 days ahead · 5 minutes' notice |
| Calls & scheduling → Name on the incoming call | | **Graine Care · Billing** |
| Calls & scheduling → ringing | | 45 s · ring again after 10 min · 3 rings per user per day · quiet hours 21:00–08:00 |

What to remember — and what never to, for this agent:

```text
Remember which days and times suit the patient for appointments, how they prefer to be contacted,
and the billing or appointment questions they raised and whether they were resolved.
Never remember amounts, card or bank details, insurance policy numbers or anything about their health.
```

The **Memory** section of `help-prompt.md` is what makes the agent use what it remembers.

In-app calling credentials are the workspace's (Workspace settings → In-app calling): one upload serves both
agents.
