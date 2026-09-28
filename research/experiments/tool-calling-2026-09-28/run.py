#!/usr/bin/env python3
"""Scores tool-calling formats against a running llama-server (TASK-033).

Usage: run.py <server-url> <label> <out.jsonl> [variant ...]
Set BRUCE_TC_PHONE=1 to run only the cases marked "phone" (the slow on-device subset).
Variants: native, prompt, grammar, index (default: all four).

native  - the model's own chat-template tool format, via /v1/chat/completions with `tools`
          (llama.cpp common/chat: template-specific prompt, lazy grammar and parser).
prompt  - one Bruce-defined format described in the system prompt, no grammar.
grammar - the Bruce format plus a GBNF grammar that switches on at "<tool_call>".
index   - as grammar, but each tool is one line (name, argument names, description) instead of
          its JSON schema: the short skill index Bruce would load up front.
"""
import ast
import json
import os
import operator
import re
import sys
import time
import urllib.request

HERE = __file__.rsplit("/", 1)[0]
SPEC = json.load(open(f"{HERE}/cases.json"))
TOOLS = SPEC["tools"]
CASES = [c for c in SPEC["cases"] if c.get("phone") or not os.environ.get("BRUCE_TC_PHONE")]
MAX_TOKENS = 160

PERSONA = "You are Bruce, a helpful assistant running on the user's Android phone. Be brief."



def bruce_system(tool_lines):
    return (
        PERSONA
        + "\n\nYou can call these tools:\n<tools>\n"
        + "\n".join(tool_lines)
        + "\n</tools>\n\n"
        "When a tool would help answer, reply with only this and nothing else:\n"
        "<tool_call>\n{\"name\": \"<tool name>\", \"arguments\": {<arguments as JSON>}}\n</tool_call>\n"
        "When no tool is needed, answer the user directly."
    )


def index_line(tool):
    arguments = ", ".join(tool["parameters"]["properties"])
    return f"{tool['name']}({arguments}): {tool['description']}"


BRUCE_SYSTEM = bruce_system(json.dumps(t) for t in TOOLS)
INDEX_SYSTEM = bruce_system(index_line(t) for t in TOOLS)


def gbnf_for(tools):
    rules = []
    alternatives = []
    for i, tool in enumerate(tools):
        props = tool["parameters"]["properties"]
        if props:
            pairs = ' ws "," ws '.join(f'"\\"{name}\\"" ws ":" ws string' for name in props)
            args = f'"{{" ws {pairs} ws "}}"'
        else:
            args = '"{" ws "}"'
        rules.append(f'call{i} ::= "{{" ws "\\"name\\"" ws ":" ws "\\"{tool["name"]}\\"" ws "," ws "\\"arguments\\"" ws ":" ws {args} ws "}}"')
        alternatives.append(f"call{i}")
    return "\n".join(
        [
            'root ::= "<tool_call>" ws call ws "</tool_call>"',
            "call ::= " + " | ".join(alternatives),
            *rules,
            'string ::= "\\"" ( [^"\\\\\\x7F\\x00-\\x1F] | "\\\\" ["\\\\/bfnrt] )* "\\""',
            "ws ::= [ \\t\\n]{0,4}",
        ]
    )


GRAMMAR = gbnf_for(TOOLS)


