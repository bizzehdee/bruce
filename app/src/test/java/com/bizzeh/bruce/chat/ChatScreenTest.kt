package com.bizzeh.bruce.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.ui.theme.BruceTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = object : ChatActions {
        override fun setInput(input: String) { calls += "input $input" }
        override fun send() { calls += "send" }
        override fun stop() { calls += "stop" }
        override fun decide(callId: String, approved: Boolean) { calls += "decide $callId $approved" }
    }

    private fun show(state: ChatState) = compose.setContent { BruceTheme { ChatScreen(state, actions) } }

    @Test
    fun summariesAreMarkedAndSummarisingIsShown() {
        val entries = listOf(ChatEntry(ChatRole.USER, "old"), ChatEntry(ChatRole.SYSTEM, "They talked about dogs."), ChatEntry(ChatRole.USER, "new"))
        show(ChatState(modelName = "m", entries = entries, summarising = true, generating = true))

        compose.onNodeWithText("Summary: the model now sees this instead of the messages above").assertIsDisplayed()
        compose.onNodeWithText("They talked about dogs.").assertIsDisplayed()
        compose.onNodeWithTag("summarising").assertIsDisplayed()
    }

    @Test
    fun contextBarShowsUseMarksDroppedMessagesAndExplains() {
        val entries = listOf(ChatEntry(ChatRole.USER, "old"), ChatEntry(ChatRole.ASSISTANT, "reply"), ChatEntry(ChatRole.USER, "new"))
        show(ChatState(modelName = "m", entries = entries, context = com.bizzeh.bruce.runtime.ContextUse(used = 900, total = 1000, dropped = 2, limit = 1000), firstSeen = 2))

        compose.onNodeWithText("900 of 1,000 tokens used · 100 free").assertIsDisplayed()
        compose.onNodeWithTag("contextNearlyFull", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("contextDropped").assertIsDisplayed()
        compose.onNodeWithTag("contextBar").performClick()
        compose.onNodeWithText("Context").assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("Context").assertDoesNotExist()
    }

    @Test
    fun contextBarIsQuietWhenThereIsRoom() {
        show(ChatState(modelName = "m", context = com.bizzeh.bruce.runtime.ContextUse(used = 100, total = 1000, dropped = 0)))

        compose.onNodeWithTag("contextNearlyFull", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("contextDropped").assertDoesNotExist()
    }

    @Test
    fun approvalCardShowsWhatWouldRunAndTakesTheAnswer() {
        val awaiting = ToolUse("c1", "get_datetime", "{}", ToolStatus.AWAITING_APPROVAL)
        val old = ToolUse("c0", "get_datetime", "{}", ToolStatus.AWAITING_APPROVAL)
        show(
            ChatState(
                modelName = "m",
                entries = listOf(ChatEntry(ChatRole.TOOL, "{}", tool = old), ChatEntry(ChatRole.TOOL, "{}", tool = awaiting)),
                confirmations = mapOf("c1" to Confirmation("c1", "get_datetime", listOf("path" to "Notes/a.txt"), listOf("Notes/a.txt"))),
            ),
        )

        compose.onNodeWithTag("confirmExpired:c0").assertIsDisplayed()
        compose.onNodeWithTag("approve:c0").assertDoesNotExist()
        assertEquals(2, compose.onAllNodesWithText("Allow Date and time?").fetchSemanticsNodes().size)
        compose.onNodeWithText("On: Notes/a.txt").assertIsDisplayed()
        compose.onNodeWithText("path: Notes/a.txt").assertIsDisplayed()
        compose.onNodeWithTag("approve:c1").performClick()
        compose.onNodeWithTag("deny:c1").performClick()
        assertEquals(listOf("decide c1 true", "decide c1 false"), calls)
    }

    @Test
    fun withoutModelAskForOneAndDisableSend() {
        show(ChatState(input = "hi"))

        compose.onNodeWithText("No model loaded").assertIsDisplayed()
        compose.onNodeWithText("Load a model to start chatting.").assertIsDisplayed()
        compose.onNodeWithTag("send").assertIsNotEnabled()
    }

    @Test
    fun typingReportsInput() {
        show(ChatState(modelName = "qwen3"))

        compose.onNodeWithText("I'm Bruce. What can I help with?").assertIsDisplayed()
        compose.onNodeWithTag("composer").performTextInput("Hello")

        assertEquals(listOf("input Hello"), calls)
    }

    @Test
    fun sending() {
        show(ChatState(modelName = "qwen3", input = "Hello"))

        compose.onNodeWithTag("send").performClick()

        assertEquals(listOf("send"), calls)
    }

    @Test
    fun conversationWithCollapsedReasoning() {
        show(
            ChatState(
                modelName = "qwen3",
                entries = listOf(ChatEntry(ChatRole.USER, "Hi"), ChatEntry(ChatRole.ASSISTANT, "<think>User says hi.</think>\n\nHello!")),
            ),
        )

        compose.onNodeWithText("Hi").assertIsDisplayed()
        compose.onNodeWithTag("answer:1").assertIsDisplayed()
        compose.onNodeWithTag("reasoning:1").assertDoesNotExist()
        compose.onNodeWithTag("reasoningToggle:1").performClick()
        compose.onNodeWithTag("reasoning:1").assertIsDisplayed()
        compose.onNodeWithText("Hide reasoning").assertIsDisplayed()
    }

    @Test
    fun whileThinkingShowsThinkingAndStop() {
        show(
            ChatState(
                modelName = "qwen3",
                entries = listOf(ChatEntry(ChatRole.USER, "Hi"), ChatEntry(ChatRole.ASSISTANT, "<think>Hmm")),
                generating = true,
            ),
        )

        compose.onNodeWithText("Bruce is thinking…").assertIsDisplayed()
        compose.onNodeWithTag("stop").performClick()

        assertEquals(listOf("stop"), calls)
    }

    @Test
    fun errorIsShown() {
        show(ChatState(modelName = "qwen3", error = ChatError.CONVERSATION_TOO_LONG))

        compose.onNodeWithTag("chatError").assertIsDisplayed()
    }

    @Test
    fun skillUseIsNotShownOnlyTheAnswer() {
        val call = com.bizzeh.bruce.inference.ToolCall("get_datetime", "{}", "c1")
        show(
            ChatState(
                modelName = "qwen3",
                entries = listOf(
                    ChatEntry(ChatRole.USER, "What time is it?"),
                    ChatEntry(ChatRole.ASSISTANT, "", toolCalls = listOf(call)),
                    ChatEntry(ChatRole.TOOL, "", tool = ToolUse("c1", "get_datetime", """{"status":"ok","untrusted_data":"Monday, 14:37"}""", ToolStatus.RAN)),
                    ChatEntry(ChatRole.TOOL, "", tool = ToolUse("c2", "read_file", """{"status":"denied","message":"The user has turned this skill off."}""", ToolStatus.REFUSED)),
                    ChatEntry(ChatRole.ASSISTANT, "It is 14:37."),
                ),
            ),
        )

        compose.onNodeWithTag("reply:1").assertDoesNotExist()
        compose.onNodeWithText("Monday, 14:37", substring = true).assertDoesNotExist()
        compose.onNodeWithText("read_file", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("answer:4").assertIsDisplayed()
    }

    @Test
    fun repliesAreFormattedLinksAskFirstAndImagesLoadOnlyWhenAsked() {
        val opened = mutableListOf<String>()
        val loads = mutableListOf<String>()
        val markdownActions = object : ChatActions by actions {
            override fun openLink(url: String) { opened += url }
            override suspend fun loadImage(url: String): ImageResult {
                loads += url
                return ImageResult.NotAllowed
            }
        }
        val reply = "## Plan\n\n```\ncode here\n```\n\nSee [the docs](https://example.com/docs).\n\n![diagram](https://example.com/d.png)"
        compose.setContent { BruceTheme { ChatScreen(ChatState(modelName = "m", entries = listOf(ChatEntry(ChatRole.USER, "Hi"), ChatEntry(ChatRole.ASSISTANT, reply))), markdownActions) } }

        compose.onNodeWithText("Plan").assertIsDisplayed()
        compose.onNodeWithTag("codeBlock").assertIsDisplayed()
        compose.onNodeWithText("```", substring = true).assertDoesNotExist()
        compose.onNodeWithText("diagram").assertIsDisplayed()
        assertEquals("nothing is fetched on its own", emptyList<String>(), loads)

        compose.onNodeWithText("See the docs.").performFirstLinkClick()
        compose.onNodeWithTag("linkAddress").assertTextEquals("https://example.com/docs")
        assertEquals(emptyList<String>(), opened)
        compose.onNodeWithTag("openLink").performClick()
        assertEquals(listOf("https://example.com/docs"), opened)

        compose.onNodeWithTag("loadImage").performClick()
        compose.onNodeWithTag("imageNotAllowed").assertIsDisplayed()
        compose.onNodeWithTag("loadImage").assertDoesNotExist()
        assertEquals(listOf("https://example.com/d.png"), loads)
    }

    @Test
    fun theMicrophoneShowsOnlyWithAnOnDeviceRecogniser() {
        var state by androidx.compose.runtime.mutableStateOf(ChatState(modelName = "m"))
        val voiceActions = object : ChatActions by actions {
            override fun startVoice() { calls += "voice" }
            override fun stopVoice() { calls += "stop voice" }
        }
        compose.setContent { BruceTheme { ChatScreen(state, voiceActions) } }

        compose.onNodeWithTag("voice").assertDoesNotExist()
        state = state.copy(voice = VoiceState.IDLE)
        compose.onNodeWithTag("voice").performClick()
        state = state.copy(voice = VoiceState.LISTENING)
        compose.onNodeWithText("Listening…").assertExists()
        compose.onNodeWithTag("voice").performClick()

        assertEquals(listOf("voice", "stop voice"), calls)
    }
}
