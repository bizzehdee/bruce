package com.bizzeh.bruce.skills.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.BruceApplication
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The file skills against a real document provider, through the app's own grant of a folder
 * named "Documents" (added by hand in Settings, Permissions). The skills are run directly, as
 * the runtime does once policy has allowed them; policy itself is covered by unit tests.
 */
@ManualOnly("needs a folder named Documents granted in the installed app")
@RunWith(AndroidJUnit4::class)
class FileSkillsDeviceTest {
    private val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BruceApplication).container

    private fun run(id: String, vararg arguments: Pair<String, String>) = runBlocking {
        container.skills[id]!!.execute(SkillArguments(arguments.toMap()))
    }

    @Test
    fun createReadReplaceAndListInAGrantedFolder() = runBlocking {
        assumeTrue("grant a folder named Documents first", "Documents" in container.grantNames())
        val name = "bruce-device-test-${System.currentTimeMillis()}.txt"
        val path = "Documents/$name"

        assertEquals(SkillOutcome.Done("Created the file (5 characters)."), run("create_file", "path" to path, "content" to "first"))
        assertEquals(SkillOutcome.Done("first"), run("read_file", "path" to path))
        assertEquals(SkillOutcome.Done("Replaced the file's contents (2 characters)."), run("write_file", "path" to path, "content" to "ok"))
        assertEquals(SkillOutcome.Done("ok"), run("read_file", "path" to path))
        assertTrue((run("list_files", "path" to "Documents") as SkillOutcome.Done).content.lines().contains(name))
    }
}
