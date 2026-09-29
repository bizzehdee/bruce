package com.bizzeh.bruce.skills.files

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.policy.DocumentRef
import com.bizzeh.bruce.policy.GrantKind
import com.bizzeh.bruce.policy.GrantScope
import com.bizzeh.bruce.policy.GrantStore
import com.bizzeh.bruce.policy.PolicyDatabase
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.FolderGuidance
import com.bizzeh.bruce.skills.SkillArguments
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.testing.FakeDocuments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileSkillsTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PolicyDatabase::class.java).build()
    private val documents = FakeDocuments()
    private val grants = GrantStore(database.policy(), documents)
    private val skills = FileSkills(GrantScope(grants, documents), documents, Dispatchers.Unconfined).create().associateBy { it.id }

    private val tree = Uri.parse("content://docs/tree/documents")
    private val root = Uri.parse("$tree#root")
    private val notes = DocumentRef(Uri.parse("content://docs/notes"), isDirectory = true)
    private val todo = DocumentRef(Uri.parse("content://docs/todo"), isDirectory = false)
    private val photo = DocumentRef(Uri.parse("content://docs/photo"), isDirectory = false)

    @Before
    fun setUp() = runBlocking {
        documents.names[tree] = "Documents"
        documents.tree[root] = listOf("todo.txt" to todo, "notes" to notes, "photo.jpg" to photo)
        documents.contents[todo.uri] = "text/plain" to "milk\neggs".toByteArray()
        documents.contents[photo.uri] = "image/jpeg" to byteArrayOf(-1, -40, 0)
        grants.add(tree, GrantKind.FOLDER)
        Unit
    }

    @After
    fun tearDown() = database.close()

    private fun run(id: String, path: String) = runBlocking { skills.getValue(id).execute(SkillArguments(mapOf("path" to path))) }

    private fun failure(outcome: SkillOutcome) = (outcome as SkillOutcome.Failed).code

    @Test
    fun readingIsOffAndChangingAsksByDefault() {
        assertEquals(setOf("list_files", "read_file", "create_file", "write_file", "delete_file"), skills.keys)
        assertEquals(SkillState.DECLINED, skills.getValue("list_files").defaultState)
        assertEquals(SkillState.DECLINED, skills.getValue("read_file").defaultState)
        assertEquals(SkillState.ASK, skills.getValue("create_file").defaultState)
        assertEquals(SkillState.ASK, skills.getValue("write_file").defaultState)
        assertEquals(SkillState.ASK, skills.getValue("delete_file").defaultState)
        assertEquals(listOf("delete_file"), skills.values.filter { it.highRisk }.map { it.id })
    }

    @Test
    fun everySuccessCarriesTheFoldersFollowedInstructions() = runBlocking {
        val asked = mutableListOf<String>()
        val guided = FileSkills(GrantScope(grants, documents), documents, Dispatchers.Unconfined) { grant ->
            asked += grant.name
            FolderGuidance("h", grant.name, "Keep lists short.")
        }.create().associateBy { it.id }
        fun guidance(id: String, vararg arguments: Pair<String, Any>) = (runBlocking { guided.getValue(id).execute(SkillArguments(mapOf(*arguments))) } as SkillOutcome.Done).guidance

        assertEquals("Documents", guidance("list_files", "path" to "Documents")?.folder)
        assertEquals("Keep lists short.", guidance("read_file", "path" to "Documents/todo.txt")?.text)
        assertEquals("h", guidance("create_file", "path" to "Documents/new.txt", "content" to "x")?.id)
        assertEquals("h", guidance("write_file", "path" to "Documents/new.txt", "content" to "y")?.id)
        assertEquals("h", guidance("delete_file", "path" to "Documents/new.txt")?.id)
        assertEquals(5, asked.size)
        assertTrue("failures carry none", guided.getValue("read_file").execute(SkillArguments(mapOf("path" to "Documents/gone.txt"))) is SkillOutcome.Failed)
        assertEquals(5, asked.size)
    }

    @Test
    fun deletingRemovesOneFileInAGrantedFolder() {
        assertEquals(SkillOutcome.Done("Deleted the file."), run("delete_file", "Documents/todo.txt"))
        assertEquals(DenialCode.RESOURCE_NOT_FOUND, failure(run("read_file", "Documents/todo.txt")))
    }

    @Test
    fun deletingNeverRemovesFoldersGrantedFilesOrMissingFiles() = runBlocking {
        assertEquals(DenialCode.INVALID_ARGUMENTS, failure(run("delete_file", "Documents/notes")))
        assertEquals(DenialCode.INVALID_ARGUMENTS, failure(run("delete_file", "Documents")))
        assertEquals(DenialCode.RESOURCE_NOT_FOUND, failure(run("delete_file", "Documents/gone.txt")))
        assertEquals(DenialCode.RESOURCE_OUTSIDE_SCOPE, failure(run("delete_file", "Documents/../todo.txt")))
        val report = Uri.parse("content://docs/report")
        documents.names[report] = "report.txt"
        documents.contents[report] = "text/plain" to "x".toByteArray()
        grants.add(report, GrantKind.FILE)
        assertEquals(DenialCode.RESOURCE_OUTSIDE_SCOPE, failure(run("delete_file", "report.txt")))
        documents.refuseDelete = true
        assertEquals(DenialCode.TOOL_FAILED, failure(run("delete_file", "Documents/todo.txt")))
        assertEquals(SkillOutcome.Done("milk\neggs"), run("read_file", "Documents/todo.txt"))
    }

    private fun write(id: String, path: String, content: String) =
        runBlocking { skills.getValue(id).execute(SkillArguments(mapOf("path" to path, "content" to content))) }

    @Test
    fun creatingMakesANewFileWithTheText() {
        assertEquals(SkillOutcome.Done("Created the file (5 characters)."), write("create_file", "Documents/notes/ideas.txt", "hello"))
        assertEquals(SkillOutcome.Done("hello"), run("read_file", "Documents/notes/ideas.txt"))
    }

    @Test
    fun creatingNeverReplacesAndNeedsAGrantedFolder() {
        assertEquals(DenialCode.INVALID_ARGUMENTS, failure(write("create_file", "Documents/todo.txt", "x")))
        assertEquals(DenialCode.INVALID_ARGUMENTS, failure(write("create_file", "Documents/notes", "x")))
        assertEquals("the granted folder itself exists", DenialCode.INVALID_ARGUMENTS, failure(write("create_file", "Documents", "x")))
        assertEquals(DenialCode.RESOURCE_OUTSIDE_SCOPE, failure(write("create_file", "Documents/missing/new.txt", "x")))
        documents.refuseCreate = true
        assertEquals(DenialCode.TOOL_FAILED, failure(write("create_file", "Documents/new.txt", "x")))
        assertEquals(SkillOutcome.Done("milk\neggs"), run("read_file", "Documents/todo.txt"))
    }

    @Test
    fun writingReplacesTheWholeTextOfAnExistingTextFile() {
        assertEquals(SkillOutcome.Done("Replaced the file's contents (3 characters)."), write("write_file", "Documents/todo.txt", "tea"))
        assertEquals(SkillOutcome.Done("tea"), run("read_file", "Documents/todo.txt"))
    }

    @Test
    fun writingNeverTouchesFoldersMissingFilesOrNonText() {
        assertEquals(DenialCode.RESOURCE_NOT_FOUND, failure(write("write_file", "Documents/new.txt", "x")))
        assertEquals(DenialCode.INVALID_ARGUMENTS, failure(write("write_file", "Documents/notes", "x")))
        assertEquals(DenialCode.TOOL_UNAVAILABLE, failure(write("write_file", "Documents/photo.jpg", "x")))
        documents.contents[todo.uri] = "application/octet-stream" to byteArrayOf(1, 0, 2)
        assertEquals(DenialCode.TOOL_UNAVAILABLE, failure(write("write_file", "Documents/todo.txt", "x")))
        assertEquals(DenialCode.RESOURCE_OUTSIDE_SCOPE, failure(write("write_file", "Documents/../x.txt", "x")))
    }

    @Test
    fun listingShowsFoldersWithASlashInNameOrder() {
        assertEquals(SkillOutcome.Done("notes/\nphoto.jpg\ntodo.txt"), run("list_files", "Documents"))
        assertEquals(SkillOutcome.Done("The folder is empty."), run("list_files", "Documents/notes"))
        assertEquals(DenialCode.INVALID_ARGUMENTS, failure(run("list_files", "Documents/todo.txt")))
        assertEquals(DenialCode.RESOURCE_NOT_FOUND, failure(run("list_files", "Documents/missing")))
        assertEquals(DenialCode.RESOURCE_OUTSIDE_SCOPE, failure(run("list_files", "Pictures")))
    }

    @Test
    fun longListingsAreCut() {
        documents.tree[notes.uri] = (1..FileSkills.MAX_LISTED + 3).map { "f$it" to DocumentRef(Uri.parse("content://docs/f$it"), false) }

        val text = (run("list_files", "Documents/notes") as SkillOutcome.Done).content
        assertTrue(text.endsWith("[3 more not shown]"))
    }

    @Test
    fun readingReturnsPlainTextOnly() {
        assertEquals(SkillOutcome.Done("milk\neggs"), run("read_file", "Documents/todo.txt"))
        assertEquals(DenialCode.TOOL_UNAVAILABLE, failure(run("read_file", "Documents/photo.jpg")))
        assertEquals(DenialCode.INVALID_ARGUMENTS, failure(run("read_file", "Documents/notes")))
        assertEquals(DenialCode.RESOURCE_NOT_FOUND, failure(run("read_file", "Documents/new.txt")))
        assertEquals(DenialCode.RESOURCE_OUTSIDE_SCOPE, failure(run("read_file", "Documents/../secret.txt")))
    }

    @Test
    fun binaryDisguisedAsTextIsRefused() {
        documents.contents[todo.uri] = "application/octet-stream" to byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0)
        assertEquals(DenialCode.TOOL_UNAVAILABLE, failure(run("read_file", "Documents/todo.txt")))
    }

    @Test
    fun longFilesAreCutAndSaySo() {
        documents.contents[todo.uri] = "text/plain" to "é".repeat(FileSkills.MAX_READ_BYTES).toByteArray()

        val text = (run("read_file", "Documents/todo.txt") as SkillOutcome.Done).content
        assertTrue(text.endsWith("[Only the start of this file was read.]"))
        assertEquals(FileSkills.MAX_READ_BYTES / 2, text.substringBefore("\n[").length)
    }

    @Test
    fun plainTextRules() {
        assertTrue(PlainText.allowedType(null))
        assertTrue(PlainText.allowedType("text/markdown"))
        assertTrue(PlainText.allowedType("application/json; charset=utf-8"))
        assertEquals(false, PlainText.allowedType("application/pdf"))
        assertEquals("hi", PlainText.decode("﻿hi".toByteArray()))
        assertEquals("a", PlainText.decode(byteArrayOf(0x61, 0xC3.toByte())))
        assertNull(PlainText.decode(byteArrayOf(0x61, 0xFF.toByte(), 0x62)))
    }
}
