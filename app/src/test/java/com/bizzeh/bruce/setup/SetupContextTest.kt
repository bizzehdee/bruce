package com.bizzeh.bruce.setup

import com.bizzeh.bruce.R
import com.bizzeh.bruce.gguf.GgufMetadata
import com.bizzeh.bruce.hardware.CpuFeatures
import com.bizzeh.bruce.huggingface.DownloadError
import com.bizzeh.bruce.models.AutoContext
import com.bizzeh.bruce.models.BrowseState
import com.bizzeh.bruce.models.Candidate
import com.bizzeh.bruce.models.DeviceProfile
import com.bizzeh.bruce.models.DeviceTier
import com.bizzeh.bruce.models.DownloadState
import com.bizzeh.bruce.models.InstalledModel
import com.bizzeh.bruce.models.ModelFit
import com.bizzeh.bruce.models.ModelOverrides
import com.bizzeh.bruce.models.RepositoryFiles
import com.bizzeh.bruce.models.SuggestedModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class SetupContextTest {
    private val gb = 1L shl 30
    private val metadata = GgufMetadata(
        version = 3, architecture = "qwen3", name = null, parameterCount = 1_700_000_000, tensorCount = 300, contextLength = 32_768,
        fileType = 15, quantisation = "Q4_K_M", fileSizeBytes = gb,
        blockCount = 28, embeddingLength = 2048, headCount = 16, headCountKv = 8, keyLength = 128, valueLength = 128,
    )
    private val phone = DeviceProfile(4 * gb, CpuFeatures(arm64 = true, neon = true, fp16 = true, dotProd = true, i8mm = false), 8 * gb)
    private val suggestion = SuggestedModel("a/repo", "m.gguf", gb, "Model M", R.string.suggested_qwen3_1_7b, setOf(DeviceTier.EIGHT_GB))
    private val installed = InstalledModel(File("other.gguf"), gb, metadata, ModelFit.assess(Candidate("", "other.gguf", gb, false, "qwen3", null, metadata), phone, 4096), ModelOverrides())

    private fun browse(download: DownloadState?) = BrowseState(
        downloads = download?.let { mapOf("a/repo/m.gguf" to it) }.orEmpty(),
        files = mapOf("a/repo" to RepositoryFiles(loading = false, ranked = listOf(ModelFit.assess(Candidate("a/repo", "m.gguf", gb, false, "qwen3", null, metadata), phone, 4096)))),
    )

    @Test
    fun aSuggestedModelBeingDownloadedIsTheOneAskedAbout() {
        val choice = SetupContext.choice(listOf(installed), listOf(suggestion), browse(DownloadState(10, 100)), 4 * gb)!!
        assertEquals("Model M", choice.modelName)
        assertEquals(AutoContext.pick(metadata, 4 * gb), choice.auto)
        assertEquals(AutoContext.options(metadata, 4 * gb), choice.options)
    }

    @Test
    fun otherwiseTheInstalledModelAndWithNeitherNoStep() {
        assertEquals("other", SetupContext.choice(listOf(installed), listOf(suggestion), browse(null), 4 * gb)?.modelName)
        assertEquals("other", SetupContext.choice(listOf(installed), listOf(suggestion), browse(DownloadState(0, 0, DownloadError.INTERRUPTED)), 4 * gb)?.modelName)
        assertNull(SetupContext.choice(emptyList(), listOf(suggestion), browse(null), 4 * gb))
        assertNull(SetupContext.choice(emptyList(), listOf(suggestion), BrowseState(downloads = mapOf("a/repo/m.gguf" to DownloadState(0, 0))), 4 * gb), "header not read yet")
    }
}
