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

    @Test
    fun theSkillAnswersFromTheIndex() = runBlocking {
        assumeTrue("set up file search first", container.searchModelInstalled())
        container.fileSearch.refreshCounts()
        org.junit.Assert.assertTrue(container.requirementMet(com.bizzeh.bruce.skills.SkillRequirement.FILE_INDEX))
        val outcome = container.skills["search_my_files"]!!.execute(com.bizzeh.bruce.skills.SkillArguments(mapOf("query" to "launch code"))) as com.bizzeh.bruce.skills.SkillOutcome.Done
        org.junit.Assert.assertTrue(outcome.content, outcome.content.startsWith("From Documents/kestrel.pdf:"))
        val none = container.skills["search_my_files"]!!.execute(com.bizzeh.bruce.skills.SkillArguments(mapOf("query" to "Who won the 1966 World Cup?"))) as com.bizzeh.bruce.skills.SkillOutcome.Done
        Log.i("BruceSearchTest", "unrelated: ${none.content.take(40)}")
        Unit
    }
}
