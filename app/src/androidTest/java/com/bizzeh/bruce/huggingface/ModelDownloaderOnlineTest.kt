package com.bizzeh.bruce.huggingface

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bizzeh.bruce.testing.ManualOnly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@ManualOnly("needs the internet")
@RunWith(AndroidJUnit4::class)
class ModelDownloaderOnlineTest {
    @Test
    fun downloadsAndVerifiesARealModel() = runBlocking<Unit> {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "online-download").apply { deleteRecursively() }
        val downloader = ModelDownloader(UrlConnectionTransport(), dir, { true }, Dispatchers.IO, "Bruce/online-test")

        val result = downloader.download(
            repositoryId = "ggml-org/models",
            path = "tinyllamas/stories260K.gguf",
            sizeBytes = 1_185_376,
            sha256 = "270cba1bd5109f42d03350f60406024560464db173c0e387d91f0426d3bd256d",
            revision = "499bc8821c6b12b4e53c5bffcb21ec206f212d81",
        )

        assertEquals(1_185_376L, (result as DownloadResult.Downloaded).file.length())
        dir.deleteRecursively()
    }
}
