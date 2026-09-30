package com.bizzeh.bruce.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bizzeh.bruce.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Alarms through AlarmManager: exact while idle where the user allows exact alarms (Android 12+
 * asks them), otherwise inexact while idle, which can be a few minutes late (owner, 2026-09-30).
 */
class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    override fun schedule(id: Long, atMillis: Long): Boolean {
        val exact = exactAllowed()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent(id))
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent(id))
        }
        return exact
    }

    override fun cancel(id: Long) = alarms.cancel(intent(id))

    private fun intent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_REMIND = "com.bizzeh.bruce.REMIND"
        const val EXTRA_ID = "reminder"
    }
}

/** Shows a reminder when its alarm goes off. Not exported: only Bruce's own alarms reach it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AndroidReminderScheduler.ACTION_REMIND) return
        val id = intent.getLongExtra(AndroidReminderScheduler.EXTRA_ID, -1)
        val done = goAsync()
        val container = context.appContainer
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.reminders.take(id)?.let { container.replyNotifications.reminder(it.text) }
            } finally {
                done.finish()
            }
        }
    }
}

/** Sets reminders' alarms and scheduled tasks' work again after a reboot or an update of Bruce. */
class RemindersRestorer : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val done = goAsync()
        val container = context.appContainer
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.reminders.rescheduleAll()
                container.scheduledTasks.rescheduleAll()
            } finally {
                done.finish()
            }
        }
    }
}
