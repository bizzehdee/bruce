package com.bizzeh.bruce.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.appContainer
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.ui.theme.BruceTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class RemindersTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, ReminderDatabase::class.java).allowMainThreadQueries().build()

    private class FakeScheduler : ReminderScheduler {
        var exact = true
        val scheduled = mutableMapOf<Long, Long>()
        override fun schedule(id: Long, atMillis: Long) = exact.also { scheduled[id] = atMillis }
        override fun cancel(id: Long) {
            scheduled.remove(id)
        }
        override fun exactAllowed() = exact
    }

    private val scheduler = FakeScheduler()
    private val store = ReminderStore(database.reminders(), scheduler)
    private val london = ZoneId.of("Europe/London")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(london).toInstant().toEpochMilli()
    private val now = at("2026-09-30T10:00")
    private var offered = 0
    private val skill = ReminderSkills(store, inexact = { offered++ }, zone = { london }, clock = { now }, sdk = 34).create().single()

    @After
    fun tearDown() = database.close()

    private fun run(vararg arguments: Pair<String, Any>) = runBlocking { skill.execute(SkillArguments(mapOf(*arguments))) }

    @Test
    fun offByDefaultNeedingNotifications() {
        assertEquals(SkillState.DECLINED, skill.defaultState)
        assertEquals(listOf(android.Manifest.permission.POST_NOTIFICATIONS), skill.androidPermissions)
        assertEquals(emptyList<String>(), ReminderSkills(store, sdk = 32).create().single().androidPermissions)
    }

    @Test
    fun remindersAreSetAtATimeOrInMinutes() = runBlocking {
        assertEquals(SkillOutcome.Done("Reminder set for Wed 30 Sep 2026 15:00."), run("text" to "Call Ada", "at" to "2026-09-30T15:00"))
        assertEquals(SkillOutcome.Done("Reminder set for Wed 30 Sep 2026 10:20."), run("text" to " Tea ", "in_minutes" to 20L))

        assertEquals(listOf("Tea" to at("2026-09-30T10:20"), "Call Ada" to at("2026-09-30T15:00")), store.upcoming.first().map { it.text to it.atMillis })
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(0, offered)
    }

    @Test
    fun inexactRemindersSaySoAndOfferExactTiming() {
        scheduler.exact = false
        val outcome = run("text" to "Tea", "in_minutes" to 5L) as SkillOutcome.Done
        assertTrue(outcome.content.endsWith("Android may deliver it a few minutes late, as exact alarms are not allowed."))
        assertEquals(1, offered)
    }

    @Test
    fun badTimesAreRefused() {
        fun code(vararg arguments: Pair<String, Any>) = (run(*arguments) as SkillOutcome.Failed).code
        assertEquals(DenialCode.INVALID_ARGUMENTS, code("text" to "x"))
        assertEquals(DenialCode.INVALID_ARGUMENTS, code("text" to "x", "at" to "tomorrow"))
        assertEquals(DenialCode.INVALID_ARGUMENTS, code("text" to "x", "at" to "2026-09-30T09:00"))
        assertEquals(DenialCode.INVALID_ARGUMENTS, code("text" to "x", "at" to "2028-09-30T09:00"))
        assertEquals(DenialCode.INVALID_ARGUMENTS, code("text" to " ", "in_minutes" to 5L))
        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun aReminderIsTakenOnceCancelledOrRestored() = runBlocking {
        val (first, _) = store.add("One", now + 60_000)
        val (second, _) = store.add("Two", now + 120_000)

        assertEquals(first, store.take(first.id))
        assertNull("shown once", store.take(first.id))
        scheduler.scheduled.clear()
        store.rescheduleAll()
        assertEquals(mapOf(second.id to second.atMillis), scheduler.scheduled)
        store.cancel(second.id)
        assertTrue(store.upcoming.first().isEmpty())
        assertTrue(scheduler.scheduled.isEmpty())
        store.add("Three", now)
        store.deleteAll()
        assertTrue(store.upcoming.first().isEmpty())
    }

    @Test
    fun theScreenListsRemindersAndCancelsThem() {
        val calls = mutableListOf<String>()
        val actions = object : RemindersActions {
            override fun cancel(reminder: Reminder) { calls += "cancel ${reminder.id}" }
            override fun allowExact() { calls += "exact" }
        }
        compose.setContent { BruceTheme { RemindersScreen(listOf(Reminder(4, "Call Ada", now)), exactAllowed = false, actions = actions) {} } }

        compose.onNodeWithText("Call Ada").assertExists()
        compose.onNodeWithTag("cancelReminder:4").performClick()
        compose.onNodeWithTag("allowExact").performClick()
        assertEquals(listOf("cancel 4", "exact"), calls)
    }

    @Test
    fun scheduledTasksAreListedWithTheirRepeatAndCancelled() {
        val calls = mutableListOf<String>()
        val actions = object : RemindersActions {
            override fun cancel(reminder: Reminder) = Unit
            override fun cancelTask(task: ScheduledTask) { calls += "task ${task.id}" }
            override fun allowExact() = Unit
        }
        compose.setContent { BruceTheme { RemindersScreen(emptyList(), true, actions, listOf(ScheduledTask(3, "Weather", now, Repeat.DAILY))) {} } }

        compose.onNodeWithText("Scheduled tasks").assertExists()
        compose.onNodeWithText("Every day", substring = true).assertExists()
        compose.onNodeWithTag("remindersEmpty").assertDoesNotExist()
        compose.onNodeWithTag("cancelTask:3").performClick()
        assertEquals(listOf("task 3"), calls)
    }

    @Test
    fun theEmptyScreenSaysHowToSetOne() {
        compose.setContent { BruceTheme { RemindersScreen(emptyList(), exactAllowed = true, actions = object : RemindersActions {
            override fun cancel(reminder: Reminder) = Unit
            override fun allowExact() = Unit
        }) {} } }
        compose.onNodeWithTag("remindersEmpty").assertExists()
        compose.onNodeWithTag("allowExact").assertDoesNotExist()
    }

    @Test
    fun alarmsAreSetOnAndroidAndADueReminderIsPosted() = runBlocking {
        val scheduler = AndroidReminderScheduler(context)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse("inexact when not allowed", scheduler.schedule(8, now + 30_000))
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(scheduler.exactAllowed())
        scheduler.cancel(8)
        assertTrue(scheduler.schedule(9, now + 60_000))
        assertEquals(now + 60_000, alarms.nextScheduledAlarm!!.triggerAtTime)
        scheduler.cancel(9)
        assertNull(alarms.nextScheduledAlarm)

        val container = context.appContainer
        val (reminder, _) = container.reminders.add("Stretch", System.currentTimeMillis() + 60_000)
        context.sendBroadcast(Intent(context, ReminderReceiver::class.java).setAction(AndroidReminderScheduler.ACTION_REMIND).putExtra(AndroidReminderScheduler.EXTRA_ID, reminder.id))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val posted = { shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications }
        repeat(100) { if (posted().isEmpty()) Thread.sleep(20) }
        assertEquals("Stretch", posted().single().extras.getCharSequence("android.text").toString())
        assertFalse(container.reminders.upcoming.first().any { it.id == reminder.id })
    }
}
