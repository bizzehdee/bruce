package com.bizzeh.bruce.inference

import org.json.JSONException
import org.json.JSONObject

/**
 * Bruce's own tool-call format, for models whose chat template cannot express tools (ADR 0001):
 * the tools are described in the system prompt and the model replies with
 * `<tool_call>{"name": …, "arguments": {…}}</tool_call>`, constrained by a lazy grammar.
 * Measured as option C in TASK-033.
 */
object BruceToolFormat {
    const val OPEN = "<tool_call>"
    const val CLOSE = "</tool_call>"

    private val CALL = Regex(Regex.escape(OPEN) + "(.*?)(?:" + Regex.escape(CLOSE) + "|$)", RegexOption.DOT_MATCHES_ALL)

    /** The tool part of the system prompt, with each tool's full schema. */
    fun instructions(tools: List<ToolDefinition>): String = buildString {
        append("You can call these tools:\n<tools>\n")
        tools.forEach { tool ->
            append(JSONObject().put("name", tool.name).put("description", tool.description).put("parameters", JSONObject(tool.parametersJson)))
            append('\n')
        }
        append("</tools>\n\n")
        append("When a tool would help answer, reply with only this and nothing else:\n")
        append("$OPEN\n{\"name\": \"<tool name>\", \"arguments\": {<arguments as JSON>}}\n$CLOSE\n")
        append("When no tool is needed, answer the user directly.")
    }

    /**
     * Splits a reply into text and at most one tool call. A tag whose contents are not a
     * name-and-arguments object is left in the text rather than guessed at.
     */
    fun parse(text: String): ParsedReply {
        val match = CALL.find(text) ?: return ParsedReply(text.trim(), "", emptyList())
        val call = try {
            val json = JSONObject(match.groupValues[1].trim())
            ToolCall(json.getString("name"), json.opt("arguments")?.toString() ?: "{}")
        } catch (e: JSONException) {
            return ParsedReply(text.trim(), "", emptyList())
        }
        return ParsedReply(text.removeRange(match.range).trim(), "", listOf(call))
    }
}
