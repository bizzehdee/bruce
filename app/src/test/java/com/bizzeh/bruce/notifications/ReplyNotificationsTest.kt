package com.bizzeh.bruce.notifications

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.appContainer
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
    fun nothingIsPostedWhenAndroidDoesNotLetBruceNotify() {
        shadowOf(system).setNotificationsEnabled(false)

        notifications.replyReady(42, "It is noon.")

        assertTrue(shadowOf(system).allNotifications.isEmpty())
    }

    @Test
    fun theServiceRunsInTheForegroundAndStopsTheTurnWhenAndroidEndsIt() {
        var stopped = 0
        (context as Application).appContainer.stopTurn = { stopped++ }
        val controller = Robolectric.buildService(ReplyService::class.java, Intent(context, ReplyService::class.java)).create().startCommand(0, 1)

        assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        controller.get().onTimeout(1)
        controller.get().onTimeout(1, 0)

        assertEquals(2, stopped)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }
}
