import json, re, collections
src=open('/mnt/data/dev/bruce/app/src/main/java/com/bizzeh/bruce/models/LlamaArchitectures.kt').read()
allset=set(re.findall(r'"([^"]+)"', src.split('val all')[1].split(')')[0]))
notchat=set(re.findall(r'"([^"]+)"', src.split('notChatModels = setOf(')[1].split(')')[0]))|{'qwen3tts','pockettts','llama-embed','t5','wavtokenizer-dec'}
data=json.load(open('search-raw.json'))
B=10**9
rows=[]; why=collections.Counter()
for m in data:
    g=m.get('gguf') or {}
    arch=g.get('architecture'); total=g.get('total'); tmpl=g.get('chat_template')
    cd=m.get('cardData') or {}
    base=cd.get('base_model')
    if isinstance(base,list): base=base[0] if len(base)==1 else None
    files=[s['rfilename'] for s in m.get('siblings',[]) if s['rfilename'].endswith('.gguf')]
    reason=None
    if m.get('gated'): reason='gated'
    elif not arch or arch not in allset: reason='unsupported arch'
    elif arch in notchat: reason='not chat'
    elif not total: reason='no params'
    elif not tmpl: reason='no chat template'
    why[reason]+=1
    rows.append(dict(id=m['id'],downloads=m.get('downloads',0),arch=arch,params=total,base=base or m['id'],license=cd.get('license'),pipeline=m.get('pipeline_tag'),reason=reason,nfiles=len(files)))
print(why)
json.dump(rows,open('rows.json','w'),indent=0)
