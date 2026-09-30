package com.bizzeh.bruce.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.BruceApplication
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schedules a task a minute ahead and ends, which stops Bruce; WorkManager must run it, loading the
 * chosen model, and post "Your reply is ready". Needs a model chosen, and charging for a phone model.
 */
@ManualOnly("leaves a scheduled task that runs a model a minute later")
@RunWith(AndroidJUnit4::class)
class ScheduledTaskDeviceTest {
    private val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BruceApplication).container

    private companion object {
        const val SAVE_MS = 3_000L
    }

    @Test
    fun aTaskIsScheduledForAMinuteAhead() = runBlocking {
        container.scheduledTasks.add("Bruce device test: say hello in five words.", System.currentTimeMillis() + 60_000, Repeat.ONCE)
        // WorkManager saves the work on its own thread; the test's end force-stops Bruce.
        Thread.sleep(SAVE_MS)
    }
}
