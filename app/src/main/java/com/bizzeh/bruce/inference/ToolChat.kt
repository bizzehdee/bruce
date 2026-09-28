package com.bizzeh.bruce.inference

import org.json.JSONArray
import org.json.JSONObject

/** A skill as the model's template describes it: [parametersJson] is a JSON Schema object. */
data class ToolDefinition(val name: String, val description: String, val parametersJson: String)

/** A tool call the model made; [argumentsJson] is the model's raw JSON text, still untrusted. */
data class ToolCall(val name: String, val argumentsJson: String, val id: String = "")

enum class ToolChatRole(val wireName: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool"),
}

/** A conversation message with room for tool calls (assistant) and tool results (tool). */
data class ToolChatMessage(
    val role: ToolChatRole,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String = "",
    val toolName: String = "",
)

/**
 * How a prompt was formatted, which [InferenceEngine.parseReply] and a grammar-constrained
 * generation need back. [grammar] is null when the format has no grammar; [supportsTools] is false
 * when the model's template cannot express tools and Bruce's own format must be used (ADR 0001).
 */
data class ToolFormat(
    val format: Int,
    val parser: String,
    val generationPrompt: String,
    val supportsTools: Boolean,
    val grammar: ToolGrammar?,
    val stops: List<String>,
)

/** A tool-call grammar with its lazy triggers and preserved tokens, as the native layer takes it. */
data class ToolGrammar(val json: String) {
    companion object {
        /** Bruce's own `<tool_call>` format: the grammar applies once the model writes the opening tag. */
        fun bruceFormat(gbnf: String): ToolGrammar = ToolGrammar(
            JSONObject()
                .put("grammar", gbnf)
                .put("grammar_lazy", true)
                .put("grammar_triggers", JSONArray().put(JSONObject().put("type", TRIGGER_WORD).put("value", BruceToolFormat.OPEN)))
                .put("preserved_tokens", JSONArray().put(BruceToolFormat.OPEN).put(BruceToolFormat.CLOSE))
                .toString(),
        )

        /** common_grammar_trigger_type in llama.cpp's common.h. */
        private const val TRIGGER_WORD = 1
    }
}

data class ToolChatPrompt(val text: String, val format: ToolFormat)

/** A reply split into what the user reads and the tool calls it asks for. */
data class ParsedReply(val content: String, val reasoning: String, val toolCalls: List<ToolCall>)
