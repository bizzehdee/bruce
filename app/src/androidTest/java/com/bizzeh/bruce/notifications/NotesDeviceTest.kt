package com.bizzeh.bruce.notifications

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.BruceApplication
import com.bizzeh.bruce.testing.ManualOnly
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Posts a note through Android and finds it among the app's active notifications, then clears it. */
@ManualOnly("posts a notification on the phone")
@RunWith(AndroidJUnit4::class)
class NotesDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notifications = (context.applicationContext as BruceApplication).container.replyNotifications
    private val system = context.getSystemService(NotificationManager::class.java)

    @Test
    fun aNoteAppearsOnItsChannel() {
        assumeTrue("allow Bruce's notifications first", system.areNotificationsEnabled())
        assertTrue(notifications.post("Bruce device test", "A note to self"))

        val note = system.activeNotifications.single { it.notification.extras.getCharSequence("android.title") == "Bruce device test" }
        assertTrue(note.notification.channelId == "notes")
        system.cancel(note.id)
    }
}
