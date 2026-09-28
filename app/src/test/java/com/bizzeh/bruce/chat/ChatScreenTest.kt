package com.bizzeh.bruce.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.ui.theme.BruceTheme
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
    }

    private fun show(state: ChatState) = compose.setContent { BruceTheme { ChatScreen(state, actions) } }

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

        compose.onNodeWithText("What can I help with?").assertIsDisplayed()
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

        compose.onNodeWithText("Thinking…").assertIsDisplayed()
        compose.onNodeWithTag("stop").performClick()

        assertEquals(listOf("stop"), calls)
    }

    @Test
    fun errorIsShown() {
        show(ChatState(modelName = "qwen3", error = ChatError.CONVERSATION_TOO_LONG))

        compose.onNodeWithTag("chatError").assertIsDisplayed()
    }

    @Test
    fun skillUsesShowAsRowsThatOpenToWhatTheModelWasGiven() {
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
        compose.onNodeWithText("Used get_datetime").assertIsDisplayed()
        compose.onNodeWithText("Not allowed: read_file").assertIsDisplayed()
        compose.onNodeWithTag("toolToggle:2").performClick()
        compose.onNodeWithText("Monday, 14:37").assertIsDisplayed()
        compose.onNodeWithTag("toolToggle:3").performClick()
        compose.onNodeWithText("The user has turned this skill off.").assertIsDisplayed()
        compose.onNodeWithTag("answer:4").assertIsDisplayed()
        assertEquals("not JSON: shown as is", "raw", ChatText.toolDetail("raw"))
        assertEquals("{}", ChatText.toolDetail("{}"))
    }
}
