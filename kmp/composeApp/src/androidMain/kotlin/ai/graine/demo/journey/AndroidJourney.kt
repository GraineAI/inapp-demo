package ai.graine.demo.journey

import ai.graine.demo.MainActivity
import ai.graine.demo.R
import ai.graine.inapp.kmp.Graine
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The Journey's files, in the app's files folder: each written whole to a new file, then put in place. */
class AndroidFiles(context: Context) : TextStore {
    private val dir = File(context.filesDir, "journey").apply { mkdirs() }

    override fun read(name: String): String? = runCatching { AtomicFile(File(dir, name)).readFully().toString(Charsets.UTF_8) }.getOrNull()

    override fun write(name: String, text: String) {
        val file = AtomicFile(File(dir, name))
        val out = file.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    override fun delete(name: String) = AtomicFile(File(dir, name)).delete()
}

/**
 * CHECK-IN REMINDERS ON ANDROID: one alarm per reminder (AlarmManager, allowed while idle — a minute or two late at
 * worst, which a check-in tolerates), shown as a notification by [JourneyReminderReceiver]; a tap opens the app with
 * the reminder's id. What is scheduled is kept, so a restart of the phone schedules it again ([JourneyBootReceiver]).
 */
class AndroidReminders(private val context: Context) : ReminderScheduler {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val alarms: AlarmManager? get() = context.getSystemService(AlarmManager::class.java)

    override fun replaceAll(reminders: List<Reminder>) {
        val am = alarms ?: return
        stored(context).forEach { old -> alarm(context, old, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() } }
        reminders.forEach { r -> alarm(context, r, PendingIntent.FLAG_UPDATE_CURRENT)?.let { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, it) } }
        prefs.edit().putString(KEY, JSONArray(reminders.map { it.toJson() }).toString()).apply()
    }

    /** Android 13+ asks for notifications (the SDK's own question: calls ring with it too); older phones only need them on. */
    override suspend fun requestPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 33) runCatching { Graine.calls.requestCallPermissions() }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    companion object {
        private const val PREFS = "graine-journey-reminders"
        private const val KEY = "scheduled"
        const val EXTRA_ID = "ai.graine.demo.journey.CHECK_IN"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_AT = "at"
        const val CHANNEL = "journey_check_ins"

        fun stored(context: Context): List<Reminder> = runCatching {
            val a = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                Reminder(o.getString("id"), o.getLong("at"), o.getString("title"), o.getString("body"))
            }
        }.getOrDefault(emptyList())

        /** After a restart of the phone or an update of the app, the alarms are gone: those still to come, again. */
        fun restore(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val now = System.currentTimeMillis()
            stored(context).filter { it.at > now }.forEach { r ->
                alarm(context, r, PendingIntent.FLAG_UPDATE_CURRENT)?.let { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, it) }
            }
        }

        private fun Reminder.toJson() = JSONObject().put("id", id).put("at", at).put("title", title).put("body", body)

        /** The alarm's broadcast, one per reminder (its id in the data, so each is its own). */
        private fun alarm(context: Context, r: Reminder, flag: Int): PendingIntent? {
            val intent = Intent(context, JourneyReminderReceiver::class.java)
                .setData(Uri.parse("graine-journey://check-in/${r.id}"))
                .putExtra(EXTRA_ID, r.id).putExtra(EXTRA_TITLE, r.title).putExtra(EXTRA_BODY, r.body).putExtra(EXTRA_AT, r.at)
            return PendingIntent.getBroadcast(context, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
        }

        internal fun show(context: Context, intent: Intent) {
            val id = intent.getStringExtra(EXTRA_ID) ?: return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Check-ins", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                context,
                id.hashCode(),
                Intent(context, MainActivity::class.java).putExtra(EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_check_in)
                .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
                .setContentText(intent.getStringExtra(EXTRA_BODY))
                .setWhen(intent.getLongExtra(EXTRA_AT, System.currentTimeMillis()))
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) nm.notify(id.hashCode(), notification)
        }
    }
}

/** A check-in's alarm went off: its notification. */
class JourneyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AndroidReminders.show(context, intent)
}

/** The phone restarted, or the app was updated: the reminders still to come, scheduled again. */
class JourneyBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) AndroidReminders.restore(context)
    }
}

/** The share sheet, over whatever is in front: the plan as text, the user's data as a JSON file. */
class AndroidShare(private val context: Context) : JourneyShare {
    override fun shareText(title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun shareFile(fileName: String, text: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.journeyfiles", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(fileName, uri)
        context.startActivity(Intent.createChooser(send, fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
