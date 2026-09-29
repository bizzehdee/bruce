#!/usr/bin/env python3
"""Indented against compact tool JSON in Llama 3.2's template (2026-09-29).

Usage: run.py <server-url> <label> <out.jsonl> <prompt-dump.txt> [repeats]
The system prompt and tools are those of an empty Bruce chat, taken from a PromptDumpDeviceTest dump.
Each case is asked [repeats] times at Bruce's default temperature; the reply's first tool call (if
any) is recorded, parsed by llama-server with the model's own tool format. Output that starts a
call llama-server cannot parse is recorded as "<unparsed>".
"""
import json
import re
import sys
import urllib.error
import urllib.request

url, label, out, dump = sys.argv[1:5]
repeats = int(sys.argv[5]) if len(sys.argv) > 5 else 5
TEMPERATURE = 0.8

text = open(dump).read()
system = text.split("<|start_header_id|>system<|end_header_id|>\n\n", 1)[1].split("<|eot_id|>", 1)[0]
# Llama's template writes its own header; keep only Bruce's part.
system = system.split("\n\n", 1)[1] if system.startswith("Environment:") else system
system = re.sub(r"^Cutting Knowledge Date:.*\nToday Date:.*\n\n", "", system)
section = text.split("Do not use variables.\n\n", 1)[1].split("<|eot_id|>", 1)[0]
tools = [json.loads(block) for block in section.strip().split("\n\n")]

CASES = [
    ("What time is it?", "get_datetime"),
    ("What day is it today?", "get_datetime"),
    ("How much battery have I got left?", "get_battery_status"),
    ("What is 17.5 times 23?", "calculate"),
    ("What phone is this?", "get_device_info"),
    ("How much free storage do I have?", "get_storage_status"),
    ("Am I on wifi?", "get_network_status"),
    ("What is my screen timeout set to?", "get_setting"),
    ("Set my screen timeout to 60 seconds", "set_setting"),
    ("Open my Bluetooth settings", "open_settings_page"),
    ("What files are in Documents?", "list_files"),
    ("Tell me a joke about dogs.", None),
]


def ask(question):
    body = {"messages": [{"role": "system", "content": system}, {"role": "user", "content": question}],
            "tools": tools, "temperature": TEMPERATURE, "max_tokens": 120}
    request = urllib.request.Request(url + "/v1/chat/completions", json.dumps(body).encode(), {"Content-Type": "application/json"})
    try:
        reply = json.load(urllib.request.urlopen(request, timeout=300))["choices"][0]["message"]
    except urllib.error.HTTPError as error:
        # llama-server answers 500 when the output starts a tool call it cannot parse.
        return "<unparsed>", error.read().decode()[:200]
    calls = reply.get("tool_calls") or []
    return (calls[0]["function"]["name"] if calls else None), (reply.get("content") or "")[:200]


with open(out, "a") as sink:
    for question, expected in CASES:
        for attempt in range(repeats):
            called, content = ask(question)
            sink.write(json.dumps({"label": label, "question": question, "expected": expected, "called": called, "ok": called == expected, "content": content}) + "\n")
            sink.flush()
print("tools", len(tools))
