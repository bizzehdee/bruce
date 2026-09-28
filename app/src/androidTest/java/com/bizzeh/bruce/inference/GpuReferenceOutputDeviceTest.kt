package com.bizzeh.bruce.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

/** Whether this phone's GPU computes correctly. On the Pixel 11 (PowerVR) Vulkan gave wrong text and a GPU run froze the phone. */
@ManualOnly("can freeze a phone whose GPU driver is faulty")
@RunWith(AndroidJUnit4::class)
class GpuReferenceOutputDeviceTest {
    private val nativeThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val engine = deviceEngine(nativeThread)

    @After
    fun tearDown() = runTest {
        engine.unloadModel()
        nativeThread.close()
    }

    @Test
    fun vulkanGreedyOutputMatchesLlamaCpp() {
        assertEquals(STORIES_REFERENCE, engine.greedyStoryText(BackendPreference.VULKAN, threads = 1).take(STORIES_REFERENCE.length))
    }
}
