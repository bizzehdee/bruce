package com.bizzeh.bruce.settings

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.policy.DocumentAccess
import com.bizzeh.bruce.policy.DocumentRef
import com.bizzeh.bruce.policy.FolderInstructionsStore
import com.bizzeh.bruce.policy.Grant
import com.bizzeh.bruce.policy.GrantKind
import com.bizzeh.bruce.policy.GrantStore
import com.bizzeh.bruce.policy.InstructionsChoice
import com.bizzeh.bruce.policy.PolicyDatabase
import com.bizzeh.bruce.testing.FakeDocuments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GrantsViewModelTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PolicyDatabase::class.java).build()

    private var refuse = false
    private val documents = object : DocumentAccess {
        val held = mutableSetOf<Uri>()
        override fun take(uri: Uri) {
            if (refuse) throw SecurityException("not persistable")
            held += uri
        }
        override fun release(uri: Uri) { held -= uri }
        override fun persisted(): Set<Uri> = held.toSet()
        override fun displayName(uri: Uri, kind: GrantKind) = "Documents"
        override fun root(tree: Uri) = DocumentRef(tree, isDirectory = true)
        override fun children(tree: Uri, folder: DocumentRef) = emptyList<Pair<String, DocumentRef>>()
        override fun mimeType(document: Uri): String? = null
        override fun read(document: Uri, maxBytes: Int): ByteArray? = null
        override fun create(tree: Uri, parent: DocumentRef, name: String): DocumentRef? = null
        override fun write(document: Uri, bytes: ByteArray) = Unit
        override fun delete(document: Uri) = false
    }
    /** Built after setUp, so its flows start on the test Main dispatcher rather than the blocked main looper. */
    private val viewModel by lazy { GrantsViewModel(GrantStore(database.policy(), documents), Dispatchers.IO) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun <T> StateFlow<T>.await(predicate: (T) -> Boolean): T = runBlocking { withTimeout(5_000) { first(predicate) } }

    @Test
    fun addsRevokesAndReportsARefusedGrant() {
        val tree = Uri.parse("content://docs/tree/documents")
        viewModel.add(tree, GrantKind.FOLDER)
        val grant: Grant = viewModel.grants.await { it.isNotEmpty() }.single()
        assertEquals("Documents", grant.name)

        viewModel.revoke(grant)
        viewModel.grants.await { it.isEmpty() }

        refuse = true
        viewModel.add(Uri.parse("content://docs/tree/other"), GrantKind.FOLDER)
        viewModel.addFailed.await { it }
        refuse = false
        viewModel.add(tree, GrantKind.FOLDER)
        viewModel.addFailed.await { !it }
    }

    @Test
    fun aNewFolderWithInstructionsIsReviewedAndTheChoiceShows() {
        val documents = FakeDocuments()
        val tree = Uri.parse("content://docs/tree/notes")
        val agents = DocumentRef(Uri.parse("content://docs/agents-md"), isDirectory = false)
        documents.names[tree] = "Notes"
        documents.tree[documents.root(tree).uri] = listOf("AGENTS.md" to agents)
        documents.contents[agents.uri] = "text/markdown" to "Keep lists short.".toByteArray()
        val viewModel = GrantsViewModel(GrantStore(database.policy(), documents), Dispatchers.IO, FolderInstructionsStore(database.policy(), documents))

        viewModel.add(tree, GrantKind.FOLDER)
        val id = viewModel.reviewing.await { it != null }!!
        val review = viewModel.folders.await { it[id] != null }.getValue(id)
        assertEquals(InstructionsChoice.UNDECIDED, review.choice)
        assertEquals("Keep lists short.", review.instructions.agentsMd)

        viewModel.choose(review, follow = true)
        assertNull(viewModel.reviewing.value)
        viewModel.folders.await { it[id]?.choice == InstructionsChoice.FOLLOW }

        viewModel.review(viewModel.grants.await { it.isNotEmpty() }.single())
        assertEquals(id, viewModel.reviewing.value)
        viewModel.closeReview()
        assertNull(viewModel.reviewing.value)
    }
}
