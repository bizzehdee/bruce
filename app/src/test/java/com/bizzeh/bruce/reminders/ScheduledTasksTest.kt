package com.bizzeh.bruce.reminders

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class ScheduledTasksTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, ReminderDatabase::class.java).allowMainThreadQueries().build()

    private class FakeScheduler : TaskScheduler {
        val queued = mutableMapOf<Long, Pair<Long, Boolean>>()
        override fun enqueue(id: Long, atMillis: Long, needsCharging: Boolean) {
            queued[id] = atMillis to needsCharging
        }
        override fun cancel(id: Long) {
            queued.remove(id)
        }
    }

    private val scheduler = FakeScheduler()
    private val london = ZoneId.of("Europe/London")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(london).toInstant().toEpochMilli()
    private var now = at("2026-09-30T10:00")
    private var phone = true
    private var charging = false
    private val prompts = mutableListOf<String>()
    private val announced = mutableListOf<TaskOutcome>()
    private val tasks = ScheduledTasks(
        database.tasks(), scheduler,
        run = { prompts += it; TaskOutcome.Answered(1, "Done: $it") },
        usesPhoneModel = { phone }, charging = { charging }, announce = { announced += it }, clock = { now },
    )

    @After
    fun tearDown() = database.close()

    @Test
    fun aPhoneModelWaitsForChargingAndAServerDoesNot() = runBlocking {
        val onPhone = tasks.add("Summarise the news", at("2026-09-30T18:00"), Repeat.ONCE)
        phone = false
        val onServer = tasks.add("Plan dinner", at("2026-09-30T17:00"), Repeat.ONCE)

        assertEquals(at("2026-09-30T18:00") to true, scheduler.queued[onPhone.id])
        assertEquals(at("2026-09-30T17:00") to false, scheduler.queued[onServer.id])
        assertEquals(listOf("Plan dinner", "Summarise the news"), tasks.upcoming.first().map { it.prompt })
    }

    @Test
    fun aOnceTaskRunsAndIsGoneButNotBeforeChargingOnThePhone() = runBlocking {
        val task = tasks.add("Summarise", at("2026-09-30T11:00"), Repeat.ONCE)
        now = at("2026-09-30T11:00")

        tasks.runDue(task.id)
        assertTrue("not charging: waits", prompts.isEmpty())
        assertEquals(now to true, scheduler.queued[task.id])

        charging = true
        tasks.runDue(task.id)
        assertEquals(listOf("Summarise"), prompts)
        assertEquals(listOf<TaskOutcome>(TaskOutcome.Answered(1, "Done: Summarise")), announced)
        assertTrue(tasks.upcoming.first().isEmpty())
        tasks.runDue(task.id)
        assertEquals("a removed task does nothing", 1, prompts.size)
    }

    @Test
    fun aRepeatingTaskMovesToItsNextTime() = runBlocking {
        charging = true
        val task = tasks.add("Weather", at("2026-09-30T07:00"), Repeat.DAILY)
        now = at("2026-10-02T09:00")

        tasks.runDue(task.id)

        assertEquals(at("2026-10-03T07:00"), tasks.upcoming.first().single().nextAt)
        assertEquals(at("2026-10-03T07:00") to true, scheduler.queued[task.id])
    }

    @Test
    fun everyTaskCanBeQueuedAgain() = runBlocking {
        val a = tasks.add("A", now + 1000, Repeat.WEEKLY)
        scheduler.queued.clear()
        phone = false
        tasks.rescheduleAll()
        assertEquals(mapOf(a.id to ((now + 1000) to false)), scheduler.queued)
    }

    @Test
    fun cancellingAndClearingRemoveTheWork() = runBlocking {
        val a = tasks.add("A", now + 1000, Repeat.WEEKLY)
        tasks.add("B", now + 2000, Repeat.ONCE)
        tasks.cancel(a.id)
        assertEquals(listOf("B"), tasks.upcoming.first().map { it.prompt })
        tasks.deleteAll()
        assertTrue(tasks.upcoming.first().isEmpty())
        assertTrue(scheduler.queued.isEmpty())
    }

    @Test
    fun theSkillSchedulesWithApprovalEveryTime() = runBlocking {
        val skill = ReminderSkills(ReminderStore(database.reminders(), object : ReminderScheduler {
            override fun schedule(id: Long, atMillis: Long) = true
            override fun cancel(id: Long) = Unit
            override fun exactAllowed() = true
        }), tasks, zone = { london }, clock = { now }, sdk = 34).create().single { it.id == "schedule_task" }
        assertTrue(skill.confirmEveryUse)

        val done = skill.execute(SkillArguments(mapOf("prompt" to "Weather", "at" to "2026-10-01T07:00", "repeat" to "daily")))
        assertEquals(SkillOutcome.Done("Scheduled for Thu 1 Oct 2026 07:00, then every day. With the phone's own model it runs once the phone is charging."), done)
        assertEquals(Repeat.DAILY, tasks.upcoming.first().single().repeat)
        assertEquals(DenialCode.INVALID_ARGUMENTS, (skill.execute(SkillArguments(mapOf("prompt" to "x", "at" to "2026-09-29T07:00"))) as SkillOutcome.Failed).code)
        assertEquals(DenialCode.INVALID_ARGUMENTS, (skill.execute(SkillArguments(mapOf("prompt" to " ", "in_minutes" to 5L))) as SkillOutcome.Failed).code)
    }

    @Test
    fun workManagerHoldsOneJobPerTaskWithItsCondition() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val work = WorkManager.getInstance(context)
        val scheduler = WorkManagerTasks(context) { now }

        scheduler.enqueue(7, now + 60_000, needsCharging = true)
        scheduler.enqueue(7, now + 120_000, needsCharging = true)
        val infos = work.getWorkInfosForUniqueWork("scheduled-task-7").get()
        assertEquals(1, infos.count { it.state == WorkInfo.State.ENQUEUED })
        assertTrue(infos.single { it.state == WorkInfo.State.ENQUEUED }.constraints.requiresCharging())

        scheduler.cancel(7)
        assertTrue(work.getWorkInfosForUniqueWork("scheduled-task-7").get().all { it.state == WorkInfo.State.CANCELLED })
    }
}
