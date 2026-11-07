package com.bizzeh.bruce.litert

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.io.File

class LiteRtPipelineImplTest {
    private class FakeModel(override val maxContextTokens: Int) : LiteRtModel {
        var closed = false
        override fun converse(
            systemInstruction: String,
            priorMessages: List<com.bizzeh.bruce.inference.ToolChatMessage>,
            latest: com.bizzeh.bruce.inference.ToolChatMessage,
            tools: List<com.bizzeh.bruce.inference.ToolDefinition>,
            thinking: Boolean,
            maxTokens: Int,
            temperature: Float,
        ): LiteRtConversation = error("not used")

        override fun close() {
            closed = true
        }
    }

    private class FakeApi : LiteRtApi {
        var result: LiteRtLoadResult = LiteRtLoadResult.Failed
        val calls = mutableListOf<Triple<File, Int, LiteRtBackendPreference>>()
        val nativeLibraryDirs = mutableListOf<String>()

        override fun loadModel(file: File, maxContextTokens: Int, preference: LiteRtBackendPreference, nativeLibraryDir: String): LiteRtLoadResult {
            calls += Triple(file, maxContextTokens, preference)
            nativeLibraryDirs += nativeLibraryDir
            return result
        }
    }

    private val api = FakeApi()
    private val pipeline = LiteRtPipelineImpl(api, "/data/app/com.bizzeh.bruce/lib/arm64")

    @Test
    fun loadAsksForAutoForAnUnverifiedModelAndPassesTheNativeLibraryDir() = runBlockingTest {
        pipeline.load(File("a.litertlm"), 4096)
        assertEquals(LiteRtBackendPreference.AUTO, api.calls.single().third)
        assertEquals(4096, api.calls.single().second)
        assertEquals("/data/app/com.bizzeh.bruce/lib/arm64", api.nativeLibraryDirs.single())
    }

    @Test
    fun loadAsksForGpuForAFileOnTheVerifiedList() = runBlockingTest {
        pipeline.load(File("/data/models/gemma-4-E2B-it.litertlm"), 4096)
        assertEquals(LiteRtBackendPreference.GPU, api.calls.single().third)
    }

    @Test
    fun preferGpuTrueForcesGpuOnAVerifiedFile() = runBlockingTest {
        pipeline.load(File("/data/models/gemma-4-E2B-it.litertlm"), 4096, preferGpu = true)
        assertEquals(LiteRtBackendPreference.GPU, api.calls.single().third)
    }

    @Test
    fun preferGpuFalseForcesCpuEvenOnAVerifiedFile() = runBlockingTest {
        pipeline.load(File("/data/models/gemma-4-E2B-it.litertlm"), 4096, preferGpu = false)
        assertEquals(LiteRtBackendPreference.CPU, api.calls.single().third)
    }

    @Test
    fun preferGpuTrueOnAnUnverifiedFileStillAsksForGpu() = runBlockingTest {
        // The caller is responsible for only offering true when gpuAvailable said so; load()
        // itself does not re-check the allowlist for an explicit request.
        pipeline.load(File("a.litertlm"), 4096, preferGpu = true)
        assertEquals(LiteRtBackendPreference.GPU, api.calls.single().third)
    }

    @Test
    fun gpuAvailableMatchesTheVerifiedList() {
        assertEquals(true, pipeline.gpuAvailable(File("/data/models/gemma-4-E2B-it.litertlm")))
        assertEquals(false, pipeline.gpuAvailable(File("/data/models/gemma-3-270m-it.litertlm")))
    }

    @Test
    fun loadedModelExposesContextLengthAndACurrentBackend() = runBlockingTest {
        val model = FakeModel(8192)
        api.result = LiteRtLoadResult.Loaded(model, LiteRtBackendChoice.CPU)

        val result = pipeline.load(File("a.litertlm"), 0)

        assertEquals(LiteRtPipelineResult.Loaded::class, result::class)
        assertEquals(8192, pipeline.contextLength())
        assertSame((result as LiteRtPipelineResult.Loaded).backend, pipeline.current())
    }

    @Test
    fun aSecondLoadClosesThePreviousModel() = runBlockingTest {
        val first = FakeModel(4096)
        api.result = LiteRtLoadResult.Loaded(first, LiteRtBackendChoice.CPU)
        pipeline.load(File("a.litertlm"), 0)

        api.result = LiteRtLoadResult.Loaded(FakeModel(8192), LiteRtBackendChoice.CPU)
        pipeline.load(File("b.litertlm"), 0)

        assertEquals(true, first.closed)
        assertEquals(8192, pipeline.contextLength())
    }

    @Test
    fun aFailedLoadClearsTheCurrentModel() = runBlockingTest {
        val model = FakeModel(4096)
        api.result = LiteRtLoadResult.Loaded(model, LiteRtBackendChoice.CPU)
        pipeline.load(File("a.litertlm"), 0)

        api.result = LiteRtLoadResult.Failed
        val result = pipeline.load(File("b.litertlm"), 0)

        assertEquals(LiteRtPipelineResult.Failed, result)
        assertNull(pipeline.contextLength())
        assertNull(pipeline.current())
        assertEquals(true, model.closed)
    }

    @Test
    fun unloadClosesTheModel() = runBlockingTest {
        val model = FakeModel(4096)
        api.result = LiteRtLoadResult.Loaded(model, LiteRtBackendChoice.CPU)
        pipeline.load(File("a.litertlm"), 0)

        pipeline.unload()

        assertEquals(true, model.closed)
        assertNull(pipeline.contextLength())
    }

    private fun runBlockingTest(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }
}
