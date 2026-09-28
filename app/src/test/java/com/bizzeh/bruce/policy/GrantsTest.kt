package com.bizzeh.bruce.policy

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.ResourceScope
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRegistry
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.skills.ToolOutput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GrantsTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PolicyDatabase::class.java).build()
    private val dao = database.policy()

    /** A document provider in memory: each folder URI maps to its children. */
    private class FakeDocuments : DocumentAccess {
        val held = mutableSetOf<Uri>()
        val released = mutableListOf<Uri>()
        val names = mutableMapOf<Uri, String>()
        val tree = mutableMapOf<Uri, List<Pair<String, DocumentRef>>>()
        var refuseTake = false

        override fun take(uri: Uri) {
            if (refuseTake) throw SecurityException("not persistable")
            held += uri
        }

        override fun release(uri: Uri) {
            if (!held.remove(uri)) throw SecurityException("not held")
            released += uri
        }

        override fun persisted(): Set<Uri> = held.toSet()
        override fun displayName(uri: Uri, kind: GrantKind): String? = names[uri]
        override fun root(tree: Uri) = DocumentRef(Uri.parse("$tree#root"), isDirectory = true)
        override fun children(tree: Uri, folder: DocumentRef) = this.tree[folder.uri].orEmpty()
    }

    private val documents = FakeDocuments()
    private var now = 1_000L
    private val store = GrantStore(dao, documents) { now }
    private val scope = GrantScope(store, documents)

    private val documentsTree = Uri.parse("content://docs/tree/documents")
    private val root = DocumentRef(Uri.parse("$documentsTree#root"), isDirectory = true)
    private val notes = DocumentRef(Uri.parse("content://docs/notes"), isDirectory = true)
    private val todo = DocumentRef(Uri.parse("content://docs/todo"), isDirectory = false)
    private val readme = DocumentRef(Uri.parse("content://docs/readme"), isDirectory = false)

    @After
    fun tearDown() = database.close()

    private fun grantDocuments(): Grant = runBlocking {
        documents.names[documentsTree] = "Documents"
        documents.tree[root.uri] = listOf("notes" to notes, "readme.txt" to readme)
        documents.tree[notes.uri] = listOf("todo.txt" to todo)
        store.add(documentsTree, GrantKind.FOLDER)
    }

    private fun resolve(path: String) = runBlocking { scope.resolve(path) }

    @Test
    fun addingTakesPersistedAccessAndNamesTheGrantUniquely() = runBlocking {
        val first = grantDocuments()
        val otherTree = Uri.parse("content://docs/tree/other")
        documents.names[otherTree] = "Documents"
        now = 2_000
        val second = store.add(otherTree, GrantKind.FOLDER)
        val unnamed = store.add(Uri.parse("content://docs/file"), GrantKind.FILE)

        assertEquals(listOf("Documents", "Documents (2)", "File"), store.current().map { it.name })
        assertEquals(setOf(documentsTree, otherTree, Uri.parse("content://docs/file")), documents.held)
        assertEquals(first, store.add(documentsTree, GrantKind.FOLDER))
        assertEquals(3, store.current().size)
        assertEquals(2_000, second.grantedAt)
        assertEquals(GrantKind.FILE, unnamed.kind)
    }

    @Test
    fun everyGrantChangeBumpsThePolicyVersion() = runBlocking {
        val before = dao.version() ?: 0
        val grant = grantDocuments()
        store.revoke(grant)

        assertEquals(before + 2, dao.version())
    }

    @Test
    fun revokingReleasesAccessAndForgetsTheGrant() = runBlocking {
        val grant = grantDocuments()
        store.revoke(grant)

        assertEquals(listOf(documentsTree), documents.released)
        assertEquals(emptyList<Grant>(), store.grants.first())
        assertTrue(resolve("Documents/readme.txt") is PathResolution.Refused)
    }

    @Test
    fun revokingSurvivesAccessAndroidAlreadyDropped() = runBlocking {
        val grant = grantDocuments()
        documents.held.clear()

        store.revoke(grant)
        assertEquals(emptyList<Grant>(), store.current())
    }

    @Test
    fun clearRevokesEverything() = runBlocking {
        grantDocuments()
        store.add(Uri.parse("content://docs/file"), GrantKind.FILE)

        store.clear()

        assertEquals(emptyList<Grant>(), store.current())
        assertEquals(emptySet<Uri>(), documents.held)
    }

    @Test
    fun aGrantAndroidNoLongerHoldsIsShownAndRefused() = runBlocking {
        grantDocuments()
        documents.held.clear()

        assertFalse(store.grants.first().single().available)
        assertEquals(PathResolution.Refused("The user's grant for that is no longer available."), resolve("Documents/readme.txt"))
    }

    @Test
    fun pathsResolveOnlyToRealChildrenOfAGrantedFolder() {
        val grant = grantDocuments()

        assertEquals(PathResolution.Found(grant, todo, notes, "todo.txt"), resolve("Documents/notes/todo.txt"))
        assertEquals(PathResolution.Found(grant, readme, root, "readme.txt"), resolve("Documents/readme.txt"))
        assertEquals(PathResolution.Found(grant, notes, root, "notes"), resolve("Documents/notes/"))
        assertEquals(PathResolution.Found(grant, root, null, "Documents"), resolve("Documents"))
        val missing = resolve("Documents/notes/new.txt") as PathResolution.Found
        assertNull("a file that does not exist yet resolves to its folder", missing.document)
        assertEquals(notes, missing.parent)
    }

    @Test
    fun pathsThatCouldEscapeOrMisleadAreRefused() {
        grantDocuments()
        val refused = listOf(
            "",
            "/Documents/readme.txt",
            "Documents/../readme.txt",
            "Documents/./readme.txt",
            "Documents//readme.txt",
            "Documents/notes\\..\\readme.txt",
            "Documents/read\u0000me.txt",
            "Documents/" + "a/".repeat(PathRules.MAX_SEGMENTS) + "b",
            "Documents/" + "a".repeat(PathRules.MAX_LENGTH),
            "Pictures/cat.jpg",
            "documents/readme.txt",
            "Documents/missing/readme.txt",
            "Documents/readme.txt/inside",
        )
        refused.forEach { path -> assertTrue("should refuse: $path", resolve(path) is PathResolution.Refused) }
    }

    @Test
    fun duplicateNamesInAFolderAreAmbiguous() {
        grantDocuments()
        documents.tree[root.uri] = listOf("readme.txt" to readme, "readme.txt" to todo)

        assertEquals(PathResolution.Refused("More than one item has that name, so it is ambiguous."), resolve("Documents/readme.txt"))
    }

    @Test
    fun aGrantedFileIsItsOwnOnlyPath() = runBlocking {
        val file = Uri.parse("content://docs/file")
        documents.names[file] = "report.pdf"
        val grant = store.add(file, GrantKind.FILE)

        assertEquals(PathResolution.Found(grant, DocumentRef(file, isDirectory = false), null, "report.pdf"), resolve("report.pdf"))
        assertTrue(resolve("report.pdf/page1") is PathResolution.Refused)
    }

    @Test
    fun grantNamesAreCleanedOfCharactersThatWouldBreakPaths() {
        assertEquals("a_b", PathRules.cleanName("a/b"))
        assertEquals("ab", PathRules.cleanName("a\u0007b"))
        assertNull(PathRules.cleanName(".."))
        assertNull(PathRules.cleanName("  "))
        assertNull(PathRules.cleanName(null))
    }

    @Test
    fun theEngineRefusesFileSkillsOutsideTheGrants() = runBlocking {
        val states = SkillStateStore(dao)
        val reader = Skill(
            "read_file", 1, "Read a file.", InputSchema(listOf(Parameter("path", ParameterType.STRING, "Path", required = true))),
            setOf(Capability.FILE_READ), SkillState.ACCEPTED, scope = ResourceScope.GRANTED_FILES,
        ) { SkillOutcome.Done("contents") }
        val engine = PolicyEngine(SkillRegistry(listOf(reader)), states, ToolOutput(), { true }, scope::check)

        val none = engine.decide("read_file", """{"path":"Documents/readme.txt"}""") as PolicyDecision.Denied
        assertEquals(DenialCode.RESOURCE_OUTSIDE_SCOPE, none.denial.code)

        grantDocuments()
        assertTrue(engine.decide("read_file", """{"path":"Documents/readme.txt"}""") is PolicyDecision.Allowed)
        val escape = engine.decide("read_file", """{"path":"Documents/../secret"}""") as PolicyDecision.Denied
        assertEquals(DenialCode.RESOURCE_OUTSIDE_SCOPE, escape.denial.code)
        assertFalse("the denial never repeats the path", escape.denial.message.contains("secret"))
    }
}
