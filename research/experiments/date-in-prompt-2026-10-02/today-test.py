#!/usr/bin/env python3
"""Do prompt changes make a model check the date for 'today' questions, and stop the pun line leaking? (2026-10-02)"""
import json, sys, urllib.request
d = json.load(open('/mnt/data/dev/bruce/research/experiments/model-bins-2026-09-30/bruce-prompt.json'))['on']
tools = [t if 'type' in t else {'type': 'function', 'function': t} for t in d['tools']] + [
    {"type": "function", "function": {"name": "web_search", "description": "Search the web and return text from the best matching pages, with their addresses.", "parameters": {"type": "object", "properties": {"query": {"type": "string", "description": "What to search for"}}, "required": ["query"]}}},
    {"type": "function", "function": {"name": "search_wikipedia", "description": "Search Wikipedia, and only Wikipedia, for encyclopedia articles; returns titles, addresses and matching excerpts. Search for an article's title or a few key words, such as \"October 2\" or \"Julius Caesar\", not a question. Then read the best article with fetch_page to answer.", "parameters": {"type": "object", "properties": {"query": {"type": "string", "description": "An article title or a few key words, not a sentence"}}, "required": ["query"]}}},
    {"type": "function", "function": {"name": "fetch_page", "description": "Read a web page and return its text: the start of the page, or the whole page with full set to true. Only for http or https addresses.", "parameters": {"type": "object", "properties": {"url": {"type": "string"}, "full": {"type": "boolean"}}, "required": ["url"]}}},
]
OLD_PUN = "An occasional light dog pun is welcome, never at the expense of a clear answer."
OLD_TOOL = "Use a tool for anything a tool can look up or calculate, such as the time, date, arithmetic or the phone's status; do not guess those."
PHONE = [t for t in tools if t['function']['name'] != 'web_search']
DATE = " Today is Friday 2 October 2026."
VARIANTS = {
    "current": d['system'],
    "_new": d['system'].replace(OLD_PUN, "Now and then you may make a light dog pun of your own, never at the expense of a clear answer.")
        .replace(OLD_TOOL, OLD_TOOL + " Words like today, now, tomorrow or this week need the current date: get it with a tool first."),
    "date": d['system'].replace(OLD_TOOL, OLD_TOOL + DATE),
}
PROMPTS = ["what happened today in history", "what happened on this day in history", "how many days until christmas", "what day of the week is it tomorrow", "anything interesting happen this week in past years?", "tell me a joke"]
for name, system in [(k, v) for k, v in VARIANTS.items() if not k.startswith("_")]:
  for toolset, offered in (("web", tools), ("phone", PHONE)):
    for p in PROMPTS:
        body = {"messages": [{"role": "system", "content": system}, {"role": "user", "content": p}], "tools": offered, "temperature": 0, "max_tokens": 200, "chat_template_kwargs": {"enable_thinking": False}}
        r = json.load(urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:8092/v1/chat/completions", json.dumps(body).encode(), {"Content-Type": "application/json"})))
        m = r['choices'][0]['message']
        calls = [c['function']['name'] for c in m.get('tool_calls') or []]
        text = (m.get('content') or '').replace('\n', ' ')
        args = [c['function']['arguments'] for c in m.get('tool_calls') or []]
        print(f"{name:8s} {toolset:5s} | {p[:40]:40s} | {args} | calls={calls} | {text[:140]}")
