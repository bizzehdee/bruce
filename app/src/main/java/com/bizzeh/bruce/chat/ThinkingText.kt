package com.bizzeh.bruce.chat

/**
 * A reply split into the model's reasoning and its answer. Reasoning models such as Qwen3
 * wrap their reasoning in `<think>…</think>` before the answer.
 */
data class ThinkingText(val thinking: String?, val answer: String, val stillThinking: Boolean) {
    companion object {
        private const val OPEN = "<think>"
        private const val CLOSE = "</think>"

        fun split(raw: String): ThinkingText {
            val trimmed = raw.trimStart()
            if (!trimmed.startsWith(OPEN)) return ThinkingText(null, raw, stillThinking = false)
            val afterOpen = trimmed.substring(OPEN.length)
            val close = afterOpen.indexOf(CLOSE)
            if (close < 0) return ThinkingText(afterOpen.trim(), "", stillThinking = true)
            return ThinkingText(
                thinking = afterOpen.substring(0, close).trim().ifEmpty { null },
                answer = afterOpen.substring(close + CLOSE.length).trimStart(),
                stillThinking = false,
            )
        }
    }
}
