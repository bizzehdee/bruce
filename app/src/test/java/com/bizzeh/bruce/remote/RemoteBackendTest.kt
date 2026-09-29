package com.bizzeh.bruce.remote

import com.bizzeh.bruce.huggingface.HttpResponse
import com.bizzeh.bruce.huggingface.HttpTransport
import com.bizzeh.bruce.huggingface.StreamingResponse
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.ToolCall
import com.bizzeh.bruce.inference.ToolChatMessage
import com.bizzeh.bruce.inference.ToolDefinition
import com.bizzeh.bruce.runtime.BackendChat
import com.bizzeh.bruce.runtime.Prepared
import com.bizzeh.bruce.runtime.RuntimeError
import com.bizzeh.bruce.runtime.StepEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.IOException

/** Robolectric for Android's org.json. */
@RunWith(RobolectricTestRunner::class)
class RemoteBackendTest {
    private var status = 200
    private var stream = javaClass.getResource("/remote/stream-tool-call.txt")!!.readText()
    private var props = """{"default_generation_settings":{"n_ctx":4096}}"""
    private var unreachable = false
    private val posted = mutableListOf<Triple<String, Map<String, String>, String>>()
    private val gets = mutableListOf<String>()

    private val transport = object : HttpTransport {
        override fun get(url: String, headers: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")
        override fun open(url: String, headers: Map<String, String>): StreamingResponse = error("the key must not follow redirects")
        override fun postForm(url: String, headers: Map<String, String>, form: Map<String, String>, maxBytes: Int): HttpResponse = error("not used")
        override fun openSingle(url: String, headers: Map<String, String>): StreamingResponse {
            gets += url
            if (unreachable) throw IOException("down")
            return StreamingResponse(if (props.isEmpty()) 404 else 200, emptyMap(), ByteArrayInputStream(props.toByteArray())) {}
        }
        override fun postStream(url: String, headers: Map<String, String>, body: ByteArray, readTimeoutMs: Int): StreamingResponse {
            posted += Triple(url, headers, String(body))
            if (unreachable) throw IOException("down")
            return StreamingResponse(status, emptyMap(), ByteArrayInputStream(stream.toByteArray())) {}
        }
    }
    private val server = RemoteServer("a", "Home", "http://192.168.1.20:8080", true, true)
    private val backend = RemoteBackend(server, "qwen3", "k", transport, Dispatchers.Unconfined, "Bruce/test")
    private val clock = ToolDefinition("get_datetime", "Get the date and time", """{"type":"object","properties":{}}""")
    private val chat = BackendChat(
        "You are Bruce.",
        listOf(
            ToolChatMessage(ChatRole.USER, "Hi"),
            ToolChatMessage(ChatRole.ASSISTANT, "", toolCalls = listOf(ToolCall("get_datetime", "{}", "c1"))),
            ToolChatMessage(ChatRole.TOOL, "{\"status\":\"ok\"}", toolCallId = "c1", toolName = "get_datetime"),
            ToolChatMessage(ChatRole.USER, "What time is it?"),
        ),
        listOf(clock),
    )

    private fun run(prepared: Prepared.Ready = runBlocking { backend.prepare(chat) as Prepared.Ready }) = runBlocking { backend.generate(prepared, 200, 0.3f).toList() }

    @Test
    fun theContextSizeComesFromTheServerOnce() = runBlocking {
        assertEquals(4096, backend.contextLength())
        assertEquals(4096, backend.contextLength())
        assertEquals(listOf("http://192.168.1.20:8080/props"), gets)
        props = ""
        assertEquals(RemoteBackend.FALLBACK_CONTEXT, RemoteBackend(server, "m", null, transport, Dispatchers.Unconfined, "x").contextLength())
        props = "not json"
        assertEquals(RemoteBackend.FALLBACK_CONTEXT, RemoteBackend(server, "m", null, transport, Dispatchers.Unconfined, "x").contextLength())
        unreachable = true
        assertEquals(RemoteBackend.FALLBACK_CONTEXT, RemoteBackend(server, "m", null, transport, Dispatchers.Unconfined, "x").contextLength())
    }

    @Test
    fun theConversationGoesAsOpenAiMessagesWithThinkingOff() {
        run()

        val (url, headers, body) = posted.single()
        assertEquals("http://192.168.1.20:8080/v1/chat/completions", url)
        assertEquals("Bearer k", headers["Authorization"])
        val json = JSONObject(body)
        assertEquals("qwen3", json.getString("model"))
        assertEquals(false, json.getJSONObject("chat_template_kwargs").getBoolean("enable_thinking"))
        val messages = json.getJSONArray("messages")
        assertEquals(listOf("system", "user", "assistant", "tool", "user"), (0 until messages.length()).map { messages.getJSONObject(it).getString("role") })
        assertEquals("c1", messages.getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0).getString("id"))
        assertEquals("c1", messages.getJSONObject(3).getString("tool_call_id"))
        assertEquals("get_datetime", json.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"))
    }

    @Test
    fun aStreamedToolCallIsGatheredWithReasoningShownAsThinking() {
        val events = run()

        val text = events.filterIsInstance<StepEvent.Text>().joinToString("") { it.text }
        assertTrue(text.startsWith("<think>The user is asking"))
        val done = events.last() as StepEvent.Done
        assertEquals(1, done.reply.toolCalls.size)
        assertEquals("get_datetime", done.reply.toolCalls.single().name)
        assertEquals("m0GVx0xVtXwiY9v54bzN7gyIST6npfM2", done.reply.toolCalls.single().id)
        assertTrue(done.reply.reasoning.startsWith("The user is asking"))
        assertEquals(258, done.stats!!.promptTokens)
    }

    @Test
    fun theServersUsageCorrectsLaterEstimates() = runBlocking {
        val before = (backend.prepare(chat) as Prepared.Ready).tokens
        run()
        val after = (backend.prepare(chat) as Prepared.Ready).tokens
        assertTrue("before $before, after $after", after != before && after > 0)
    }

    @Test
    fun textRepliesAndFailures() {
        stream = listOf(
            """data: {"choices":[{"delta":{"content":"It is "}}]}""",
            "data: not json",
            ": keep-alive",
            """data: {"choices":[{"delta":{"content":"noon."}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":""}}]}}]}""",
            "data: [DONE]",
        ).joinToString("\n")
        val done = run().last() as StepEvent.Done
        assertEquals("It is noon.", done.reply.content)
        assertEquals(emptyList<ToolCall>(), done.reply.toolCalls)

        status = 400
        stream = """{"error":{"message":"the request exceeds the available context size"}}"""
        assertEquals(StepEvent.Failed(RuntimeError.CONVERSATION_TOO_LONG), run().single())
        status = 500
        assertEquals(StepEvent.Failed(RuntimeError.GENERATION_FAILED), run().single())
        unreachable = true
        assertEquals(StepEvent.Failed(RuntimeError.SERVER_UNREACHABLE), run().single())
        backend.stop()
    }
}
