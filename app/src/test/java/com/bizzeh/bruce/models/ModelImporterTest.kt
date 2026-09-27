package com.bizzeh.bruce.models

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ModelImporterTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var modelsDir: File
    private var freeSpace = Long.MAX_VALUE
    private val dispatcher = StandardTestDispatcher()
    private val ggufBytes = "GGUF".toByteArray() + ByteArray(4096) { it.toByte() }

    private val importer by lazy {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        ModelImporter(resolver, modelsDir, dispatcher) { freeSpace }
    }

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(TestDocumentProvider::class.java, TestDocumentProvider.AUTHORITY)
        modelsDir = File(temp.root, "models")
    }

    @After
    fun tearDown() {
        TestDocumentProvider.documents.clear()
    }

    private fun document(bytes: ByteArray?, displayName: String?, sizeBytes: Long? = bytes?.size?.toLong()): Uri {
        val uri = Uri.parse("content://${TestDocumentProvider.AUTHORITY}/doc/${TestDocumentProvider.documents.size}")
        val file = bytes?.let { temp.newFile().apply { writeBytes(it) } }
        TestDocumentProvider.documents[uri] = TestDocumentProvider.Document(file, displayName, sizeBytes)
        return uri
    }

    @Test
    fun copiesGgufIntoModelsDirectory() = runTest(dispatcher) {
        val result = importer.import(document(ggufBytes, "qwen3-4b-q4km.gguf"))

        val expected = File(modelsDir, "qwen3-4b-q4km.gguf")
        assertEquals(ImportResult.Imported(expected), result)
        assertArrayEquals(ggufBytes, expected.readBytes())
        assertEquals(listOf("qwen3-4b-q4km.gguf"), modelsDir.list()!!.toList())
    }

    @Test
    fun rejectsNonGgufWithoutWritingAnything() = runTest(dispatcher) {
        val result = importer.import(document("PK\u0003\u0004 zip".toByteArray(), "model.gguf"))

        assertEquals(ImportResult.Failed(ImportError.NOT_GGUF), result)
        assertEquals(0, modelsDir.list()!!.size)
    }

    @Test
    fun rejectsModelLargerThanFreeSpace() = runTest(dispatcher) {
        freeSpace = ggufBytes.size - 1L

        assertEquals(ImportResult.Failed(ImportError.INSUFFICIENT_STORAGE), importer.import(document(ggufBytes, "m.gguf")))
    }

    @Test
    fun unknownSizeIsCopied() = runTest(dispatcher) {
        freeSpace = 0L

        val result = importer.import(document(ggufBytes, "m.gguf", sizeBytes = null))

        assertEquals(ImportResult.Imported(File(modelsDir, "m.gguf")), result)
    }

    @Test
    fun unreadableDocumentIsReported() = runTest(dispatcher) {
        assertEquals(ImportResult.Failed(ImportError.UNREADABLE), importer.import(document(null, "m.gguf")))
    }

    @Test
    fun revokedPermissionIsReportedAsUnreadable() = runTest(dispatcher) {
        val uri = document(ggufBytes, "m.gguf")
        TestDocumentProvider.documents[uri] = TestDocumentProvider.Document(null, "m.gguf", null, permissionRevoked = true)

        assertEquals(ImportResult.Failed(ImportError.UNREADABLE), importer.import(uri))
    }

    @Test
    fun existingNameGetsNumberedSuffix() = runTest(dispatcher) {
        importer.import(document(ggufBytes, "m.gguf"))
        importer.import(document(ggufBytes, "m.gguf"))
        val third = importer.import(document(ggufBytes, "m.gguf"))

        assertEquals(ImportResult.Imported(File(modelsDir, "m-2.gguf")), third)
    }

    @Test
    fun missingDisplayNameUsesDefault() = runTest(dispatcher) {
        assertEquals(ImportResult.Imported(File(modelsDir, "model.gguf")), importer.import(document(ggufBytes, null)))
    }

    @Test
    fun copyFailureLeavesNoPartialFile() = runTest(dispatcher) {
        modelsDir.mkdirs()
        modelsDir.setWritable(false)
        try {
            assertEquals(ImportResult.Failed(ImportError.COPY_FAILED), importer.import(document(ggufBytes, "m.gguf")))
        } finally {
            modelsDir.setWritable(true)
        }
        assertFalse(modelsDir.listFiles()!!.any { it.name.endsWith(".partial") })
    }
}
