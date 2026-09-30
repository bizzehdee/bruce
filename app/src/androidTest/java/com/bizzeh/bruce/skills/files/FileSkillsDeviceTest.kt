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
 * named "Documents" (added by hand in Settings, Permissions), holding a folder named
 * "bruce-move-test" for the move (made by hand, for example with adb). The file it makes is deleted at the end. The skills are run directly, as
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
    fun createReadReplaceListAndDeleteInAGrantedFolder() = runBlocking {
        assumeTrue("grant a folder named Documents first", "Documents" in container.grantNames())
        val name = "bruce-device-test-${System.currentTimeMillis()}.txt"
        val path = "Documents/$name"

        assertEquals(SkillOutcome.Done("Created the file (5 characters)."), run("create_file", "path" to path, "content" to "first"))
        assertEquals(SkillOutcome.Done("first"), run("read_file", "path" to path))
        assertEquals(SkillOutcome.Done("Replaced the file's contents (2 characters)."), run("write_file", "path" to path, "content" to "ok"))
        assertEquals(SkillOutcome.Done("ok"), run("read_file", "path" to path))
        assertTrue((run("list_files", "path" to "Documents") as SkillOutcome.Done).content.lines().contains(name))
        assertEquals(SkillOutcome.Done("Deleted the file."), run("delete_file", "path" to path))
        assertTrue(name !in (run("list_files", "path" to "Documents") as SkillOutcome.Done).content.lines())
    }

    @Test
    fun renameAndMoveInAGrantedFolder() = runBlocking {
        assumeTrue("grant a folder named Documents first", "Documents" in container.grantNames())
        assumeTrue("make Documents/bruce-move-test first", "bruce-move-test/" in (run("list_files", "path" to "Documents") as SkillOutcome.Done).content.lines())
        val stamp = System.currentTimeMillis()
        val name = "bruce-rename-$stamp.txt"
        val renamed = "bruce-renamed-$stamp.txt"

        run("create_file", "path" to "Documents/$name", "content" to "moving")
        assertEquals(SkillOutcome.Done("Renamed the file."), run("rename_file", "path" to "Documents/$name", "new_name" to renamed))
        assertEquals(SkillOutcome.Done("Moved the file."), run("move_file", "path" to "Documents/$renamed", "destination" to "Documents/bruce-move-test"))
        assertEquals(SkillOutcome.Done("moving"), run("read_file", "path" to "Documents/bruce-move-test/$renamed"))
        val top = (run("list_files", "path" to "Documents") as SkillOutcome.Done).content.lines()
        assertTrue(name !in top && renamed !in top)
        assertEquals(SkillOutcome.Done("Deleted the file."), run("delete_file", "path" to "Documents/bruce-move-test/$renamed"))
    }
}
