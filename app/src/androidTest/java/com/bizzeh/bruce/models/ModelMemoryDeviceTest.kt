package com.bizzeh.bruce.models

import android.app.ActivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.gguf.GgufReader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ModelMemoryDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val memoryInfo = ActivityManager.MemoryInfo().also {
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
    }

    private fun fixtureMetadata(): GgufMetadata {
        val file = File(context.cacheDir, "stories260K.gguf")
        InstrumentationRegistry.getInstrumentation().context.assets.open("stories260K.gguf").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return (GgufReader.read(file) as GgufReadResult.Read).metadata
    }

    @Test
    fun smallModelFitsAndUsableMemoryIsWithinTotal() {
        val check = ModelMemory.check(ModelMemory.estimate(fixtureMetadata(), 2048), memoryInfo)

        assertTrue(check.fits)
        assertTrue(check.usableBytes in 1..memoryInfo.totalMem)
    }

    @Test
    fun modelLargerThanTheDeviceDoesNotFit() {
        val tooLarge = fixtureMetadata().copy(fileSizeBytes = memoryInfo.totalMem)

        assertFalse(ModelMemory.check(ModelMemory.estimate(tooLarge, 2048), memoryInfo).fits)
    }
}
