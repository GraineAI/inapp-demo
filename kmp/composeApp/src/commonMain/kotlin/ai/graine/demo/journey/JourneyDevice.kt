package ai.graine.demo.journey

/**
 * THE PHONE'S PART OF THE JOURNEY — what differs on Android and iOS: local notifications for check-ins, and the share
 * sheet. Graine sends no chat message on a schedule, so check-in reminders are the app's own, from the plan.
 */
interface ReminderScheduler {
    /** These reminders and no others: every one scheduled before that is not in [reminders] is cancelled. */
    fun replaceAll(reminders: List<Reminder>)

    /** Ask to show notifications (iOS; Android 13+). True when they may be shown. */
    suspend fun requestPermission(): Boolean
}

/** The share sheet: the plan as text, the user's data as a file. */
interface JourneyShare {
    fun shareText(title: String, text: String)
    fun shareFile(fileName: String, text: String)
}

/**
 * A REMINDER THE USER TAPPED, on its way to the journey. The platform hands it over as soon as the tap arrives —
 * at a cold start that can be before the app's state exists — and the journey takes it once it is listening.
 */
object ReminderTaps {
    private var pending: String? = null
    private var listener: ((String) -> Unit)? = null

    /** From the platform: the reminder's id (CheckInSchedule.id). Main thread. */
    fun deliver(id: String) {
        val l = listener
        if (l != null) l(id) else pending = id
    }

    /** From the journey: taps from now on, and the one that came before it listened. */
    fun listen(onTap: (String) -> Unit) {
        listener = onTap
        pending?.let { pending = null; onTap(it) }
    }

    fun stop() {
        listener = null
    }
}
