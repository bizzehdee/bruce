package com.bizzeh.bruce.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThinkingTextTest {
    @Test
    fun plainReplyHasNoReasoning() {
        assertEquals(ThinkingText(null, "Hello there.", stillThinking = false), ThinkingText.split("Hello there."))
    }

    @Test
    fun completedReasoningIsSeparatedFromTheAnswer() {
        assertEquals(
            ThinkingText("The user greets me.", "Hello!", stillThinking = false),
            ThinkingText.split("<think>\nThe user greets me.\n</think>\n\nHello!"),
        )
    }

    @Test
    fun reasoningStillStreaming() {
        assertEquals(ThinkingText("Let me see", "", stillThinking = true), ThinkingText.split("  <think>\nLet me see"))
    }

    @Test
    fun emptyReasoningIsDropped() {
        assertEquals(ThinkingText(null, "Hi", stillThinking = false), ThinkingText.split("<think>\n\n</think>\n\nHi"))
    }

    @Test
    fun thinkTagLaterInTheReplyIsJustText() {
        val raw = "Use <think> tags like this."
        assertEquals(ThinkingText(null, raw, stillThinking = false), ThinkingText.split(raw))
    }

    @Test
    fun gemmaChannelReasoningIsSeparatedFromTheAnswer() {
        assertEquals(
            ThinkingText("Plan the reply.", "Hello!", stillThinking = false),
            ThinkingText.split("<|channel>thought\nPlan the reply.<channel|>Hello!"),
        )
    }

    @Test
    fun gemmaChannelReasoningStillStreaming() {
        assertEquals(ThinkingText("Plan (", "", stillThinking = true), ThinkingText.split("<|channel>thought\nPlan ("))
    }

    @Test
    fun otherFamiliesTagsAreRecognised() {
        listOf("[THINK]" to "[/THINK]", "<thinking>" to "</thinking>", "<seed:think>" to "</seed:think>", "<|START_THINKING|>" to "<|END_THINKING|>").forEach { (open, close) ->
            assertEquals(ThinkingText("Plan.", "Hi", stillThinking = false), ThinkingText.split("${open}Plan.${close}Hi"))
        }
    }
}
