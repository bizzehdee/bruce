package com.bizzeh.bruce.settings

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.policy.DocumentAccess
import com.bizzeh.bruce.policy.DocumentRef
import com.bizzeh.bruce.policy.Grant
import com.bizzeh.bruce.policy.GrantKind
import com.bizzeh.bruce.policy.GrantStore
import com.bizzeh.bruce.policy.PolicyDatabase
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
}
