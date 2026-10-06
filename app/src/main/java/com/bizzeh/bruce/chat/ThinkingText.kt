package com.bizzeh.bruce.chat

/**
 * A reply split into the model's reasoning and its answer. Reasoning models wrap their reasoning
 * before the answer, each family in its own tags: Qwen3 in `<think>…</think>`, Gemma 4 in
 * `<|channel>thought…<channel|>`, Mistral in `[THINK]…[/THINK]`.
 */
data class ThinkingText(val thinking: String?, val answer: String, val stillThinking: Boolean) {
    companion object {
        private val MARKERS = listOf(
            "<think>" to "</think>",
            "<|channel>thought" to "<channel|>",
            "[THINK]" to "[/THINK]",
            "<thinking>" to "</thinking>",
            "<seed:think>" to "</seed:think>",
            "<|START_THINKING|>" to "<|END_THINKING|>",
        )

        fun split(raw: String): ThinkingText {
            val trimmed = raw.trimStart()
            val (open, close) = MARKERS.firstOrNull { trimmed.startsWith(it.first) } ?: return ThinkingText(null, raw, stillThinking = false)
            val afterOpen = trimmed.substring(open.length)
            val end = afterOpen.indexOf(close)
            if (end < 0) return ThinkingText(afterOpen.trim(), "", stillThinking = true)
            return ThinkingText(
                thinking = afterOpen.substring(0, end).trim().ifEmpty { null },
                answer = afterOpen.substring(end + close.length).trimStart(),
                stillThinking = false,
            )
        }
    }
}
