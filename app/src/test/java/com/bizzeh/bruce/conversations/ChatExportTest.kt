package com.bizzeh.bruce.conversations

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.bizzeh.bruce.chat.ChatEntry
import com.bizzeh.bruce.chat.ToolStatus
import com.bizzeh.bruce.chat.ToolUse
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.ToolCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.StringWriter
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ChatExportTest {
    private val export = ChatExport(ZoneOffset.UTC)

    // 2026-09-30 10:00 and 11:30 UTC.
    private val started = 1_790_762_400_000L
    private val lastMessage = 1_790_767_800_000L

    private val call = ToolCall("get_datetime", "{\"zone\":\"local\"}", "call-1")
    private val chat = ExportedChat(
        "What time is it?", started, lastMessage, archived = false,
        listOf(
            ChatEntry(ChatRole.USER, "What time is it?"),
            ChatEntry(ChatRole.ASSISTANT, "<think>The user wants the time.</think>", toolCalls = listOf(call)),
            ChatEntry(ChatRole.TOOL, "{\"time\":\"10:00\"}", tool = ToolUse("call-1", "get_datetime", "{\"time\":\"10:00\"}", ToolStatus.RAN)),
            ChatEntry(ChatRole.SYSTEM, "The user asked the time."),
            ChatEntry(ChatRole.ASSISTANT, "<think>Answer.</think>It's 10:00."),
        ),
    )

    private fun write(format: ExportFormat, vararg chats: ExportedChat): Pair<Int, String> = runBlocking {
        val out = StringWriter()
        export.write(format, flowOf(*chats), out, lastMessage) to out.toString()
    }

    @Test
    fun markdownShowsTheConversationWithoutReasoning() {
        val (count, text) = write(ExportFormat.MARKDOWN, chat, chat.copy(title = " ", archived = true, entries = emptyList()))

        assertEquals(2, count)
        assertEquals(
            """
            Exported from Bruce on 2026-09-30 11:30.

            ---

            # What time is it?

            Started 2026-09-30 10:00, last message 2026-09-30 11:30.

            **You:**

            What time is it?

            **Bruce:**


            - Asked to use `get_datetime` with ` {"zone":"local"} `

            *Result of `get_datetime` (ran):*

            ```
            {"time":"10:00"}
            ```

            **Summary of earlier messages:**

            The user asked the time.

            **Bruce:**

            It's 10:00.

            ---

            # Untitled chat

            Started 2026-09-30 10:00, last message 2026-09-30 11:30. Archived.

            """.trimIndent(),
            text,
        )
    }

    @Test
    fun codeInResultsCannotCloseItsFence() {
        val result = "a ``` b ```` c"
        val entry = ChatEntry(ChatRole.TOOL, result, tool = ToolUse("1", "read_file", result, ToolStatus.RAN))

        val text = write(ExportFormat.MARKDOWN, chat.copy(entries = listOf(entry))).second

        assertTrue(text, text.contains("`````\na ``` b ```` c\n`````"))
    }

    @Test
    fun jsonKeepsEverythingTheChatHolds() {
        val (count, text) = write(ExportFormat.JSON, chat)

        assertEquals(1, count)
        val root = JSONObject(text)
        assertEquals(ChatExport.JSON_FORMAT, root.getString("format"))
        assertEquals(ChatExport.JSON_VERSION, root.getInt("version"))
        assertEquals("2026-09-30T11:30:00Z", root.getString("exportedAt"))
        val exported = root.getJSONArray("conversations").getJSONObject(0)
        assertEquals("2026-09-30T10:00:00Z", exported.getString("createdAt"))
        assertFalse(exported.getBoolean("archived"))
        val messages = exported.getJSONArray("messages")
        assertEquals(5, messages.length())
        val asked = messages.getJSONObject(1)
        assertEquals("assistant", asked.getString("role"))
        assertEquals("<think>The user wants the time.</think>", asked.getString("text"))
        assertEquals("{\"zone\":\"local\"}", asked.getJSONArray("toolCalls").getJSONObject(0).getString("arguments"))
        val result = messages.getJSONObject(2).getJSONObject("tool")
        assertEquals("ran", result.getString("status"))
        assertEquals("{\"time\":\"10:00\"}", result.getString("result"))
        assertEquals("system", messages.getJSONObject(3).getString("role"))
    }

    @Test
    fun imagesAreCountedButNotExported() {
        val withImages = chat.copy(entries = listOf(ChatEntry(ChatRole.USER, "What is this?", images = listOf("a.jpg", "b.jpg"))))

        assertTrue(write(ExportFormat.MARKDOWN, withImages).second.contains("**You:**\n\n*(2 images, not exported)*\n\nWhat is this?"))
        val message = JSONObject(write(ExportFormat.JSON, withImages).second).getJSONArray("conversations").getJSONObject(0).getJSONArray("messages").getJSONObject(0)
        assertEquals(2, message.getInt("imageCount"))
        assertFalse(message.toString().contains("a.jpg"))
    }

    @Test
    fun documentsAreNamedInMarkdownAndKeptInJson() {
        val withDocument = chat.copy(entries = listOf(ChatEntry(ChatRole.USER, "Summarise", documents = listOf(com.bizzeh.bruce.chat.ChatDocument("k.pdf", "code 4417")))))

        assertTrue(write(ExportFormat.MARKDOWN, withDocument).second.contains("*(Document: k.pdf)*\n\nSummarise"))
        val document = JSONObject(write(ExportFormat.JSON, withDocument).second).getJSONArray("conversations").getJSONObject(0)
            .getJSONArray("messages").getJSONObject(0).getJSONArray("documents").getJSONObject(0)
        assertEquals("code 4417", document.getString("text"))
    }

    @Test
    fun anEmptyExportIsStillValidJson() {
        val (count, text) = write(ExportFormat.JSON)

        assertEquals(0, count)
        assertEquals(0, JSONObject(text).getJSONArray("conversations").length())
    }

    @Test
    fun fileNamesComeFromTheTitleOrTheDate() {
        assertEquals("Plans for Tuesday.md", export.fileName(ExportFormat.MARKDOWN, "Plans for Tuesday", lastMessage))
        assertEquals("a b c.json", export.fileName(ExportFormat.JSON, "a/b\\c", lastMessage))
        assertEquals("Bruce chats 2026-09-30.json", export.fileName(ExportFormat.JSON, " ?* ", lastMessage))
        assertEquals("Bruce chats 2026-09-30.md", export.fileName(ExportFormat.MARKDOWN, null, lastMessage))
        assertEquals("hidden", ChatExport.safeFileName("..hidden"))
        assertEquals(60, ChatExport.safeFileName("x".repeat(100)).length)
    }
}

