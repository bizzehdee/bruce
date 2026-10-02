import json, urllib.request, collections, itertools
P = 'research/experiments/date-in-prompt-2026-10-02/'
phone = open(P + 'phone-prompt.txt').read(); host = open(P + 'host-prompt.txt').read()
L = phone.split('\n')
fetch = next(l for l in L if '"fetch_page"' in l); wiki = next(l for l in L if '"search_wikipedia"' in l); net = next(l for l in L if '"get_network_status"' in l)
extra = '\n'.join(l for l in host.split('\n') if any(n in l for n in ('get_battery_status', 'get_device_info', 'get_storage_status')))
base = phone.replace(net + '\n', '').replace(fetch + '\n' + wiki, '{STATUS}{WEB}')
status = {"none": "", "network": net + "\n", "battery/device/storage": extra + "\n", "all four": net + "\n" + extra + "\n"}
order = {"read first": fetch + "\n" + wiki, "search first": wiki + "\n" + fetch}
Q = ["what happened today in history", "what happened on this day in history"]
for (sn, s), (on, o), q in itertools.product(status.items(), order.items(), Q):
    prompt = base.replace('{STATUS}', s).replace('{WEB}', o).replace("what happened today in history", q)
    c = collections.Counter()
    for i in range(30):
        body = {"prompt": prompt, "temperature": 0.5, "top_k": 0, "top_p": 1.0, "min_p": 0.0, "seed": i, "n_predict": 40, "cache_prompt": True}
        t = json.load(urllib.request.urlopen(urllib.request.Request("http://127.0.0.1:8092/completion", json.dumps(body).encode(), {"Content-Type": "application/json"})))['content']
        c[t.split('name="')[1].split('"')[0] if t.startswith(' name="') else 'none'] += 1
    print(f"{sn:24s} {on:13s} {q[14:26]:12s} search={c['search_wikipedia']:2d} date={c['get_datetime']:2d} none={c['none']:2d}", flush=True)
