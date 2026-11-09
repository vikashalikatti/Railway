"""
Builds src/main/resources/data/indian-railways.json.gz (the admin's bundled train list) from
DataMeet's open Indian Railways data (CC0): https://github.com/datameet/railways

    git clone --depth 1 https://github.com/datameet/railways.git datameet
    python3 tools/build-indian-railways.py datameet src/main/resources/data/indian-railways.json.gz

Keeps trains with reserved classes, their real halts (not the stations they pass), station
positions, and km per stop (straight line x 1.25, scaled to each train's published distance).
"""
import json, gzip, math, re, collections, sys
src, out = sys.argv[1], sys.argv[2]
T=json.load(open(src+'/trains.json'))['features']; S=json.load(open(src+'/stations.json'))['features']; SC=json.load(open(src+'/schedules.json'))
CODE=re.compile(r'^[A-Z]{1,6}$')
def title(n):
    n=re.sub(r'\s+',' ',(n or '').strip())
    w=[]
    for x in n.split(' '):
        if x in ('JN','JN.'): w.append('Jn')
        elif re.fullmatch(r'[A-Z]{1,3}\.?', x) and x not in ('A','ON','OF','THE','AND','BY','NEW','OLD','RD','ROAD','HALT','TOWN','CITY','PORT','FORT','GATE','NAGAR','PUR','GANJ','BAD'): w.append(x.rstrip('.') if len(x)<=3 else x.title())
        else: w.append('-'.join(p.capitalize() for p in x.lower().split('-')))
    return ' '.join(w)
def city(name):
    c=re.sub(r'\s+(Jn|Junction|Cantt|Cant|Terminus|Term|Halt|Rs|Ph)$','',name)
    return c[:60] or name[:60]
stations={}
for f in S:
    p=f['properties']; code=(p.get('code') or '').strip().upper()
    if not CODE.match(code): continue
    g=f.get('geometry') or {}; c=g.get('coordinates') if g else None
    nm=title(p.get('name'))[:80]
    stations[code]=[code,nm,city(nm),(p.get('state') or '')[:40] or None, round(c[1],5) if c else None, round(c[0],5) if c else None]
by=collections.defaultdict(list)
for s in SC: by[s['train_number']].append(s)
TYPES={'Exp':'Express','SF':'Superfast','Raj':'Rajdhani','Drnt':'Duronto','Shtb':'Shatabdi','JShtb':'Jan Shatabdi','GR':'Garib Rath','SKr':'Sampark Kranti','Mail':'Mail','Pass':'Passenger','MEMU':'MEMU','DEMU':'DEMU','Toy':'Toy train','Del':'Suburban','Klkt':'Suburban','Hyd':'Suburban'}
CL=[('first_ac','AC1_TIER'),('second_ac','AC2_TIER'),('third_ac','AC3_TIER'),('chair_car','AC_CHAIR_CAR'),('sleeper','SLEEPER_CLASS')]
def hav(a,b):
    R=6371; la1,lo1,la2,lo2=map(math.radians,[a[0],a[1],b[0],b[1]])
    h=math.sin((la2-la1)/2)**2+math.cos(la1)*math.cos(la2)*math.sin((lo2-lo1)/2)**2
    return 2*R*math.asin(math.sqrt(h))
def hm(t):
    return None if not t or t=='None' else t[:5]
trains=[]; skipped=collections.Counter()
for f in T:
    p=f['properties']
    classes=[c for k,c in CL if p.get(k)]
    if not classes: skipped['no reserved class']+=1; continue
    num=p['number']
    if not str(num).isdigit(): skipped['bad number']+=1; continue
    num=int(num)
    sc=sorted([s for s in by.get(p['number'],[]) if s['day'] is not None], key=lambda s:(s['day'], s['id']))
    stops=[]; seen=set()
    for s in sc:
        code=(s['station_code'] or '').strip().upper()
        if not CODE.match(code) or code in seen: continue
        seen.add(code)
        if code not in stations:
            nm=title(s['station_name'])[:80]; stations[code]=[code,nm,city(nm),None,None,None]
        arr,dep=hm(s['arrival']),hm(s['departure'])
        stops.append([code,arr,dep])
    if len(stops)<2: skipped['fewer than 2 stops']+=1; continue
    # keep the first and last stop and the real halts (the data also lists stations the train passes)
    first,last=stops[0],stops[-1]
    first[1]=first[1] or first[2]; last[2]=last[2] or last[1]
    if first[2] is None or last[1] is None: skipped['missing times']+=1; continue
    stops=[first]+[x for x in stops[1:-1] if x[1] and x[2] and x[1]!=x[2]]+[last]
    # km: straight line x1.25, scaled to the published total, gaps interpolated, always increasing
    pos=[(stations[x[0]][4],stations[x[0]][5]) if stations[x[0]][4] is not None else None for x in stops]
    raw=[0.0]; last=pos[0]; lastIdx=0; known=[0] if pos[0] else []
    cum=[None]*len(stops); cum[0]=0.0
    acc=0.0; prev=pos[0]
    for i in range(1,len(stops)):
        if pos[i] and prev:
            acc+=hav(prev,pos[i])*1.25; cum[i]=acc; prev=pos[i]
        elif pos[i] and not prev:
            cum[i]=None; prev=pos[i]
        else: cum[i]=None
    # interpolate missing by index
    idx=[i for i,c in enumerate(cum) if c is not None]
    if len(idx)<2: cum=[float(i) for i in range(len(stops))]
    else:
        for i in range(len(cum)):
            if cum[i] is None:
                lo=max([j for j in idx if j<i],default=None); hi=min([j for j in idx if j>i],default=None)
                if lo is None: cum[i]=cum[hi]*i/max(hi,1)
                elif hi is None: cum[i]=cum[lo]+(i-lo)*5
                else: cum[i]=cum[lo]+(cum[hi]-cum[lo])*(i-lo)/(hi-lo)
    total=p.get('distance') or 0
    if total and cum[-1]>0: cum=[c*total/cum[-1] for c in cum]
    km=[0]; 
    for c in cum[1:]: km.append(max(int(round(c)), km[-1]+1))
    trains.append([num,title(p['name'])[:80] if p['name'].isupper() else p['name'][:80],TYPES.get(p.get('type'),p.get('type') or 'Express'),classes,[[x[0],x[1],x[2],k] for x,k in zip(stops,km)]])
used={x[0] for t in trains for x in t[4]}
st=[v for k,v in sorted(stations.items()) if k in used]
data={"source":"DataMeet Indian Railways data, CC0 (github.com/datameet/railways), collected 2016","stations":st,"trains":sorted(trains)}
with gzip.open(out,'wt',encoding='utf-8') as fh: json.dump(data,fh,separators=(',',':'))
print(len(trains),'trains',len(st),'stations',skipped, 'avg stops',sum(len(t[4]) for t in trains)/len(trains))
print(trains[0][:4], trains[0][4][:3], trains[0][4][-1])
print(st[:3])