/** The export from a real (in-memory) chat database to the picked document. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConversationExportTest {
    private val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ConversationDatabase::class.java).build()
    private val store = ConversationStore(database.conversations()) { 1_790_762_400_000L }
    private val written = ByteArrayOutputStream()
    private val opened = mutableListOf<String>()

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    // Main is set once per test: setting it again while an earlier view model's flows run fails.
    private fun viewModel(open: (String) -> OutputStream? = { opened += it; written }) =
        ConversationsViewModel(store, {}, open, { 1_790_762_400_000L }, ChatExport(ZoneOffset.UTC), Dispatchers.Unconfined)

    private fun outcome(vm: ConversationsViewModel) = runBlocking { withTimeout(5_000) { vm.exportOutcome.filterNotNull().first() } }

    @Test
    fun exportsTheChosenChatsToThePickedDocument() {
        val (kept, other, archived) = runBlocking {
            Triple(store.save(null, listOf(ChatEntry(ChatRole.USER, "keep me"))), store.save(null, listOf(ChatEntry(ChatRole.USER, "not me"))), store.save(null, listOf(ChatEntry(ChatRole.USER, "old"))))
        }
        runBlocking { store.archive(listOf(archived)) }
        val vm = viewModel()
        runBlocking { withTimeout(5_000) { vm.active.filterNotNull().first { it.size == 2 } } }

        assertEquals("keep me.md", vm.requestExport(ExportRequest(setOf(kept), ExportFormat.MARKDOWN)))
        vm.exportTo("content://picked/1")

        assertEquals(ExportOutcome.Exported(1), outcome(vm))
        assertEquals(listOf("content://picked/1"), opened)
        val text = written.toString(Charsets.UTF_8)
        assertTrue(text, text.contains("keep me"))
        assertFalse(text, text.contains("not me"))

        vm.exportOutcomeShown()
        written.reset()
        assertEquals("Bruce chats 2026-09-30.json", vm.requestExport(ExportRequest(null, ExportFormat.JSON)))
        vm.exportTo("content://picked/2")
        assertEquals("archived chats are included", ExportOutcome.Exported(3), outcome(vm))
        assertEquals(3, JSONObject(written.toString(Charsets.UTF_8)).getJSONArray("conversations").length())
        assertTrue(other > 0)
    }

    @Test
    fun cancellingThePickerWritesNothing() {
        val vm = viewModel()
        vm.requestExport(ExportRequest(null, ExportFormat.JSON))

        vm.exportTo(null)
        vm.exportTo("content://late")

        assertEquals(emptyList<String>(), opened)
        assertEquals(null, vm.exportOutcome.value)
    }

    @Test
    fun aDocumentThatCannotBeWrittenReportsFailure() {
        val cases: List<(String) -> OutputStream?> = listOf(
            { null },
            { throw SecurityException("no access") },
            { object : OutputStream() { override fun write(b: Int) = throw IOException("disk full") } },
        )
        runBlocking { store.save(null, listOf(ChatEntry(ChatRole.USER, "hello"))) }
        cases.forEach { open ->
            val vm = viewModel(open)
            vm.requestExport(ExportRequest(null, ExportFormat.MARKDOWN))
            vm.exportTo("content://picked")
            assertEquals(ExportOutcome.Failed, outcome(vm))
        }
    }
}
