import json, gzip, os, time, datetime
RAW='/home/user/oflayn/build/raw'
OUT='/home/user/oflayn/rebuild/app/src/main/assets/offline'
os.makedirs(OUT, exist_ok=True)
idx=json.load(open(f'{RAW}/lines_index.json'))
stops={}          # id -> [name, lat, lon]
lines=[]
for e in idx:
    h=e['hatNo']; p=f'{RAW}/routestat/{h}.json'
    if not os.path.exists(p): continue
    try: o=json.load(open(p))
    except Exception: continue
    dirs={}
    for r in (o.get('result') or []):
        try: sid=int(r['stopId'])
        except Exception: continue
        nm=(r.get('stopName') or '').strip()
        try: lat=round(float(r.get('latitude') or 0),6)
        except Exception: lat=0.0
        try: lon=round(float(r.get('longitude') or 0),6)
        except Exception: lon=0.0
        try: seq=int(r.get('sequence') or 0)
        except Exception: seq=0
        if sid not in stops: stops[sid]=[nm,lat,lon]
        dirs.setdefault((r.get('direction') or 'G'),[]).append((seq,sid))
    if not dirs: continue
    lines.append({'id':h,'code':e['code'],'title':(e.get('title') or e['code']).strip(),
                  'dirs':[{'d':k,'ids':[s for _,s in sorted(v)]} for k,v in sorted(dirs.items())]})
lines.sort(key=lambda x: x['code'])
net={'v':1,'built':datetime.datetime.now(datetime.UTC).strftime('%Y-%m-%dT%H:%M:%SZ'),
     'source':'bursakartapi.abys-web.com — official BursaKart "Otobüsüm Nerede" service',
     'stops':[[k]+v for k,v in sorted(stops.items())],'lines':lines}
raw=json.dumps(net,ensure_ascii=False,separators=(',',':')).encode('utf-8')
with gzip.open(f'{OUT}/network.json.gz','wb',compresslevel=9) as f: f.write(raw)
print('lines',len(lines),'stops',len(stops),'raw MB',round(len(raw)/1048576,2),'gz MB',round(os.path.getsize(f'{OUT}/network.json.gz')/1048576,2))
snap=json.load(gzip.open(f'{OUT}/day_snapshot.json.gz'))
print('snapshot trips',len(snap['trips']))
