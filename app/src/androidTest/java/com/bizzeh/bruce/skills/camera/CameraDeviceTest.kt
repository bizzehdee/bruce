package com.bizzeh.bruce.skills.camera

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.BruceApplication
import com.bizzeh.bruce.MainActivity
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The camera skill with Bruce on screen: the camera app opens, the test presses Back without
 * taking a picture (nothing of the owner's surroundings is photographed), and the skill reports it.
 */
@ManualOnly("opens the phone's camera app")
@RunWith(AndroidJUnit4::class)
class CameraDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val container = (instrumentation.targetContext.applicationContext as BruceApplication).container

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()

    @Test
    fun theCameraOpensAndClosingItGivesNoPhoto() {
        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(SETTLE_MS)
            runBlocking {
                val outcome = async(kotlinx.coroutines.Dispatchers.IO) { container.skills["take_photo"]!!.execute(SkillArguments(emptyMap())) }
                Thread.sleep(CAMERA_OPEN_MS)
                val top = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities").let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
                    .lines().first { "mResumedActivity" in it }
                assertTrue("the camera app is on screen: $top", "com.bizzeh.bruce/" !in top)
                shell("input keyevent 4")
                assertEquals(SkillOutcome.Done("The user closed the camera without taking a photo."), outcome.await())
            }
        }
    }

    private companion object {
        const val SETTLE_MS = 2_000L
        const val CAMERA_OPEN_MS = 4_000L
    }
}
