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
}
