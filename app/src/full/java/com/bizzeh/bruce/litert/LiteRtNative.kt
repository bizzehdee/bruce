package com.bizzeh.bruce.litert

import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.ToolDefinition
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.io.File
import com.bizzeh.bruce.inference.ToolCall as BruceToolCall
import com.google.ai.edge.litertlm.ToolCall as NativeToolCall

/**
 * Wraps the real LiteRT-LM Kotlin API (TASK-118; GPU backend TASK-119). Isolated to this one file
 * because the exact API shape came from reading the library's own compiled classes on 2026-11-06
 * (`javap` against `litertlm-android-0.19.1.aar`; see
 * `research/sources/litert-lm-android-tensor-sdk-2026-11-06/` and
 * `research/sources/litert-torch-conversion-2026-11-06/` for background), not from an API
 * reference or a real device run. The library is pre-1.0 (`0.19.1`): re-check this file against the
 * pinned version on any bump.
 *
 * Unverified, to confirm on first real device build: whether `sendMessageAsync(): Flow<Message>`
 * emits incremental deltas (assumed here, appended as they arrive, matching how
 * [com.bizzeh.bruce.inference.LlamaCppEngine] and [com.bizzeh.bruce.remote.RemoteBackend] stream) or
 * whole cumulative messages; and whether `Conversation.cancelProcess()` actually halts native
 * generation (its own KDoc says it does; the C++ side was not read).
 */
internal object LiteRtNative : LiteRtApi {
    override fun loadModel(file: File, maxContextTokens: Int, preference: LiteRtBackendPreference): LiteRtLoadResult {
        val failed = mutableListOf<LiteRtBackendChoice>()
        for (choice in LiteRtBackendSelection.plan(preference)) {
            when (val result = openOn(file, maxContextTokens, choice)) {
                null -> if (choice != LiteRtBackendChoice.CPU) failed += choice
                else -> return LiteRtLoadResult.Loaded(result, choice, failed.toList())
            }
        }
        return LiteRtLoadResult.Failed
    }

    private fun nativeBackend(choice: LiteRtBackendChoice): Backend = when (choice) {
        LiteRtBackendChoice.CPU -> Backend.CPU()
        LiteRtBackendChoice.GPU -> Backend.GPU()
    }

    private fun openOn(file: File, maxContextTokens: Int, choice: LiteRtBackendChoice): EngineModel? {
        val engine = Engine(EngineConfig(modelPath = file.absolutePath, backend = nativeBackend(choice), maxNumTokens = maxContextTokens))
        return try {
            engine.initialize()
            val contextTokens = ModelInfo.from(file.absolutePath).use { it.maxContextTokens() }
            EngineModel(engine, contextTokens)
        } catch (e: Exception) {
            engine.close()
            null
        }
    }

    private class EngineModel(private val engine: Engine, override val maxContextTokens: Int) : LiteRtModel {
        override fun converse(
            systemInstruction: String,
            priorMessages: List<ToolChatMessage>,
            latest: ToolChatMessage,
            tools: List<ToolDefinition>,
            thinking: Boolean,
            maxTokens: Int,
            temperature: Float,
        ): LiteRtConversation {
            val config = ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
                initialMessages = priorMessages.map(::toLiteRtMessage),
                tools = tools.map { tool(BruceTool(it)) },
                samplerConfig = SamplerConfig(topK = DEFAULT_TOP_K, topP = DEFAULT_TOP_P, temperature = temperature.toDouble()),
                automaticToolCalling = false,
                maxOutputToken = maxTokens,
                thinkingConfig = ThinkingConfig(thinking),
            )
            return LiveConversation(engine.createConversation(config), toLiteRtMessage(latest))
        }

        override fun close() = engine.close()
    }

    /**
     * [latest] is the only message sent through `sendMessageAsync`; [priorMessages] already went in
     * as [ConversationConfig.initialMessages] when the conversation was created (TASK-118: Bruce
     * hands the whole history every turn, like [com.bizzeh.bruce.inference.LlamaCppEngine] does,
     * rather than keeping one engine-held conversation alive across turns).
     */
    private class LiveConversation(private val conversation: com.google.ai.edge.litertlm.Conversation, private val latest: Message) : LiteRtConversation {
        override fun send(): Flow<LiteRtEvent> = flow {
            val content = StringBuilder()
            val reasoning = StringBuilder()
            var toolCalls = emptyList<BruceToolCall>()
            conversation.sendMessageAsync(latest).collect { message ->
                val reasoningPiece = message.channels[REASONING_CHANNEL].orEmpty()
                if (reasoningPiece.isNotEmpty()) {
                    reasoning.append(reasoningPiece)
                    emit(LiteRtEvent.Reasoning(reasoningPiece))
                }
                val textPiece = message.plainText()
                if (textPiece.isNotEmpty()) {
                    content.append(textPiece)
                    emit(LiteRtEvent.Text(textPiece))
                }
                if (message.toolCalls.isNotEmpty()) toolCalls = message.toolCalls.map(::toBruceToolCall)
            }
            emit(LiteRtEvent.Done(content.toString(), reasoning.toString(), toolCalls))
        }.catch { emit(LiteRtEvent.Failed) }

        override fun cancel() = conversation.cancelProcess()

        override fun tokenCount(): Int = conversation.getTokenCount()

        override fun close() = conversation.close()
    }

    private fun Message.plainText(): String = contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    private fun toBruceToolCall(call: NativeToolCall): BruceToolCall =
        // No id: LiteRT-LM's ToolCall carries none; BruceRuntime numbers tool calls that arrive without one.
        BruceToolCall(call.name, JSONObject(call.arguments).toString(), id = "")

    private fun toLiteRtMessage(message: ToolChatMessage): Message = when (message.role) {
        ChatRole.SYSTEM -> Message.system(message.content)
        ChatRole.USER -> Message.user(message.content)
        ChatRole.ASSISTANT -> Message.model(Contents.of(message.content), message.toolCalls.map(::toNativeToolCall), emptyMap())
        ChatRole.TOOL -> Message.tool(Contents.of(listOf(Content.ToolResponse(message.toolName, message.content))))
    }

    private fun toNativeToolCall(call: BruceToolCall): NativeToolCall = NativeToolCall(call.name, jsonToMap(call.argumentsJson))

    private fun jsonToMap(json: String): Map<String, Any?> = try {
        val obj = JSONObject(json)
        obj.keys().asSequence().associateWith { obj.get(it) }
    } catch (e: org.json.JSONException) {
        emptyMap()
    }

    /** A skill's definition and schema (OpenAPI JSON), with no real [execute]: automatic tool calling is off (`ConversationConfig.automaticToolCalling = false`), so LiteRT-LM must never call this itself. */
    private class BruceTool(private val definition: ToolDefinition) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = JSONObject()
            .put("name", definition.name)
            .put("description", definition.description)
            .put("parameters", JSONObject(definition.parametersJson))
            .toString()

        override fun execute(paramsJsonString: String): String =
            JSONObject().put("error", "automatic tool execution is disabled; this must not be called").toString()
    }

    private const val REASONING_CHANNEL = "thought"
    private const val DEFAULT_TOP_K = 40
    private const val DEFAULT_TOP_P = 0.95
}
