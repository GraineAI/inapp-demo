# Settings — Card security desk (Graine Bank), the Help screen's agent

The second agent of the Graine Bank demo: the Help screen's chat, voice and calls use it, while Home and
Chat use Kabir (`settings.md`). Create it in the same workspace, the same way, with these differences.
Values in **bold** must match the apps (they read them from `usecases/banking-support.json` → `agents.help`).

| Where | Setting | Value |
|---|---|---|
| Agents → New agent | Name | **Card security desk** |
| Agent → Prompt | Prompt | everything below the line in `help-prompt.md` |
| Agent → Prompt | Welcome message | Hello {name}, this is Card security desk. How can I help? |
| Agent → Custom Variables | Defaults | the same as Kabir's, including `who_called` = `customer` and an empty `call_reason` |
| Agent → Calling Window | Hours | on, 08:00–21:00 every day |
| Embed & Widgets → Install & domains → Allowed domains | | **`app://`**, `localhost` (the website demo) and your website's domain |
| Embed & Widgets | Publishable key | the apps' **help** key (see the root README, Set up) |
| Embed & Widgets → What visitors get | | **Call and chat** |
| Embed & Widgets → App actions → Import from JSON | | the same **`actions.json`** as Kabir, then **Save** |
| Embed & Widgets → Components | | optional: the calendar-slots component from `components.json`, for offering call times |
| Embed & Widgets → Appearance → Chat window → Suggestions | | optional: “I don't recognise a payment”, “My card was stolen”, “Is this message from you?”, “Book a call with the desk” |
| Memory → Settings → Remembers users across chats and calls | | **On** |
| Memory → Settings → Who is the user? | | **Custom field → `customer_id`** — the same field as Kabir, so one sign-in identifies the user for both |
| Memory → Settings → Devices per user | | 2 |
| Memory → Settings → Shared with | | This agent only (each agent keeps what it learned). Choose *Agents that share memory* on both agents to let them share it |
| Memory → Settings → Keep memories for | | 365 days |
| Memory → Settings → What to remember — and what never to | | the text below, as it is |
| Calls & scheduling → Who may book a call | | The agent, during a conversation: **On** · Users, from your app: **On** |
| Calls & scheduling → limits | | 3 upcoming calls per user · 14 days ahead · 5 minutes' notice |
| Calls & scheduling → Name on the incoming call | | **Graine Bank · Card security** |
| Calls & scheduling → ringing | | 45 s · ring again after 10 min · 3 rings per user per day · quiet hours 21:00–08:00 |

What to remember — and what never to, for this agent:

```text
Remember whether the customer often travels abroad (and when), how they prefer to be contacted, and
the cards or payments they reported and whether each case is still open.
Never remember card numbers, PINs, one-time passwords, passwords, CVVs, balances or amounts.
```

The **Memory** section of `help-prompt.md` is what makes the agent use what it remembers.

In-app calling credentials are the workspace's (Workspace settings → In-app calling): one upload serves both
agents.
