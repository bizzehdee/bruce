package com.bizzeh.bruce.testing

import com.bizzeh.bruce.litert.LiteRtPipeline
import com.bizzeh.bruce.litert.LiteRtPipelineResult
import com.bizzeh.bruce.runtime.BackendChat
import com.bizzeh.bruce.runtime.ChatBackend
import com.bizzeh.bruce.runtime.Prepared
import com.bizzeh.bruce.runtime.StepEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File

/** A trivial [ChatBackend] for tests that only need something present, not its generation behaviour. */
class FakeChatBackend(private val contextTokens: Int = 4096) : ChatBackend {
    override suspend fun contextLength(): Int = contextTokens
    override suspend fun prepare(chat: BackendChat): Prepared = Prepared.Ready(chat, 0, null)
    override fun generate(prepared: Prepared.Ready, maxTokens: Int, temperature: Float, branchOf: BackendChat?): Flow<StepEvent> = flowOf()
    override fun stop() = Unit
}

class FakeLiteRtPipeline : LiteRtPipeline {
    var result: LiteRtPipelineResult = LiteRtPipelineResult.Loaded(FakeChatBackend(), 4096)
    val loads = mutableListOf<Triple<File, Int, Boolean?>>()
    var unloads = 0
    var gpuAvailableFiles = emptySet<String>()
    private var active: LiteRtPipelineResult.Loaded? = null

    override suspend fun load(file: File, contextTokens: Int, preferGpu: Boolean?): LiteRtPipelineResult {
        loads += Triple(file, contextTokens, preferGpu)
        active = result as? LiteRtPipelineResult.Loaded
        return result
    }

    override suspend fun unload() {
        unloads++
        active = null
    }

    override fun contextLength(): Int? = active?.contextLength

    override fun current(): ChatBackend? = active?.backend

    override fun gpuAvailable(file: File): Boolean = file.name in gpuAvailableFiles
}
