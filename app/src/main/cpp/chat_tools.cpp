// Tool-call formats through llama.cpp's own chat code (docs/adr/0001-tool-calling-format.md):
// format a conversation with tools in the model's template, and parse a reply into text and calls.
// Requests and replies cross JNI as UTF-8 JSON bytes (.learnings/jni-text-as-utf8-bytes.md).

#include <jni.h>

#include <android/log.h>

#include <exception>
#include <string>
#include <vector>

#include <nlohmann/json.hpp>

#include "chat.h"
#include "json-schema-to-grammar.h"
#include "llama.h"

namespace {

using json = nlohmann::ordered_json;

constexpr const char *kLogTag = "BruceChat";

std::string bytesToString(JNIEnv *env, jbyteArray bytes) {
    const jsize length = env->GetArrayLength(bytes);
    std::string text(static_cast<size_t>(length), '\0');
    env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(text.data()));
    return text;
}

jbyteArray stringToBytes(JNIEnv *env, const std::string &text) {
    jbyteArray result = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return result;
}

// Template and model text is not ours to trust; an unexpected failure is logged by kind only.
void logFailure(const char *what, const std::exception &error) {
    __android_log_print(ANDROID_LOG_WARN, kLogTag, "%s failed: %s", what, error.what());
}

common_chat_msg toMessage(const json &item) {
    common_chat_msg message;
    message.role = item.at("role").get<std::string>();
    message.content = item.value("content", "");
    message.tool_call_id = item.value("tool_call_id", "");
    message.tool_name = item.value("tool_name", "");
    if (item.contains("tool_calls")) {
        for (const auto &call : item.at("tool_calls")) {
            message.tool_calls.push_back({call.at("name").get<std::string>(), call.at("arguments").get<std::string>(), call.value("id", "")});
        }
    }
    return message;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_chatTemplatesInit(JNIEnv *, jobject, jlong model) {
    try {
        return reinterpret_cast<jlong>(common_chat_templates_init(reinterpret_cast<llama_model *>(model), "").release());
    } catch (const std::exception &error) {
        logFailure("chat template init", error);
        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_chatTemplatesFree(JNIEnv *, jobject, jlong templates) {
    common_chat_templates_free(reinterpret_cast<common_chat_templates *>(templates));
}

// Request: {"messages": [{"role", "content", "tool_calls"?: [{"name", "arguments", "id"}], "tool_call_id"?}],
//           "tools": [{"name", "description", "parameters": <JSON schema>}], "enable_thinking": bool}
// Reply: {"prompt", "format", "parser", "generation_prompt", "grammar", "grammar_lazy",
//         "grammar_triggers": [{"type", "value"}], "preserved_tokens", "additional_stops", "supports_tools"}
// or null if the template cannot be applied.
JNIEXPORT jbyteArray JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_applyChat(JNIEnv *env, jobject, jlong templates, jbyteArray request) {
    try {
        const auto *tmpls = reinterpret_cast<common_chat_templates *>(templates);
        const json in = json::parse(bytesToString(env, request));
        common_chat_templates_inputs inputs;
        for (const auto &item : in.at("messages")) inputs.messages.push_back(toMessage(item));
        for (const auto &tool : in.value("tools", json::array())) {
            inputs.tools.push_back({tool.at("name").get<std::string>(), tool.value("description", ""), tool.at("parameters").dump()});
        }
        inputs.use_jinja = true;
        inputs.add_generation_prompt = true;
        inputs.parallel_tool_calls = false;
        inputs.enable_thinking = in.value("enable_thinking", true);
        const common_chat_params params = common_chat_templates_apply(tmpls, inputs);

        json triggers = json::array();
        for (const auto &trigger : params.grammar_triggers) {
            triggers.push_back({{"type", static_cast<int>(trigger.type)}, {"value", trigger.value}});
        }
        const auto caps = common_chat_templates_get_caps(tmpls);
        const auto tools = caps.find("supports_tool_calls");
        const json out = {
                {"prompt", params.prompt},
                {"format", static_cast<int>(params.format)},
                {"parser", params.parser},
                {"generation_prompt", params.generation_prompt},
                {"grammar", params.grammar},
                {"grammar_lazy", params.grammar_lazy},
                {"grammar_triggers", triggers},
                {"preserved_tokens", params.preserved_tokens},
                {"additional_stops", params.additional_stops},
                {"supports_tools", tools != caps.end() && tools->second},
        };
        return stringToBytes(env, out.dump(-1, ' ', false, json::error_handler_t::replace));
    } catch (const std::exception &error) {
        logFailure("apply chat template", error);
        return nullptr;
    }
}

// Request: {"text", "partial": bool, "format", "parser", "generation_prompt"} as applyChat returned them.
// Reply: {"content", "reasoning_content", "tool_calls": [{"name", "arguments", "id"}]}, or null if unparsable.
JNIEXPORT jbyteArray JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_parseChat(JNIEnv *env, jobject, jbyteArray request) {
    try {
        const json in = json::parse(bytesToString(env, request));
        common_chat_parser_params params;
        params.format = static_cast<common_chat_format>(in.at("format").get<int>());
        params.generation_prompt = in.value("generation_prompt", "");
        params.reasoning_format = COMMON_REASONING_FORMAT_NONE;
        const std::string parser = in.value("parser", "");
        if (!parser.empty()) params.parser.load(parser);
        const common_chat_msg message = common_chat_parse(in.at("text").get<std::string>(), in.value("partial", false), params);

        json calls = json::array();
        for (const auto &call : message.tool_calls) {
            calls.push_back({{"name", call.name}, {"arguments", call.arguments}, {"id", call.id}});
        }
        const json out = {{"content", message.content}, {"reasoning_content", message.reasoning_content}, {"tool_calls", calls}};
        return stringToBytes(env, out.dump(-1, ' ', false, json::error_handler_t::replace));
    } catch (const std::exception &error) {
        logFailure("parse reply", error);
        return nullptr;
    }
}

// Whether a chat template, given as text with its BOS and EOS tokens, can express tool calls, as
// llama.cpp decides for a loaded model (TASK-061): 1 yes, 0 no, -1 if the template cannot be read.
// Lets the model browser judge a Hub file from its reported template without downloading it.
JNIEXPORT jint JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_templateSupportsTools(JNIEnv *env, jobject, jbyteArray templateUtf8, jbyteArray bosUtf8, jbyteArray eosUtf8) {
    try {
        const std::string source = bytesToString(env, templateUtf8);
        if (source.empty()) {
            return -1;
        }
        const auto templates = common_chat_templates_init(nullptr, source, bytesToString(env, bosUtf8), bytesToString(env, eosUtf8));
        const auto caps = common_chat_templates_get_caps(templates.get());
        const auto tools = caps.find("supports_tool_calls");
        return tools != caps.end() && tools->second ? 1 : 0;
    } catch (const std::exception &error) {
        logFailure("template check", error);
        return -1;
    }
}

// Bruce's own format, for models whose template has no tool support: a GBNF grammar for
// <tool_call>{"name": ..., "arguments": {...}}</tool_call>, with each tool's arguments held to its
// schema. Request: [{"name", "parameters": <JSON schema>}]. Returns the grammar, or null.
JNIEXPORT jbyteArray JNICALL
Java_com_bizzeh_bruce_inference_LlamaNative_toolCallGrammar(JNIEnv *env, jobject, jbyteArray request) {
    try {
        const json tools = json::parse(bytesToString(env, request));
        json alternatives = json::array();
        for (const auto &tool : tools) {
            alternatives.push_back({
                    {"type", "object"},
                    {"properties", {{"name", {{"const", tool.at("name")}}}, {"arguments", tool.at("parameters")}}},
                    {"required", {"name", "arguments"}},
                    {"additionalProperties", false},
            });
        }
        const json schema = {{"anyOf", alternatives}};
        std::string grammar = json_schema_to_grammar(common_json::parse(schema.dump()), true);
        // The schema's own root becomes the call; the new root adds the tags around it.
        const std::string root = "root ::= ";
        size_t at = grammar.rfind(root, 0) == 0 ? 0 : grammar.find("\n" + root);
        if (at == std::string::npos) {
            return nullptr;
        }
        if (at != 0) {
            at += 1;
        }
        grammar.replace(at, root.size(), "tool-call ::= ");
        grammar += "\nroot ::= \"<tool_call>\" space tool-call space \"</tool_call>\"\n";
        if (grammar.find("\nspace ::= ") == std::string::npos && grammar.rfind("space ::= ", 0) != 0) {
            grammar += "space ::= | \" \" | \"\\n\"{1,2} [ \\t]{0,20}\n";
        }
        return stringToBytes(env, grammar);
    } catch (const std::exception &error) {
        logFailure("tool call grammar", error);
        return nullptr;
    }
}

}  // extern "C"
