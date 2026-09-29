package com.bizzeh.bruce.policy

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.testing.FakeDocuments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderInstructionsTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PolicyDatabase::class.java).build()
    private val documents = FakeDocuments()
    private val grants = GrantStore(database.policy(), documents)
    private val store = FolderInstructionsStore(database.policy(), documents)

    private val tree = Uri.parse("content://docs/tree/documents")
    private val root = Uri.parse("$tree#root")
    private val agentsMd = DocumentRef(Uri.parse("content://docs/agents-md"), isDirectory = false)
    private val agentsDir = DocumentRef(Uri.parse("content://docs/agents"), isDirectory = true)
    private val style = DocumentRef(Uri.parse("content://docs/style"), isDirectory = false)
    private val skills = DocumentRef(Uri.parse("content://docs/skills"), isDirectory = true)
    private val shopping = DocumentRef(Uri.parse("content://docs/shopping"), isDirectory = false)
    private val notes = DocumentRef(Uri.parse("content://docs/notes"), isDirectory = true)
    private val nestedAgents = DocumentRef(Uri.parse("content://docs/nested"), isDirectory = false)

    private lateinit var grant: Grant

    @Before
    fun setUp() = runBlocking {
        documents.names[tree] = "Documents"
        documents.tree[root] = listOf("AGENTS.md" to agentsMd, ".agents" to agentsDir, "notes" to notes)
        documents.tree[agentsDir.uri] = listOf("style.md" to style, "skills" to skills)
        documents.tree[skills.uri] = listOf("shopping.md" to shopping)
        documents.tree[notes.uri] = listOf("AGENTS.md" to nestedAgents)
        documents.contents[agentsMd.uri] = "text/markdown" to "Keep lists short.".toByteArray()
        documents.contents[style.uri] = "text/markdown" to "British spelling.".toByteArray()
        documents.contents[shopping.uri] = "text/markdown" to "Group by aisle.".toByteArray()
        documents.contents[nestedAgents.uri] = "text/markdown" to "Nested, never read.".toByteArray()
        grant = grants.add(tree, GrantKind.FOLDER)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun onlyTheRootsAgentsMdAndAgentsFolderAreFound() {
        val found = store.read(grant)!!

        assertEquals("Documents", found.folder)
        assertEquals("Keep lists short.", found.agentsMd)
        assertEquals(listOf("Documents/.agents/skills/shopping.md", "Documents/.agents/style.md"), found.agentsFiles)
        assertNull(found.problem)
    }

    @Test
    fun foldersWithoutInstructionsAndGrantedFilesHaveNone() = runBlocking {
        documents.tree[root] = listOf("notes" to notes)
        assertNull(store.read(grant))
        assertEquals(InstructionsChoice.NONE_FOUND, store.choice(null))

        val file = Uri.parse("content://docs/report")
        documents.names[file] = "report.txt"
        assertNull(store.read(grants.add(file, GrantKind.FILE)))
    }

    @Test
    fun instructionsAreUsedOnlyOnceFollowedAndAskAgainWhenTheyChange() = runBlocking {
        val found = store.read(grant)!!
        assertEquals(InstructionsChoice.UNDECIDED, store.choice(found))
        assertNull("undecided instructions are not used", store.guidance(grant))

        store.choose(found, follow = true)
        assertEquals(InstructionsChoice.FOLLOW, store.choice(store.read(grant)))
        val guidance = store.guidance(grant)!!
        assertEquals(found.hash, guidance.id)
        assertTrue(guidance.text.startsWith("From AGENTS.md:\nKeep lists short."))
        assertTrue(guidance.text.contains("Documents/.agents/style.md"))

        documents.contents[style.uri] = "text/markdown" to "American spelling.".toByteArray()
        val changed = store.read(grant)!!
        assertNotEquals(found.hash, changed.hash)
        assertEquals("a change to any file asks again", InstructionsChoice.UNDECIDED, store.choice(changed))
        assertNull(store.guidance(grant))

        store.choose(changed, follow = false)
        assertEquals(InstructionsChoice.IGNORE, store.choice(store.read(grant)))
        assertNull(store.guidance(grant))
    }

    @Test
    fun oversizedOrBinaryInstructionsAreNeverUsed() = runBlocking {
        documents.contents[agentsMd.uri] = "text/markdown" to ByteArray(FolderInstructionsStore.MAX_BYTES + 1) { 'a'.code.toByte() }
        val large = store.read(grant)!!
        assertNull(large.agentsMd)
        assertTrue(large.problem!!.contains("larger than 8 KB"))
        store.choose(large, follow = true)
        assertNull(store.guidance(grant))

        documents.contents[agentsMd.uri] = "application/octet-stream" to byteArrayOf(0, -1, 0, -2)
        assertTrue(store.read(grant)!!.problem!!.contains("not plain text"))
    }

    @Test
    fun revokingAFolderForgetsTheChoice() = runBlocking {
        store.choose(store.read(grant)!!, follow = true)
        assertEquals(setOf(grant.id), store.choices.first().keys)

        grants.revoke(grant)

        assertEquals(emptySet<Long>(), store.choices.first().keys)
    }
}
