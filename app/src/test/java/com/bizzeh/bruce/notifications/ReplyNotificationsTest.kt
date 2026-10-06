package com.bizzeh.bruce.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReplyNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val system = context.getSystemService(NotificationManager::class.java)
    private val notifications = ReplyNotifications(context).also { it.createChannels() }

    @Test
    fun aFinishedReplyIsAnnouncedAndOpensItsChat() {
        notifications.replyReady(42, "It is noon.")

        val posted = shadowOf(system).allNotifications.single()
        assertEquals("It is noon.", posted.extras.getCharSequence("android.text").toString())
        val tap = shadowOf(posted.contentIntent).savedIntent
        assertEquals(ReplyNotifications.ACTION_OPEN_CHAT, tap.action)
        assertEquals(42L, tap.getLongExtra(ReplyNotifications.EXTRA_CONVERSATION, -1))
    }

    @Test
    fun notesArePostedOnTheirOwnChannelAndKeptToFifty() {
        assertTrue(notifications.post("Milk", "Buy milk"))

        val posted = shadowOf(system).allNotifications.single()
        assertEquals("Milk", posted.extras.getCharSequence("android.title").toString())
        assertEquals("notes", posted.channelId)
        repeat(60) { notifications.post("n$it", "t") }
        assertEquals(50, shadowOf(system).allNotifications.size)
        shadowOf(system).setNotificationsEnabled(false)
        assertEquals(false, notifications.post("x", "y"))
    }

    @Test
    fun nothingIsPostedWhenAndroidDoesNotLetBruceNotify() {
        shadowOf(system).setNotificationsEnabled(false)

        notifications.replyReady(42, "It is noon.")

        assertTrue(shadowOf(system).allNotifications.isEmpty())
    }

    @Test
    fun theServiceRunsInTheForeground() {
        val controller = Robolectric.buildService(ReplyService::class.java, Intent(context, ReplyService::class.java)).create().startCommand(0, 1)

        assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
    }
}
