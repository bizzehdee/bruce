package com.bizzeh.bruce.remote

import android.util.Log
import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.huggingface.StreamingResponse
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.GenerationStats
import com.bizzeh.bruce.inference.ParsedReply
import com.bizzeh.bruce.inference.ToolCall
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.runtime.BackendChat
import com.bizzeh.bruce.runtime.ChatBackend
import com.bizzeh.bruce.runtime.ImageSource
import com.bizzeh.bruce.runtime.Prepared
import com.bizzeh.bruce.runtime.RuntimeError
import com.bizzeh.bruce.runtime.StepEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Base64
import kotlin.time.Duration.Companion.milliseconds

/**
 * A model on a remote server (TASK-076), through its OpenAI-compatible chat API: the server applies
 * the model's template and parses tool calls; skills still run on the phone. Thinking is asked off,
 * as on the phone. Token counts are estimated from text length, corrected by what the server
 * reports it used, since not every server can count tokens on request.
 */
class RemoteBackend(
    private val server: RemoteServer,
    private val model: String,
    private val key: String?,
    private val transport: HttpTransport,
    private val io: CoroutineDispatcher,
    private val userAgent: String,
    private val images: ImageSource? = null,
) : ChatBackend {
    /** What llama-server's /props says: its context size, and whether it was started with a vision projector. */
    private data class Props(val context: Int, val vision: Boolean)

    private var props: Props? = null

    /** Characters per token, from the server's own usage reports; a common English figure until then. */
    @Volatile private var charsPerToken = DEFAULT_CHARS_PER_TOKEN

    @Volatile private var open: StreamingResponse? = null

    override suspend fun contextLength(): Int = props().context

    /** Only llama-server says so, in /props (`modalities.vision`); other servers are not sent images. */
    override suspend fun acceptsImages(): Boolean = props().vision

    private suspend fun props(): Props = props ?: readProps().also { props = it }

    /** llama-server's /props; other servers are assumed to take [FALLBACK_CONTEXT] and no images. */
    private suspend fun readProps(): Props = kotlinx.coroutines.withContext(io) {
        val unknown = Props(FALLBACK_CONTEXT, vision = false)
        try {
            transport.openSingle("${server.address}/props", headers()).use { response ->
                if (response.status != 200) return@withContext unknown
                val json = JSONObject(response.body.reader().readText())
                Props(
                    context = json.optJSONObject("default_generation_settings")?.optInt("n_ctx")?.takeIf { it > 0 } ?: FALLBACK_CONTEXT,
                    vision = json.optJSONObject("modalities")?.optBoolean("vision") == true,
                )
            }
        } catch (e: IOException) {
            unknown
        } catch (e: JSONException) {
            unknown
        }
    }

    override suspend fun prepare(chat: BackendChat): Prepared {
        val characters = chat.system.length + chat.messages.sumOf { it.content.length + it.toolCalls.sumOf { call -> call.name.length + call.argumentsJson.length } } +
            chat.tools.sumOf { it.name.length + it.description.length + it.parametersJson.length }
        val tokens = (characters / charsPerToken).toInt() + PER_MESSAGE_TOKENS * (chat.messages.size + 1) +
            IMAGE_TOKENS * chat.messages.sumOf { it.images.size }
        return Prepared.Ready(chat, tokens, characters)
    }

    override fun generate(prepared: Prepared.Ready, maxTokens: Int, temperature: Float, branchOf: BackendChat?): Flow<StepEvent> = flow {
        val vision = prepared.chat.messages.any { it.images.isNotEmpty() } && acceptsImages()
        val body = request(prepared.chat, maxTokens, temperature, vision).toString().toByteArray(Charsets.UTF_8)
        val response = try {
            transport.postStream("${server.address}/v1/chat/completions", headers() + ("Content-Type" to "application/json"), body, FIRST_TOKEN_TIMEOUT_MS)
        } catch (e: IOException) {
            // The failure's kind only: its message can name the address, and never the chat.
            Log.w(TAG, "request failed: ${e.javaClass.simpleName}")
            emit(StepEvent.Failed(RuntimeError.SERVER_UNREACHABLE))
            return@flow
        }
        open = response
        try {
            if (response.status != 200) {
                val detail = response.body.reader().readText().take(MAX_ERROR_CHARS)
                emit(StepEvent.Failed(if (response.status == 400 && "context" in detail.lowercase()) RuntimeError.CONVERSATION_TOO_LONG else RuntimeError.GENERATION_FAILED))
                return@flow
            }
            val stream = Stream()
            response.body.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    stream.read(data)?.let { emit(StepEvent.Text(it)) }
                }
            }
            stream.usage?.let { (promptTokens, _) -> if (promptTokens > 0) charsPerToken = (prepared.handle as Int).toDouble() / promptTokens }
            emit(StepEvent.Done(stream.reply(), stream.stats))
        } catch (e: IOException) {
            // Stopping closes the connection; the reply so far is what the user asked to keep.
            Log.w(TAG, "stream ended: ${e.javaClass.simpleName}")
            emit(StepEvent.Failed(RuntimeError.SERVER_UNREACHABLE))
        } finally {
            open = null
            response.close()
        }
    }.flowOn(io)

    override fun stop() {
        open?.close()
    }

    private fun headers(): Map<String, String> = buildMap {
        put("Accept", "application/json, text/event-stream")
        put("User-Agent", userAgent)
        key?.let { put("Authorization", "Bearer $it") }
    }

    private fun request(chat: BackendChat, maxTokens: Int, temperature: Float, vision: Boolean): JSONObject = JSONObject()
        .put("model", model)
        .put("stream", true)
        .put("stream_options", JSONObject().put("include_usage", true))
        .put("max_tokens", maxTokens)
        .put("temperature", temperature.toDouble())
        .put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
        .put("messages", JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", chat.system))
            chat.messages.forEach { put(message(it, vision)) }
        })
        .apply {
            if (chat.tools.isNotEmpty()) {
                put("tools", JSONArray(chat.tools.map { tool ->
                    JSONObject().put("type", "function").put(
                        "function",
                        JSONObject().put("name", tool.name).put("description", tool.description).put("parameters", JSONObject(tool.parametersJson)),
                    )
                }))
            }
        }

    private fun message(message: ToolChatMessage, vision: Boolean): JSONObject = when (message.role) {
        ChatRole.SYSTEM -> JSONObject().put("role", "system").put("content", message.content)
        ChatRole.USER -> JSONObject().put("role", "user").put("content", userContent(message, vision))
        ChatRole.ASSISTANT -> JSONObject().put("role", "assistant").put("content", message.content).apply {
            if (message.toolCalls.isNotEmpty()) {
                put("tool_calls", JSONArray(message.toolCalls.map { call ->
                    JSONObject().put("id", call.id).put("type", "function").put("function", JSONObject().put("name", call.name).put("arguments", call.argumentsJson))
                }))
            }
        }
        ChatRole.TOOL -> JSONObject().put("role", "tool").put("tool_call_id", message.toolCallId).put("content", message.content)
    }

    /**
     * Text alone, or with its images as OpenAI image parts (JPEG data URLs) when the server can see
     * them. Otherwise, or for an image that has gone, the text says an image was attached.
     */
    private fun userContent(message: ToolChatMessage, vision: Boolean): Any {
        if (message.images.isEmpty()) return message.content
        val jpegs = if (vision) message.images.mapNotNull { images?.jpeg(it) } else emptyList()
        val missing = message.images.size - jpegs.size
        val note = if (missing > 0) "(${if (missing == 1) "An image was" else "$missing images were"} attached that you cannot see.)\n" else ""
        if (jpegs.isEmpty()) return note + message.content
        return JSONArray().apply {
            jpegs.forEach { put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(it)))) }
            put(JSONObject().put("type", "text").put("text", note + message.content))
        }
    }

    /** One reply as it streams: text, reasoning (shown as the chat shows a model's thinking) and tool calls by index. */
    private class Stream {
        private val content = StringBuilder()
        private val reasoning = StringBuilder()
        private val calls = sortedMapOf<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>()
        private var thinking = false
        var usage: Pair<Int, Int>? = null
        var stats: GenerationStats? = null

        /** The chunk's text to show, if any; its tool-call pieces are gathered. */
        fun read(data: String): String? {
            val chunk = try {
                JSONObject(data)
            } catch (e: JSONException) {
                return null
            }
            chunk.optJSONObject("usage")?.let { usage = it.optInt("prompt_tokens") to it.optInt("completion_tokens") }
            chunk.optJSONObject("timings")?.let { timings ->
                stats = GenerationStats(timings.optInt("prompt_n"), timings.optDouble("prompt_ms").milliseconds, timings.optInt("predicted_n"), timings.optDouble("predicted_ms").milliseconds)
            }
            val delta = chunk.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: return null
            val shown = StringBuilder()
            delta.optString("reasoning_content").takeIf { it.isNotEmpty() && delta.has("reasoning_content") && !delta.isNull("reasoning_content") }?.let {
                if (!thinking) shown.append(THINK_OPEN)
                thinking = true
                reasoning.append(it)
                shown.append(it)
            }
            delta.optString("content").takeIf { delta.has("content") && !delta.isNull("content") && it.isNotEmpty() }?.let {
                if (thinking) shown.append(THINK_CLOSE)
                thinking = false
                content.append(it)
                shown.append(it)
            }
            delta.optJSONArray("tool_calls")?.let { pieces ->
                for (i in 0 until pieces.length()) {
                    val piece = pieces.optJSONObject(i) ?: continue
                    val (id, name, arguments) = calls.getOrPut(piece.optInt("index", i)) { Triple(StringBuilder(), StringBuilder(), StringBuilder()) }
                    piece.optString("id").takeIf { piece.has("id") && !piece.isNull("id") }?.let { id.append(it) }
                    piece.optJSONObject("function")?.let { function ->
                        function.optString("name").takeIf { function.has("name") && !function.isNull("name") }?.let { name.append(it) }
                        function.optString("arguments").takeIf { function.has("arguments") && !function.isNull("arguments") }?.let { arguments.append(it) }
                    }
                }
            }
            return shown.toString().ifEmpty { null }
        }

        fun reply(): ParsedReply = ParsedReply(
            content.toString(),
            reasoning.toString(),
            calls.values.filter { it.second.isNotEmpty() }.map { (id, name, arguments) -> ToolCall(name.toString(), arguments.toString().ifBlank { "{}" }, id.toString()) },
        )
    }

    companion object {
        /** Assumed when a server does not say its context size. */
        const val FALLBACK_CONTEXT = 8192
        private const val DEFAULT_CHARS_PER_TOKEN = 3.5
        private const val PER_MESSAGE_TOKENS = 4

        /** A rough figure per image until the server's usage report corrects the estimate. */
        private const val IMAGE_TOKENS = 256

        /** Long prompts on a slow server take a while before the first token. */
        private const val FIRST_TOKEN_TIMEOUT_MS = 180_000
        private const val MAX_ERROR_CHARS = 2000
        private const val THINK_OPEN = "<think>"
        private const val THINK_CLOSE = "</think>"
        private const val TAG = "BruceRemote"
    }
}
