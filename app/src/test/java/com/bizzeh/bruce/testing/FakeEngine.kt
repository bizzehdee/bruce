package com.bizzeh.bruce.testing

import com.bizzeh.bruce.inference.Backend
import com.bizzeh.bruce.inference.ChatMessage
import com.bizzeh.bruce.inference.ChatPrompt
import com.bizzeh.bruce.inference.EngineCapabilities
import com.bizzeh.bruce.inference.GenerationEvent
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.LoadConfig
import com.bizzeh.bruce.inference.LoadResult
import com.bizzeh.bruce.inference.ModelInfo
import com.bizzeh.bruce.inference.ParsedReply
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.ToolChatPrompt
import com.bizzeh.bruce.inference.ToolDefinition
import com.bizzeh.bruce.inference.ToolFormat
import com.bizzeh.bruce.inference.ToolGrammar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File

class FakeEngine : InferenceEngine {
    var reportedCapabilities = EngineCapabilities(emptyList(), listOf("NEON=1"))
    var loadResult: LoadResult = LoadResult.Loaded(ModelInfo("fake", 1, 1, 512), Backend.CPU)
    var events: List<GenerationEvent> = emptyList()
    val loads = mutableListOf<Pair<File, LoadConfig>>()
    val requests = mutableListOf<GenerationRequest>()
    var stops = 0
    var unloads = 0
    var loadedModel: ModelInfo? = null
    var prompt: ChatPrompt? = ChatPrompt("<formatted>", usedFallbackTemplate = false)
    val formatted = mutableListOf<List<ChatMessage>>()

    override suspend fun loadModel(file: File, config: LoadConfig): LoadResult {
        loads += file to config
        return loadResult
    }

    override suspend fun unloadModel() {
        unloads++
    }

    override fun getCapabilities() = reportedCapabilities

    override fun getModelInfo(): ModelInfo? = loadedModel

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> {
        requests += request
        return flowOf(*events.toTypedArray())
    }

    override fun stop() {
        stops++
    }

    override suspend fun formatChat(messages: List<ChatMessage>): ChatPrompt? {
        formatted += messages
        return prompt
    }

    override suspend fun formatToolChat(messages: List<ToolChatMessage>, tools: List<ToolDefinition>, enableThinking: Boolean): ToolChatPrompt? = null

    override fun parseReply(format: ToolFormat, text: String, partial: Boolean): ParsedReply? = null

    override fun bruceToolGrammar(tools: List<ToolDefinition>): ToolGrammar? = null
}
