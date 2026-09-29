#include "template_one_line.h"

#include <cctype>

namespace bruce {

namespace {

constexpr const char *kWord = "tojson";
constexpr size_t kWordLength = 6;

bool isNameChar(char c) { return std::isalnum(static_cast<unsigned char>(c)) || c == '_'; }

size_t skipSpaces(const std::string &text, size_t at) {
    while (at < text.size() && std::isspace(static_cast<unsigned char>(text[at]))) ++at;
    return at;
}

// The end of "(indent=N)" starting at the '(' at [open], or 0 if the call is anything else.
size_t indentOnlyCallEnd(const std::string &text, size_t open) {
    size_t at = skipSpaces(text, open + 1);
    if (text.compare(at, 6, "indent") != 0) return 0;
    at = skipSpaces(text, at + 6);
    if (at >= text.size() || text[at] != '=') return 0;
    at = skipSpaces(text, at + 1);
    const size_t digits = at;
    while (at < text.size() && std::isdigit(static_cast<unsigned char>(text[at]))) ++at;
    if (at == digits) return 0;
    at = skipSpaces(text, at);
    return at < text.size() && text[at] == ')' ? at + 1 : 0;
}

}  // namespace

// A plain scan, not std::regex: templates are untrusted and can be large, and std::regex recurses.
std::string oneLineToolJson(const std::string &chatTemplate) {
    std::string result;
    result.reserve(chatTemplate.size());
    size_t copied = 0;
    size_t found = chatTemplate.find(kWord);
    while (found != std::string::npos) {
        const size_t end = found + kWordLength;
        const bool whole = (found == 0 || !isNameChar(chatTemplate[found - 1])) && (end >= chatTemplate.size() || !isNameChar(chatTemplate[end]));
        const size_t open = skipSpaces(chatTemplate, end);
        const size_t callEnd = whole && open < chatTemplate.size() && chatTemplate[open] == '(' ? indentOnlyCallEnd(chatTemplate, open) : 0;
        if (callEnd != 0) {
            result.append(chatTemplate, copied, end - copied);
            copied = callEnd;
        }
        found = chatTemplate.find(kWord, end);
    }
    result.append(chatTemplate, copied, std::string::npos);
    return result;
}

}  // namespace bruce
