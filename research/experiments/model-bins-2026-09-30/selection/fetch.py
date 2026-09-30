import json, urllib.request, urllib.parse, re, sys
base='https://huggingface.co/api/models'
exp=''.join('&expand[]='+e for e in ['downloads','gated','cardData','gguf','siblings','pipeline_tag','tags','likes'])
url=f'{base}?filter=gguf&num_parameters=min:0,max:3B&sort=downloads&direction=-1&limit=1000{exp}'
out=[]; pages=0
while url and pages<3:
    r=urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'bruce-research'}),timeout=60)
    out+=json.load(r); pages+=1
    m=re.search(r'<([^>]+)>;\s*rel="next"', r.headers.get('Link') or '')
    url=m.group(1) if m else None
json.dump(out,open('search-raw.json','w'))
print(len(out), pages, url is not None)