def post(url, body):
    request = urllib.request.Request(url, json.dumps(body).encode(), {"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=900) as response:
        return json.load(response)


COMMON = {"temperature": 0, "seed": 1, "cache_prompt": False}
NO_THINKING = {"chat_template_kwargs": {"enable_thinking": False}}


def strip_thinking(text):
    return re.sub(r"<think>.*?(</think>|$)", "", text, flags=re.S).strip()


def parse_bruce_call(text):
    """Returns (name, arguments) or None, or raises ValueError for a malformed call."""
    text = strip_thinking(text)
    match = re.search(r"<tool_call>(.*?)(</tool_call>|$)", text, re.S)
    if not match:
        return None
    call = json.loads(match.group(1).strip())
    return call["name"], call.get("arguments", {})


def run_native(server, prompt):
    body = {
        "messages": [{"role": "system", "content": PERSONA}, {"role": "user", "content": prompt}],
        "tools": [{"type": "function", "function": t} for t in TOOLS],
        "max_tokens": MAX_TOKENS,
        **COMMON,
        **NO_THINKING,
    }
    result = post(f"{server}/v1/chat/completions", body)
    message = result["choices"][0]["message"]
    calls = message.get("tool_calls") or []
    parsed = None
    if calls:
        function = calls[0]["function"]
        arguments = function.get("arguments") or "{}"
        parsed = (function["name"], json.loads(arguments) if isinstance(arguments, str) else arguments)
    return parsed, message.get("content") or "", result.get("timings", {})


def run_bruce(server, prompt, system, grammar):
    messages = [{"role": "system", "content": system}, {"role": "user", "content": prompt}]
    templated = post(f"{server}/apply-template", {"messages": messages, **NO_THINKING})["prompt"]
    body = {"prompt": templated, "n_predict": MAX_TOKENS, **COMMON}
    if grammar:
        body.update({"grammar": GRAMMAR, "grammar_lazy": True, "grammar_triggers": [{"type": 1, "value": "<tool_call>"}],
                     # Where "<tool_call>" is one special token, llama-server requires it preserved.
                     "preserved_tokens": ["<tool_call>", "</tool_call>"]})
    result = post(f"{server}/completion", body)
    text = result["content"]
    return parse_bruce_call(text), text, result.get("timings", {})


OPS = {ast.Add: operator.add, ast.Sub: operator.sub, ast.Mult: operator.mul, ast.Div: operator.truediv,
       ast.Pow: operator.pow, ast.USub: operator.neg, ast.UAdd: operator.pos}


def evaluate(expression):
    expression = expression.replace(",", "").replace("^", "**").replace("×", "*").replace("÷", "/")
    expression = re.sub(r"(\d+(?:\.\d+)?)\s*%\s*(of\s*)?", r"(\1/100)*", expression).rstrip("*")
    expression = re.sub(r"\bof\b", "*", expression)

    def walk(node):
        if isinstance(node, ast.Expression):
            return walk(node.body)
        if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
            return node.value
        if isinstance(node, ast.BinOp) and type(node.op) in OPS:
            return OPS[type(node.op)](walk(node.left), walk(node.right))
        if isinstance(node, ast.UnaryOp) and type(node.op) in OPS:
            return OPS[type(node.op)](walk(node.operand))
        raise ValueError(expression)

    return walk(ast.parse(expression, mode="eval"))


def score(case, parsed):
    expected = case["tool"]
    if parsed is None:
        return "ok" if expected is None else "missed"
    name, arguments = parsed
    if expected is None:
        return "unwanted"
    if name != expected:
        return "wrong_tool"
    if expected == "calculate":
        try:
            value = evaluate(str(arguments.get("expression", "")))
        except (ValueError, SyntaxError, ZeroDivisionError, TypeError):
            return "bad_args"
        return "ok" if abs(value - case["value"]) <= 1e-6 * max(1, abs(case["value"])) else "bad_args"
    return "ok"


def main():
    server, label, out = sys.argv[1:4]
    variants = sys.argv[4:] or ["native", "prompt", "grammar", "index"]
    with open(out, "a") as sink:
        for variant in variants:
            for case in CASES:
                started = time.monotonic()
                try:
                    if variant == "native":
                        parsed, text, timings = run_native(server, case["prompt"])
                    else:
                        system = INDEX_SYSTEM if variant == "index" else BRUCE_SYSTEM
                        parsed, text, timings = run_bruce(server, case["prompt"], system, variant != "prompt")
                    outcome = score(case, parsed)
                except (json.JSONDecodeError, KeyError, TypeError, AttributeError) as error:
                    parsed, text, timings, outcome = None, repr(error), {}, "malformed"
                record = {
                    "model": label, "variant": variant, "prompt": case["prompt"], "expected": case["tool"],
                    "outcome": outcome, "call": parsed, "text": text[:400], "wall_s": round(time.monotonic() - started, 2),
                    "prompt_n": timings.get("prompt_n"), "prompt_ms": timings.get("prompt_ms"),
                    "predicted_n": timings.get("predicted_n"), "predicted_ms": timings.get("predicted_ms"),
                }
                sink.write(json.dumps(record) + "\n")
                sink.flush()
                print(f"{label} {variant:7} {outcome:10} {case['prompt']}", flush=True)


if __name__ == "__main__":
    main()
