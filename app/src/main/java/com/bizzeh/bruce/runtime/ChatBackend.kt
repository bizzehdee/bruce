package com.bizzeh.bruce.runtime

import android.util.Log
import com.bizzeh.bruce.inference.BruceToolFormat
import com.bizzeh.bruce.inference.ChatMessage
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.GenerationError
import com.bizzeh.bruce.inference.GenerationEvent
import com.bizzeh.bruce.inference.GenerationRequest
import com.bizzeh.bruce.inference.GenerationStats
import com.bizzeh.bruce.inference.InferenceEngine
import com.bizzeh.bruce.inference.ParsedReply
import com.bizzeh.bruce.inference.RgbImage
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.ToolDefinition
import com.bizzeh.bruce.inference.ToolFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** What one model step is asked with: the system prompt as the runtime wrote it, the conversation and the skills offered. */
/** [savable]: [system] and [tools] hold nothing about the user, so their evaluated state may be kept on storage (TASK-109). */
/** [thinking] lets a model that can reason before answering do so (TASK-110); side passes leave it off. */
data class BackendChat(val system: String, val messages: List<ToolChatMessage>, val tools: List<ToolDefinition>, val savable: Boolean = false, val thinking: Boolean = false)

sealed interface Prepared {
    /** Ready to generate; [tokens] is what the prompt takes of the context. */
    class Ready(val chat: BackendChat, val tokens: Int, internal val handle: Any?) : Prepared

    data object NoModel : Prepared

    /** The model's template could not format this conversation. */
    data object FormatFailed : Prepared
}

sealed interface StepEvent {
    /** Raw text as it streams; reasoning included, marked as the model marks it. */
    data class Text(val text: String) : StepEvent

    /** The step finished; tool calls may have no id yet (the runtime numbers them). */
    data class Done(val reply: ParsedReply, val stats: GenerationStats?) : StepEvent

    data class Failed(val error: RuntimeError) : StepEvent
}

/** Images attached to chats, by stored name (TASK-082); ChatImages on a phone. */
interface ImageSource {
    fun rgb(name: String): RgbImage?

    fun jpeg(name: String): ByteArray?
}

/**
 * Where model steps come from (TASK-076, owner's option A): the model on the phone ([LocalBackend])
 * or a remote server. The runtime's loop, policy checks, confirmations, memory and summaries are
 * the same for both.
 */
interface ChatBackend {
    suspend fun contextLength(): Int?

    suspend fun prepare(chat: BackendChat): Prepared

    /**
     * [branchOf] is a conversation that continues the same chat differently, when a pass branches
     * off it (TASK-047); a backend that keeps the evaluated prompt can checkpoint what they share.
     */
    fun generate(prepared: Prepared.Ready, maxTokens: Int, temperature: Float, branchOf: BackendChat? = null): Flow<StepEvent>

    fun stop()

    /** Whether the model can be given images (TASK-082). */
    suspend fun acceptsImages(): Boolean = false
}

/**
 * The model on the phone: the model's own chat template and tool format through llama.cpp, or
 * Bruce's format where the template has no tool support (ADR 0001), with the evaluated prompt reused.
 */
