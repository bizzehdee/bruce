import json, collections, re
rows=[r for r in json.load(open('rows.json')) if r['reason'] is None]
B=10**9
NONCHAT_PIPE={'sentence-similarity','text-ranking','automatic-speech-recognition','text-to-speech','feature-extraction','text-to-audio','audio-to-audio'}
NONCHAT_NAME=re.compile(r'ocr|asr|tts|embed|rerank|audio|voice|-base\b|-mt\d?-|layers',re.I)
def key(b): return re.sub(r'-gguf$','',b.lower().strip())
def named_size(b):
    m=[float(x) for x in re.findall(r'[-_](?:e)?(\d+(?:\.\d+)?)b\b', b.lower())]
    return max(m)*B if m else None
g=collections.defaultdict(list); dropped=collections.Counter()
for r in rows:
    if r['pipeline'] in NONCHAT_PIPE or NONCHAT_NAME.search(r['base']) or NONCHAT_NAME.search(r['id']): dropped['not a chat model']+=1; continue
    ns=named_size(r['base'])
    if ns and ns>3.5*B: dropped['params from a partial file']+=1; continue
    g[key(r['base'])].append(r)
bases=[]
for k,v in g.items():
    v.sort(key=lambda r:-r['downloads'])
    publishers={r['id'].split('/')[0].lower() for r in v}
    if len(publishers)<2: dropped['one publisher']+=1; continue
    bases.append(dict(base=v[0]['base'],total=sum(r['downloads'] for r in v),publishers=len(publishers),repos=[r['id'] for r in v],params=v[0]['params'],arch=v[0]['arch'],lic=v[0]['license'],pipe=v[0]['pipeline']))
bases.sort(key=lambda b:-b['total'])
print(dropped)
json.dump(bases,open('bases.json','w'),indent=1)
for name,lo,hi in [('<1B',0,B),('1-3B',B,3*B)]:
    print('==',name)
    for i,b in enumerate([b for b in bases if lo<=b['params']<hi][:18]):
        print(f"{i+1:>2} {b['total']:>8} {b['publishers']:>2} {b['params']/1e9:5.2f} {b['arch']:<9} {str(b['lic'])[:10]:<10} {str(b['pipe'])[:10]:<10} {b['base'][:45]:<45} {b['repos'][:3]}")
