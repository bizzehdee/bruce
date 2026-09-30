package com.bizzeh.bruce.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.BruceApplication
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sets a reminder a minute ahead and ends, which stops Bruce; the alarm must still post it. Check
 * with `adb shell dumpsys notification` for "Bruce device test reminder" after a minute.
 */
@ManualOnly("leaves a reminder that posts a notification a minute later")
@RunWith(AndroidJUnit4::class)
class ReminderDeviceTest {
    private val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BruceApplication).container

    @Test
    fun aReminderIsSetForAMinuteAhead() = runBlocking {
        container.reminders.add("Bruce device test reminder", System.currentTimeMillis() + 60_000)
        Unit
    }
}
