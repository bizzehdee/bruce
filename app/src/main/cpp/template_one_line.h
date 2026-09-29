#pragma once

#include <string>

namespace bruce {

// A chat template that prints its JSON (tool definitions, call arguments) on one line: every
// `tojson(indent=N)` becomes plain `tojson`, which keeps a space after "," and ":". Llama 3.2's
// template prints tools with indent=4; on one line Bruce's empty-chat prompt drops from 2,009 to
// 1,639 tokens, and Llama 3.2 1B called tools as well or better. Fully compact JSON (no spaces)
// made it copy the definitions instead of calling (research/experiments/compact-tool-json-2026-09-29).
std::string oneLineToolJson(const std::string &chatTemplate);

}  // namespace bruce
