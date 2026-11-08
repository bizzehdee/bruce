package com.bizzeh.bruce.litert

import com.bizzeh.bruce.inference.ToolCall
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.ToolDefinition
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * What [LiteRtBackend] needs from LiteRT-LM, narrowed to this shape so tests can fake it without the
 * real native library (TASK-118). [LiteRtNative] is the real implementation.
 */
internal interface LiteRtApi {
    /**
     * Loads [file] as the active model, following [LiteRtBackendSelection.plan] for [preference].
     * [maxContextTokens] non-positive asks for the model's default (`EngineConfig.maxNumTokens`).
     * [nativeLibraryDir] (`context.applicationInfo.nativeLibraryDir`) is where the Tensor NPU
     * delegate's library is bundled; unused for the CPU and GPU choices.
     */
    fun loadModel(file: File, maxContextTokens: Int, preference: LiteRtBackendPreference, nativeLibraryDir: String): LiteRtLoadResult
}

internal sealed interface LiteRtLoadResult {
    /** [backend] is where the model loaded; [failedBackends] lists non-CPU choices tried first that failed, in order. */
    data class Loaded(val model: LiteRtModel, val backend: LiteRtBackendChoice, val failedBackends: List<LiteRtBackendChoice> = emptyList()) : LiteRtLoadResult

    data object Failed : LiteRtLoadResult
}

/** One loaded model; a fresh [LiteRtConversation] is made per turn, carrying the turn's full history (TASK-118: no persistent, stateful session, matching how [com.bizzeh.bruce.inference.LlamaCppEngine] is given the whole conversation each time). */
internal interface LiteRtModel {
    /** The context size this load was actually configured with — the requested override when one was given, the model's own declared default (`ModelInfo.maxContextTokens`) otherwise (TASK-123 follow-up bug fix: this used to always read the file's static default, ignoring any override). */
    val maxContextTokens: Int

    /**
     * Starts a conversation whose history is [priorMessages] and sends [latest] as the newest turn.
     * [systemInstruction] and [tools] configure the conversation; automatic tool calling is always
     * off (`ConversationConfig.automaticToolCalling = false`) so tool calls return to Bruce's policy
     * engine rather than executing inside LiteRT-LM.
     */
    fun converse(
        systemInstruction: String,
        priorMessages: List<ToolChatMessage>,
        latest: ToolChatMessage,
        tools: List<ToolDefinition>,
        thinking: Boolean,
        maxTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
    ): LiteRtConversation

    fun close()
}

internal interface LiteRtConversation {
    /** Streams the reply to the turn started by [LiteRtModel.converse]. */
    fun send(): Flow<LiteRtEvent>

    /** Best-effort: asks LiteRT-LM to stop the in-progress turn (`Conversation.cancelProcess`, unverified on-device). */
    fun cancel()

    /** The conversation's token usage so far (`Conversation.getTokenCount`, the KV-cache size, not an exact prompt/completion split). */
    fun tokenCount(): Int

    fun close()
}

sealed interface LiteRtEvent {
    data class Text(val text: String) : LiteRtEvent

    data class Reasoning(val text: String) : LiteRtEvent

    data class Done(val content: String, val reasoning: String, val toolCalls: List<ToolCall>) : LiteRtEvent

    data object Failed : LiteRtEvent
}