class LocalBackend(
    private val engine: InferenceEngine,
    private val images: ImageSource? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ChatBackend {
    /** [images] are the names behind the prompt's media markers, in order. */
    private data class Prompt(val text: String, val format: ToolFormat, val images: List<String> = emptyList())

    /** Context tokens per image and model; decoding an image to count it is slow. */
    private val imageTokens = mutableMapOf<Pair<String, String?>, Int?>()

    override suspend fun acceptsImages(): Boolean = engine.mediaMarker() != null

    override suspend fun contextLength(): Int? = engine.contextLength()

    override suspend fun prepare(chat: BackendChat): Prepared {
        val prompt = prompt(chat) ?: return if (engine.contextLength() == null) Prepared.NoModel else Prepared.FormatFailed
        val tokens = engine.countTokens(prompt.text) ?: return Prepared.NoModel
        return Prepared.Ready(chat, tokens + prompt.images.sumOf { tokensFor(it) ?: 0 }, prompt)
    }

    override fun generate(prepared: Prepared.Ready, maxTokens: Int, temperature: Float, branchOf: BackendChat?): Flow<StepEvent> = flow {
        val prompt = prepared.handle as Prompt
        val shared = branchOf?.let { other -> prompt(other)?.text?.let(prompt.text::commonPrefixWith) }
        val text = StringBuilder()
        var shown = ""
        var stats: GenerationStats? = null
        var failure: RuntimeError? = null
        val pixels = withContext(io) { prompt.images.mapNotNull { images?.rgb(it) } }
        if (pixels.size != prompt.images.size) {
            emit(StepEvent.Failed(RuntimeError.GENERATION_FAILED))
            return@flow
        }
        val request = GenerationRequest(
            prompt.text, maxTokens = maxTokens, temperature = temperature, grammar = prompt.format.grammar, stops = prompt.format.stops,
            checkpointPrefix = shared.takeIf { pixels.isEmpty() }, images = pixels,
            savedPrefix = if (engine.savesPrompts && prepared.chat.savable && pixels.isEmpty()) fixedPrefix(prepared.chat) else null,
        )
        engine.generate(request).collect { event ->
            when (event) {
                is GenerationEvent.Token -> {
                    text.append(event.text)
                    val visible = visibleText(prompt.format, text.toString())
                    if (visible != null && visible.length > shown.length && visible.startsWith(shown)) {
                        emit(StepEvent.Text(visible.substring(shown.length)))
                        shown = visible
                    }
                }
                is GenerationEvent.Completed -> stats = event.stats
                is GenerationEvent.Failed -> failure = when (event.error) {
                    GenerationError.NO_MODEL_LOADED -> RuntimeError.NO_MODEL_LOADED
                    GenerationError.PROMPT_TOO_LONG -> RuntimeError.CONVERSATION_TOO_LONG
                    GenerationError.DECODE_FAILED, GenerationError.GRAMMAR_REJECTED, GenerationError.IMAGES_UNUSABLE -> RuntimeError.GENERATION_FAILED
                }
            }
        }
        val failed = failure
        if (failed != null) emit(StepEvent.Failed(failed)) else emit(StepEvent.Done(parse(prompt.format, text.toString()), stats))
    }

    override fun stop() = engine.stop()

    /**
     * The text every prompt of [chat]'s conversation starts with: the system prompt and tools as the
     * template lays them out, up to where the first user message's words begin.
     */
    private suspend fun fixedPrefix(chat: BackendChat): String? {
        val text = prompt(chat.copy(messages = listOf(ToolChatMessage(ChatRole.USER, FIXED_END))))?.text ?: return null
        return text.substringBefore(FIXED_END, missingDelimiterValue = "").ifEmpty { null }
    }

    /** The model's own tool format when its template supports tools, otherwise Bruce's (ADR 0001). */
    private suspend fun prompt(chat: BackendChat): Prompt? {
        val system = ToolChatMessage(ChatRole.SYSTEM, chat.system)
        val (conversation, shown) = withImages(chat.messages.map(::withReadableArguments))
        val native = engine.formatToolChat(listOf(system) + conversation, chat.tools, enableThinking = chat.thinking) ?: return null
        if (chat.tools.isEmpty() || native.format.supportsTools) return Prompt(native.text, native.format, shown)

        val grammar = engine.bruceToolGrammar(chat.tools)
        val fallbackSystem = ChatMessage(ChatRole.SYSTEM, chat.system + "\n\n" + BruceToolFormat.instructions(chat.tools))
        val formatted = engine.formatChat(listOf(fallbackSystem) + conversation.map(::bruceMessage)) ?: return null
        return Prompt(formatted.text, ToolFormat(format = BRUCE_FORMAT, parser = "", generationPrompt = "", supportsTools = false, grammar = grammar, stops = emptyList()), shown)
    }

    /**
     * Messages with their images as the model's media markers, and the names shown, in order. A
     * marker the user typed is removed, so markers and images always match. An image the model
     * cannot be given (no vision projector, or the file has gone) is described instead.
     */
    private suspend fun withImages(messages: List<ToolChatMessage>): Pair<List<ToolChatMessage>, List<String>> {
        if (messages.none { it.images.isNotEmpty() }) return messages to emptyList()
        val marker = engine.mediaMarker()
        val shown = mutableListOf<String>()
        val result = messages.map { message ->
            val content = marker?.let { message.content.replace(it, "") } ?: message.content
            if (message.images.isEmpty()) return@map message.copy(content = content)
            val usable = message.images.filter { marker != null && tokensFor(it) != null }
            shown += usable
            val missing = message.images.size - usable.size
            val note = if (missing > 0) "(${if (missing == 1) "An image was" else "$missing images were"} attached that you cannot see.)\n" else ""
            message.copy(content = marker.orEmpty().repeat(usable.size) + (if (usable.isNotEmpty()) "\n" else "") + note + content, images = usable)
        }
        return result to shown
    }

    private suspend fun tokensFor(name: String): Int? = imageTokens.getOrPut(name to engine.getModelInfo()?.description) {
        val image = withContext(io) { images?.rgb(name) } ?: return@getOrPut null.also { Log.w(TAG, "image unreadable; the model is told it cannot see it") }
        engine.imageTokens(image) ?: null.also { Log.w(TAG, "the vision projector could not take a ${image.width}x${image.height} image") }
    }

    /**
     * What may be shown of a reply still being written: its text without any tool call, so the call's
     * markup never flashes up before the reply is parsed (owner, 2026-10-02). Null while a partial
     * reply cannot be parsed; the text then waits for more tokens.
     */
    private fun visibleText(format: ToolFormat, text: String): String? =
        if (format.format == BRUCE_FORMAT) BruceToolFormat.visible(text) else engine.parseReply(format, text, partial = true)?.content

    private fun parse(format: ToolFormat, text: String): ParsedReply =
        if (format.format == BRUCE_FORMAT) BruceToolFormat.parse(text) else engine.parseReply(format, text) ?: ParsedReply(text, "", emptyList())

    /**
     * A call whose arguments are not a JSON object goes back to the model as `{}`: templates parse
     * the arguments and fail on bad JSON, which would break every later turn of the chat (seen with
     * Llama 3.2 1B). The call's result already tells the model its arguments were invalid.
     */
    private fun withReadableArguments(message: ToolChatMessage): ToolChatMessage {
        if (message.toolCalls.all { isJsonObject(it.argumentsJson) }) return message
        return message.copy(toolCalls = message.toolCalls.map { if (isJsonObject(it.argumentsJson)) it else it.copy(argumentsJson = "{}") })
    }

    /** Bruce's format as plain chat: calls written back in its tags, results as the user's turn. */
    private fun bruceMessage(message: ToolChatMessage): ChatMessage = when (message.role) {
        ChatRole.SYSTEM -> ChatMessage(ChatRole.SYSTEM, message.content)
        ChatRole.USER -> ChatMessage(ChatRole.USER, message.content)
        ChatRole.ASSISTANT -> ChatMessage(
            ChatRole.ASSISTANT,
            (listOf(message.content) + message.toolCalls.map { "${BruceToolFormat.OPEN}{\"name\": ${JSONObject.quote(it.name)}, \"arguments\": ${it.argumentsJson}}${BruceToolFormat.CLOSE}" })
                .filter { it.isNotEmpty() }.joinToString("\n"),
        )
        ChatRole.TOOL -> ChatMessage(ChatRole.USER, "<tool_response>\n${message.content}\n</tool_response>")
    }

    companion object {
        private const val TAG = "BruceBackend"

        /** Stands in for the first user message's words when finding where the fixed prompt ends. */
        private const val FIXED_END = "BRUCE_FIXED_PROMPT_END_7f3a"

        /** Marks a prompt formatted in Bruce's own format rather than one of llama.cpp's. */
        private const val BRUCE_FORMAT = -1

        internal fun isJsonObject(text: String): Boolean = try {
            JSONObject(text)
            true
        } catch (e: JSONException) {
            false
        }
    }
}
