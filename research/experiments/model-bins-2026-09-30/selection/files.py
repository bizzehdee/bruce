import json, re
B=1e9
P=json.load(open('picked.json'))
phones={'xz-premium-4gb':(5.8,1.3e9),'xperia-1-ii-8gb':(24.0,3.9e9),'pixel-11-12gb':(24.0,6.0e9)}
def whole(p):
    n=p.split('/')[-1].lower()
    return not ('mmproj' in n or re.search(r'-\d{5}-of-\d{5}',n) or 'imatrix' in n)
def unq(p): return bool(re.search(r'(^|[-_.])(f32|f16|bf16|fp16|fp32)([-_.]|$)',p.split('/')[-1].lower().removesuffix('.gguf')))
for m in P:
    c=[(p,s,h) for p,s,h in m['files'] if whole(p)]
    m['picks']={}
    for ph,(k,usable) in phones.items():
        def key(x):
            p,s,h=x; tps=k/(s/B)
            fit=0 if s<=usable*0.8 else (1 if s<=usable else 2)
            speed=0 if tps>=15 else (1 if tps>=5 else 2)
            return (fit==2,fit,speed,unq(p),-s)
        best=sorted(c,key=key)[0]
        m['picks'][ph]=dict(path=best[0],size=best[1],sha256=best[2])
    print(f"{m['base'][:40]:<40}", ' | '.join(f"{v['path'].split('/')[-1][-22:]} {v['size']/1e9:.2f}" for v in m['picks'].values()))
json.dump(P,open('picked.json','w'),indent=1)
