"""After reading the start of a page, does MiniCPM5 answer or ask for more? (2026-10-02)

usage: read-more-test.py <replay jsonl> <seeds>; llama-server with MiniCPM5 2B on 127.0.0.1:8092.
"""
import json, re, sys, urllib.request, collections
rs = [json.loads(l) for l in open(sys.argv[1])]
search, fetch = [r for r in rs if r['type'] == 'tool'][:2]
phone = open('research/experiments/date-in-prompt-2026-10-02/phone-prompt.txt').read()
system = phone.split('<|im_start|>system\n', 1)[1].split('\n\n# Tools', 1)[0]
tools = [json.loads(l) for l in phone.split('<tools>\n', 1)[1].split('\n</tools>', 1)[0].split('\n')]
for t in tools:
    if t['function']['name'] == 'fetch_page':
        t['function']['description'] = "Read a web page and return its text: the start of the page, or more of it with full set to true. Only for http or https addresses."
        t['function']['parameters']['properties']['full']['description'] = "True to read more of the page, when its start did not answer the question"
order = {n: i for i, n in enumerate(["get_datetime", "calculate", "get_network_status", "search_wikipedia", "fetch_page"])}
tools.sort(key=lambda t: order[t['function']['name']])
NOTE = re.compile(r'\\n\[[^\]]*?goes on for (\d+) more characters[^\]]*\]')
def variant(note, drop_full=False):
    result = NOTE.sub(lambda m: note.replace('N', m.group(1)), fetch['result'])
    offered = json.loads(json.dumps(tools))
    if drop_full:
        f = next(t for t in offered if t['function']['name'] == 'fetch_page')['function']
        f['parameters']['properties'].pop('full'); f['description'] = "Read a web page and return the start of its text. Only for http or https addresses."
    return result, offered
V = {
  "current note": variant(r'\n[The page goes on for N more characters. Answer from this if you can; only if it does not answer the question, call fetch_page again with full set to true to read more.]'),
  "count only": variant(r'\n[The page goes on for N more characters.]'),
  "no note": variant(''),
  "answer-first note": variant(r'\n[This is enough to answer most questions: answer now. The page goes on for N more characters, which fetch_page with full set to true reads, but only if this does not answer.]'),
  "no full parameter": variant(r'\n[The page goes on for N more characters.]', drop_full=True),
}
q = "what happened today in history"
def call(i, name, args): return {"id": f"call_{i}", "type": "function", "function": {"name": name, "arguments": args}}
for name, (result, offered) in V.items():
    msgs = [{"role": "system", "content": system}, {"role": "user", "content": q},
            {"role": "assistant", "content": "", "tool_calls": [call(1, "search_wikipedia", json.dumps({"query": "October 2 history"}))]},
            {"role": "tool", "tool_call_id": "call_1", "content": search['result']},
            {"role": "assistant", "content": "", "tool_calls": [call(2, "fetch_page", json.dumps({"url": "https://en.wikipedia.org/wiki/October_2"}))]},
            {"role": "tool", "tool_call_id": "call_2", "content": result}]
    c = collections.Counter(); ex = None
    for seed in range(int(sys.argv[2])):
        body = {"messages": msgs, "tools": offered, "temperature": 0.5, "top_k": 0, "top_p": 1.0, "min_p": 0.0, "seed": seed, "max_tokens": 150, "chat_template_kwargs": {"enable_thinking": False}}
        m = json.load(urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:8092/v1/chat/completions", json.dumps(body).encode(), {"Content-Type": "application/json"})))['choices'][0]['message']
        calls = m.get('tool_calls') or []
        k = ("full" if '"full": true' in calls[0]['function']['arguments'].replace('":true', '": true') else calls[0]['function']['name']) if calls else "answer"
        c[k] += 1
        if k == "answer" and ex is None: ex = (m.get('content') or '')[:160].replace('\n', ' ')
    print(f"{name:20s} {dict(c)} | {ex}", flush=True)
