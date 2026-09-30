import json, re, urllib.request, os
B=10**9
bases=json.load(open('bases.json'))
rows={r['id']:r for r in json.load(open('rows.json'))}
def top(lo,hi,n): return [b for b in bases if lo<=b['params']<hi][:n]
sel=[('under-1B',b) for b in top(0,B,13)]+[('1B-to-3B',b) for b in top(B,3*B,13)]
os.makedirs('trees',exist_ok=True)
out=[]
for bucket,b in sel:
    name=b['base'].split('/')[-1].lower().removesuffix('-gguf')
    own=[r for r in b['repos'] if name in r.lower().replace('_','-').replace('qwen-qwen','qwen') or name in r.lower()]
    rep=(own or b['repos'])[0]
    f='trees/'+rep.replace('/','__')+'.json'
    if not os.path.exists(f):
        info=json.load(urllib.request.urlopen(f'https://huggingface.co/api/models/{rep}',timeout=60))
        sha=info['sha']
        tree=json.load(urllib.request.urlopen(f'https://huggingface.co/api/models/{rep}/tree/{sha}?recursive=true',timeout=60))
        json.dump(dict(sha=sha,tree=tree),open(f,'w'))
    t=json.load(open(f))
    ggufs=[(x['path'],x['size'],(x.get('lfs') or {}).get('oid')) for x in t['tree'] if x['path'].endswith('.gguf')]
    out.append(dict(bucket=bucket,base=b['base'],downloads_all_copies=b['total'],publishers=b['publishers'],repository=rep,revision=t['sha'],params=b['params'],arch=b['arch'],licence=b['lic'],pipeline=b['pipe'],files=ggufs))
json.dump(out,open('picked.json','w'),indent=1)
for o in out: print(o['bucket'],o['base'],'->',o['repository'],len(o['files']),'files')
