package com.bizzeh.bruce.skills.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One reading from Android's providers with approximate location only. The position is never
 * printed or kept: it is the phone owner's. Grant with
 * `adb shell pm grant com.bizzeh.bruce android.permission.ACCESS_COARSE_LOCATION`, revoke after.
 */
@ManualOnly("reads the phone's location; needs the permission granted by hand")
@RunWith(AndroidJUnit4::class)
class LocationDeviceTest {
    private val source = AndroidLocationSource(InstrumentationRegistry.getInstrumentation().targetContext)

    /**
     * Android gives no location to an app with nothing on screen, so Bruce's screen is open, and the
     * test waits a moment: a request made as the screen opens was refused before Android counted
     * Bruce as in the foreground.
     */
    @Test
    fun anApproximateReadingArrives() {
        androidx.test.core.app.ActivityScenario.launch(com.bizzeh.bruce.MainActivity::class.java).use {
            Thread.sleep(FOREGROUND_SETTLE_MS)
            runBlocking {
                assumeTrue("turn location on first", source.enabled())
                assertFalse(source.precise())
                assertNotNull("no fresh reading, nor a last known one", source.current() ?: source.lastKnown())
            }
        }
    }

    private companion object {
        const val FOREGROUND_SETTLE_MS = 2_000L
    }
}
