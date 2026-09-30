package com.bizzeh.bruce.search

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.BruceApplication
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Searches the index built on the phone: needs file search set up in Settings and a Documents
 * grant holding the test files from TASK-084 (kestrel.pdf, kestrel-notes.txt).
 */
@ManualOnly("needs file search set up and the Kestrel test files granted")
@RunWith(AndroidJUnit4::class)
class FileSearchDeviceTest {
    private val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BruceApplication).container

    @Test
    fun questionsFindTheRightFile() = runBlocking {
        assumeTrue("set up file search first", container.searchModelInstalled())
        val start = System.currentTimeMillis()
        val code = container.fileSearch.search("What is the launch code for the project?", 1).single()
        Log.i("BruceSearchTest", "first search ${System.currentTimeMillis() - start} ms, score ${code.score}")
        assertEquals("Documents/kestrel.pdf", code.path)
        assertEquals("Documents/kestrel-notes.txt", container.fileSearch.search("Qui dirige le projet ?", 1).single().path)
        container.fileSearch.update()
    }
}
