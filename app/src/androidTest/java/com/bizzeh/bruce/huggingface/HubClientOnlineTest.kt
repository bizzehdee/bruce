package com.bizzeh.bruce.huggingface

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bizzeh.bruce.gguf.GgufReadResult
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@ManualOnly("needs the internet")
@RunWith(AndroidJUnit4::class)
class HubClientOnlineTest {
    private val client = HubClient(UrlConnectionTransport(), { true }, Dispatchers.IO, userAgent = "Bruce/online-test")

    @Test
    fun searchesTheRealHub() = runBlocking {
        val models = (client.search("qwen3 0.6b", limit = 5) as HubResult.Success).value

        assertTrue(models.isNotEmpty())
        assertTrue(models.all { it.id.contains('/') })
    }

    @Test
    fun listsRealGgufFilesWithHashes() = runBlocking {
        val files = (client.ggufFiles("ggml-org/Qwen3-0.6B-GGUF") as HubResult.Success).value

        val q4 = files.single { it.path == "Qwen3-0.6B-Q4_0.gguf" }
        assertEquals(428_970_080L, q4.sizeBytes)
        assertEquals("da2572f16c06133561ce56accaa822216f2391ef4d37fba427801cd6736417d4", q4.sha256)
    }

    @Test
    fun readsARealModelHeaderWithoutDownloadingTheModel() = runBlocking {
        val result = client.ggufHeader("ggml-org/Qwen3-0.6B-GGUF", "Qwen3-0.6B-Q4_0.gguf", revision = QWEN_REVISION)

        val metadata = ((result as HubResult.Success).value as GgufReadResult.Read).metadata
        assertEquals("qwen3", metadata.architecture)
        assertEquals(428_970_080L, metadata.fileSizeBytes)
        assertEquals(28L, metadata.blockCount)
    }

    private companion object {
        const val QWEN_REVISION = "b5f37287796e5be0ea3dab2e7430873fb3f73e49"
    }
}
