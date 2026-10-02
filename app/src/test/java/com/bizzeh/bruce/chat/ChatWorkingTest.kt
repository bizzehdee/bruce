package com.bizzeh.bruce.chat

import com.bizzeh.bruce.inference.ChatRole
import com.bizzeh.bruce.inference.ToolCall
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatWorkingTest {
    private val asked = ChatEntry(ChatRole.USER, "What time is it?")

    private fun state(vararg entries: ChatEntry, generating: Boolean = true, summarising: Boolean = false) =
        ChatState(entries = entries.toList(), generating = generating, summarising = summarising)

    @Test
    fun workingUntilTheReplyHasSomethingToShow() {
        assertTrue(state(asked, ChatEntry(ChatRole.ASSISTANT, "")).working)
        assertTrue(state(asked, ChatEntry(ChatRole.ASSISTANT, "<think>\n\n</think>\n\n")).working, "an empty think block shows nothing")
        assertFalse(state(asked, ChatEntry(ChatRole.ASSISTANT, "It is noon.")).working)
    }

    @Test
    fun workingWhileASkillRunsOrBeforeAnyReply() {
        val call = ToolCall("get_datetime", "{}", "call_1")
        assertTrue(state(asked, ChatEntry(ChatRole.ASSISTANT, "", toolCalls = listOf(call)), ChatEntry(ChatRole.TOOL, "{}", tool = ToolUse("call_1", "get_datetime", "{}", ToolStatus.RAN))).working)
        assertTrue(state(asked).working)
        assertTrue(state().working)
    }

    @Test
    fun notWorkingWhenIdleSummarisingOrShowingReasoning() {
        assertFalse(state(asked, ChatEntry(ChatRole.ASSISTANT, ""), generating = false).working)
        assertFalse(state(asked, summarising = true).working, "summarising has its own note")
        assertFalse(state(asked, ChatEntry(ChatRole.ASSISTANT, "<think>Let me see")).working, "the reasoning button already says it is thinking")
    }
}
