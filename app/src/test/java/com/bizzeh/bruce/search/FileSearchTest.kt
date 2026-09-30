package com.bizzeh.bruce.search

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.bizzeh.bruce.huggingface.DownloadError
import com.bizzeh.bruce.huggingface.DownloadResult
import com.bizzeh.bruce.policy.DocumentRef
import com.bizzeh.bruce.policy.Grant
import com.bizzeh.bruce.policy.GrantKind
import com.bizzeh.bruce.testing.FakeDocuments
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileSearchTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, SearchDatabase::class.java).allowMainThreadQueries().build()

    /** Documents whose modified time can be set. */
    private class Docs : com.bizzeh.bruce.policy.DocumentAccess by FakeDocuments() {
        val fake = FakeDocuments()
        val modified = mutableMapOf<Uri, Long>()
        override fun root(tree: Uri) = fake.root(tree)
        override fun children(tree: Uri, folder: DocumentRef) = fake.children(tree, folder)
        override fun mimeType(document: Uri) = fake.mimeType(document)
        override fun read(document: Uri, maxBytes: Int) = fake.read(document, maxBytes)
        override fun lastModified(document: Uri) = modified[document]
    }

    /** A word-count embedding over a tiny vocabulary: enough for "about dogs" to find the dog note. */
    private class WordEmbedder : Embedder {
        val words = listOf("dog", "vet", "boiler", "heating", "tax", "receipt")
        var passages = 0
        var released = 0
        private fun vector(text: String): FloatArray {
            val v = FloatArray(words.size) { i -> Regex("\\b${words[i]}").findAll(text.lowercase()).count().toFloat() }
            val norm = kotlin.math.sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
            return if (norm == 0f) v else FloatArray(v.size) { v[it] / norm }
        }
        override suspend fun passage(text: String) = vector(text).also { passages++ }
        override suspend fun query(text: String) = vector(text)
        override suspend fun release() {
            released++
        }
    }

    private val docs = Docs()
    private val embedder = WordEmbedder()
    private val tree = Uri.parse("content://docs/tree/notes")
    private val root = Uri.parse("$tree#root")
    private val sub = DocumentRef(Uri.parse("content://docs/sub"), isDirectory = true)
    private val dog = DocumentRef(Uri.parse("content://docs/dog"), isDirectory = false)
    private val boiler = DocumentRef(Uri.parse("content://docs/boiler"), isDirectory = false)
    private val photo = DocumentRef(Uri.parse("content://docs/photo"), isDirectory = false)
    private val hidden = DocumentRef(Uri.parse("content://docs/hidden"), isDirectory = false)
    private var grants = listOf(Grant(1, tree, "Notes", GrantKind.FOLDER, 0, available = true))
    private val search = FileSearch(database.search(), { grants }, docs, embedder, extract = { _, type, bytes -> if (type == "text/plain") String(bytes) else null }, chunker = Chunker(size = 60, overlap = 10))

    init {
        docs.fake.tree[root] = listOf("dog.txt" to dog, "sub" to sub, "photo.jpg" to photo, ".secret" to hidden)
        docs.fake.tree[sub.uri] = listOf("boiler.txt" to boiler)
        docs.fake.contents[dog.uri] = "text/plain" to "The dog sees the vet in May for jabs.".toByteArray()
        docs.fake.contents[boiler.uri] = "text/plain" to "Boiler service booked; the heating engineer comes Tuesday.".toByteArray()
        docs.fake.contents[photo.uri] = "image/jpeg" to byteArrayOf(1, 2)
        docs.fake.contents[hidden.uri] = "text/plain" to "tax receipt".toByteArray()
        docs.modified.putAll(mapOf(dog.uri to 1L, boiler.uri to 1L, photo.uri to 1L, hidden.uri to 1L))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun grantedTextFilesAreIndexedAndFoundByMeaning() = runBlocking {
        search.update()

        assertEquals(IndexStatus(files = 2, passages = 2, checked = 3, lastUpdated = search.status.value.lastUpdated), search.status.value)
        val hits = search.search("When does the dog go to the vet?", 1)
        assertEquals("Notes/dog.txt", hits.single().path)
        assertTrue(hits.single().text.startsWith("The dog sees the vet"))
        assertEquals("Notes/sub/boiler.txt", search.search("heating", 5).first().path)
        assertEquals("the model's memory is freed after an update", 1, embedder.released)
    }

    @Test
    fun onlyChangedFilesAreEmbeddedAgainAndGoneOnesDropped() = runBlocking {
        search.update()
        val first = embedder.passages
        search.update()
        assertEquals("nothing changed", first, embedder.passages)

        docs.modified[dog.uri] = 2L
        docs.fake.contents[dog.uri] = "text/plain" to "The dog is well; next vet visit in June.".toByteArray()
        docs.fake.tree[sub.uri] = emptyList()
        search.update()

        assertEquals(first + 1, embedder.passages)
        assertTrue(search.search("next vet visit", 1).single().text.contains("June"))
        assertEquals(1, search.status.value.files)
    }

    @Test
    fun aRevokedGrantLeavesNothingBehind() = runBlocking {
        search.update()
        grants = listOf(grants.single().copy(available = false))
        search.update()
        assertEquals(0, search.status.value.passages)
        assertTrue(search.search("dog", 3).isEmpty())
    }

    @Test
    fun aGrantedFileAndLongTextAreHandled() = runBlocking {
        val single = Uri.parse("content://docs/single")
        docs.fake.contents[single] = "text/plain" to ("Tax receipt for the laptop. ".repeat(10)).toByteArray()
        docs.modified[single] = 1L
        grants = listOf(Grant(2, single, "receipt.txt", GrantKind.FILE, 0, available = true))
        search.update()

        assertTrue(search.status.value.passages > 1)
        assertEquals("receipt.txt", search.search("tax", 1).single().path)
        search.clear()
        assertEquals(0, search.status.value.files)
    }

    @Test
    fun textIsCutIntoOverlappingPassagesAtNaturalBreaks() {
        val chunker = Chunker(size = 40, overlap = 8)
        val chunks = chunker.chunks("First paragraph is here.\n\nSecond paragraph follows it closely.\n\n\n\nThird.")
        assertTrue(chunks.all { it.length <= 40 })
        assertEquals("First paragraph is here.", chunks.first())
        assertTrue(chunks.last().endsWith("Third."))
        assertEquals(emptyList<String>(), chunker.chunks("   "))
        assertEquals(listOf("x".repeat(40), "x".repeat(20)).map { it.length }, Chunker(size = 40, overlap = 8).chunks("x".repeat(52)).map { it.length })
    }

    @Test
    fun vectorsRoundTripAndCompare() {
        val v = floatArrayOf(0.6f, 0.8f)
        assertTrue(Vectors.fromBytes(Vectors.toBytes(v)).contentEquals(v))
        assertEquals(1f, Vectors.similarity(v, v), 1e-6f)
        assertEquals(0f, Vectors.similarity(v, floatArrayOf(1f)), 0f)
    }

    @Test
    fun settingUpDownloadsThenIndexesAndRemovingDeletesEverything() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val model = temp.newFile("model.gguf").also { it.delete() }
        var result: DownloadResult = DownloadResult.Failed(DownloadError.NETWORK_DISABLED)
        val setup = SearchSetup({ progress -> progress(1, 2); if (result is DownloadResult.Downloaded) model.writeText("m"); result }, model, search, IndexWork(context))

        setup.download()
        assertEquals(SetupState(installed = false, error = DownloadError.NETWORK_DISABLED), setup.setup.value)
        setup.updateIndex()
        assertTrue(WorkManager.getInstance(context).getWorkInfosForUniqueWork("file-index").get().isEmpty())

        result = DownloadResult.Downloaded(model)
        setup.download()
        assertEquals(SetupState(installed = true), setup.setup.value)
        assertEquals(WorkInfo.State.ENQUEUED, WorkManager.getInstance(context).getWorkInfosForUniqueWork("file-index-daily").get().single().state)

        search.update()
        setup.remove()
        assertFalse(model.exists())
        assertEquals(0, search.status.value.files)
        assertEquals(SetupState(installed = false), setup.setup.value)
    }
}
