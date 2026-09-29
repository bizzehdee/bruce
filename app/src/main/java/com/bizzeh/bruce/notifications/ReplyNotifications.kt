package com.bizzeh.bruce.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bizzeh.bruce.MainActivity
import com.bizzeh.bruce.R

/**
 * The notifications Bruce posts (TASK-048): the quiet one a foreground service needs while a reply
 * is being written, and one when a reply finished while Bruce was not on screen.
 */
class ReplyNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(NotificationChannel(REPLIES, context.getString(R.string.notification_channel_replies), NotificationManager.IMPORTANCE_DEFAULT))
        system.createNotificationChannel(NotificationChannel(WORKING, context.getString(R.string.notification_channel_working), NotificationManager.IMPORTANCE_LOW))
    }

    fun working(): Notification = NotificationCompat.Builder(context, WORKING)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.notification_working))
        .setOngoing(true)
        .setSilent(true)
        .build()

    /** Posts that the reply in [conversationId] is ready; does nothing if Android does not let Bruce notify. */
    fun replyReady(conversationId: Long, reply: String) {
        if (!manager.areNotificationsEnabled()) return
        val open = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_CHAT)
            .putExtra(EXTRA_CONVERSATION, conversationId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, conversationId.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, REPLIES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_reply_ready))
            .setContentText(reply.take(PREVIEW_CHARS))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reply.take(PREVIEW_CHARS)))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(REPLY_ID_BASE + conversationId.toInt(), notification)
        } catch (e: SecurityException) {
            // The permission was withdrawn between the check and the post; the reply is saved either way.
        }
    }

    companion object {
        const val ACTION_OPEN_CHAT = "com.bizzeh.bruce.OPEN_CHAT"
        const val EXTRA_CONVERSATION = "conversation"
        const val WORKING_ID = 1
        private const val REPLY_ID_BASE = 1000
        private const val REPLIES = "replies"
        private const val WORKING = "working"
        private const val PREVIEW_CHARS = 300
    }
}
