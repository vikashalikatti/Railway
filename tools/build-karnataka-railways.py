"""
Builds src/main/resources/data/karnataka-railways.json.gz, the admin's bundled train list for
Karnataka, from DataMeet's open Indian Railways data (CC0) plus the Vande Bharat trains that serve
Karnataka (added by hand from public timetables, because DataMeet's data is from 2016).

    git clone --depth 1 https://github.com/datameet/railways.git datameet
    curl -LO https://raw.githubusercontent.com/geohacker/india/master/state/india_state.geojson
    python3 tools/build-karnataka-railways.py datameet src/main/resources/data/karnataka-railways.json.gz india_state.geojson

What it keeps:
- Stations: every station inside Karnataka's state boundary (the third argument, a GeoJSON of
  Indian states; only used here, not bundled). Without it, the data's own state field is used and
  stations without one are placed by their 5 nearest neighbours (less accurate near borders).
- Trains: every train with at least 2 halts in Karnataka, cut to the part of its route inside
  Karnataka (the longest run of Karnataka halts). Km and day numbers start again at that part's
  first halt. Trains without reserved classes (Passenger, MEMU, DEMU) get 4 second-sitting (2S)
  coaches.
- Vande Bharat trains keep their whole route, so the few stations outside Karnataka at their
  ends (Chennai, Madgaon, Pune...) are included too.

Bundle format: {"source", "stations": [[code, name, city, state, lat, lon]],
"trains": [[number, name, type, classes, stops, days?, coaches?]]}, stop = [code, arr, dep, km, day]
with km counted from the first stop.
"""
import json, gzip, math, re, collections, sys

src, out = sys.argv[1], sys.argv[2]
boundary = sys.argv[3] if len(sys.argv) > 3 else None
T = json.load(open(src + '/trains.json'))['features']
S = json.load(open(src + '/stations.json'))['features']
SC = json.load(open(src + '/schedules.json'))
CODE = re.compile(r'^[A-Z]{1,6}$')
STATE = 'Karnataka'
ALL = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday']


def title(n):
    n = re.sub(r'\s+', ' ', (n or '').strip())
    w = []
    for x in n.split(' '):
        if x in ('JN', 'JN.'):
            w.append('Jn')
        elif re.fullmatch(r'[A-Z]{1,3}\.?', x) and x not in ('A', 'ON', 'OF', 'THE', 'AND', 'BY', 'NEW', 'OLD', 'RD', 'ROAD', 'HALT', 'TOWN', 'CITY', 'PORT', 'FORT', 'GATE', 'NAGAR', 'PUR', 'GANJ', 'BAD'):
            w.append(x.rstrip('.') if len(x) <= 3 else x.title())
        else:
            w.append('-'.join(p.capitalize() for p in x.lower().split('-')))
    return ' '.join(w)


def city(name):
    c = re.sub(r'\s+(Jn|Junction|Cantt|Cant|Terminus|Term|Halt|Rs|Ph)$', '', name)
    return c[:60] or name[:60]


def hav(a, b):
    R = 6371
    la1, lo1, la2, lo2 = map(math.radians, [a[0], a[1], b[0], b[1]])
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


def hm(t):
    return None if not t or t == 'None' else t[:5]


# Today's names for the big stations (the 2016 data has the old spellings), and code changes
RENAME = {
    'SBC': ('SBC', 'KSR Bengaluru City Jn', 'Bengaluru'),
    'YPR': ('YPR', 'Yesvantpur Jn', 'Bengaluru'),
    'BNC': ('BNC', 'Bengaluru Cantonment', 'Bengaluru'),
    'KJM': ('KJM', 'Krishnarajapuram', 'Bengaluru'),
    'YNK': ('YNK', 'Yelahanka Jn', 'Bengaluru'),
    'BAND': ('BAND', 'Banaswadi', 'Bengaluru'),
    'MYS': ('MYS', 'Mysuru Jn', 'Mysuru'),
    'UBL': ('UBL', 'Hubballi Jn', 'Hubballi'),
    'DWR': ('DWR', 'Dharwad', 'Dharwad'),
    'BGM': ('BGM', 'Belagavi', 'Belagavi'),
    'TK': ('TK', 'Tumakuru', 'Tumakuru'),
    'MAQ': ('MAQ', 'Mangaluru Central', 'Mangaluru'),
    'MAJN': ('MAJN', 'Mangaluru Jn', 'Mangaluru'),
    'BAY': ('BAY', 'Ballari Jn', 'Ballari'),
    'BJP': ('BJP', 'Vijayapura', 'Vijayapura'),
    'SMET': ('SMET', 'Shivamogga Town', 'Shivamogga'),
    'GR': ('KLBG', 'Kalaburagi', 'Kalaburagi'),
    'SSPN': ('SSPN', 'Sri Sathya Sai Prasanthi Nilayam', 'Puttaparthi'),
    'MAS': ('MAS', 'MGR Chennai Central', 'Chennai'),
}
NEW_STATIONS = [
    ['SMVB', 'SMVT Bengaluru', 'Bengaluru', STATE, 12.9995, 77.6631],
]

# ---------------------------------------------------------------- stations
labelled, raw = [], {}
for f in S:
    p = f['properties']
    code = (p.get('code') or '').strip().upper()
    if not CODE.match(code):
        continue
    g = f.get('geometry') or {}
    c = g.get('coordinates') if g else None
    raw[code] = (p, c)
    if c and p.get('state'):
        labelled.append((c[1], c[0], p['state']))


def nearest_state(lat, lon, k=5):
    near = sorted(labelled, key=lambda x: (x[0] - lat) ** 2 + ((x[1] - lon) * math.cos(math.radians(lat))) ** 2)[:k]
    return collections.Counter(x[2] for x in near).most_common(1)[0][0]


rings = []  # Karnataka's outer and inner rings, as (lon, lat) lists
if boundary:
    for f in json.load(open(boundary))['features']:
        if f['properties'].get('NAME_1') == STATE:
            g = f['geometry']
            polys = g['coordinates'] if g['type'] == 'MultiPolygon' else [g['coordinates']]
            rings = [ring for poly in polys for ring in poly]
    assert rings, 'no Karnataka in ' + boundary


def inside(lon, lat):
    """even-odd rule over every ring (holes count as outside)"""
    c = False
    for ring in rings:
        j = len(ring) - 1
        for i in range(len(ring)):
            xi, yi = ring[i][0], ring[i][1]
            xj, yj = ring[j][0], ring[j][1]
            if (yi > lat) != (yj > lat) and lon < (xj - xi) * (lat - yi) / (yj - yi) + xi:
                c = not c
            j = i
    return c


stations = {}  # code -> [code, name, city, state, lat, lon]
for code, (p, c) in raw.items():
    state = p.get('state') or (nearest_state(c[1], c[0]) if c else None)
    if rings and c:
        if inside(c[0], c[1]):
            state = STATE
        elif state == STATE:
            state = nearest_state(c[1], c[0], 9) if nearest_state(c[1], c[0], 9) != STATE else None
    nm = title(p.get('name'))[:80]
    new_code, nm2, ct = RENAME.get(code, (code, nm, city(nm)))
    stations[code] = [new_code, nm2, ct, state, round(c[1], 5) if c else None, round(c[0], 5) if c else None]
karnataka = {code for code, s in stations.items() if s[3] == STATE}

# ---------------------------------------------------------------- DataMeet trains
by = collections.defaultdict(list)
for s in SC:
    by[s['train_number']].append(s)
TYPES = {'Exp': 'Express', 'SF': 'Superfast', 'Raj': 'Rajdhani', 'Drnt': 'Duronto', 'Shtb': 'Shatabdi', 'JShtb': 'Jan Shatabdi', 'GR': 'Garib Rath',
         'SKr': 'Sampark Kranti', 'Mail': 'Mail', 'Pass': 'Passenger', 'MEMU': 'MEMU', 'DEMU': 'DEMU', 'Toy': 'Toy train'}
CL = [('first_ac', 'AC1_TIER'), ('second_ac', 'AC2_TIER'), ('third_ac', 'AC3_TIER'), ('chair_car', 'AC_CHAIR_CAR'), ('sleeper', 'SLEEPER_CLASS')]


def km_along(stops, total):
    """km from the first stop: straight line x 1.25 between known positions, scaled to the published total"""
    pos = [(stations[x[0]][4], stations[x[0]][5]) if stations[x[0]][4] is not None else None for x in stops]
    cum = [None] * len(stops)
    cum[0] = 0.0
    acc, prev = 0.0, pos[0]
    for i in range(1, len(stops)):
        if pos[i] and prev:
            acc += hav(prev, pos[i]) * 1.25
            cum[i] = acc
        prev = pos[i] or prev
    idx = [i for i, c in enumerate(cum) if c is not None]
    if len(idx) < 2:
        cum = [float(i) for i in range(len(stops))]
    else:
        for i in range(len(cum)):
            if cum[i] is None:
                lo = max([j for j in idx if j < i], default=None)
                hi = min([j for j in idx if j > i], default=None)
                if hi is None:
                    cum[i] = cum[lo] + (i - lo) * 5
                else:
                    cum[i] = cum[lo] + (cum[hi] - cum[lo]) * (i - lo) / (hi - lo)
    if total and cum[-1] > 0:
        cum = [c * total / cum[-1] for c in cum]
    km = [0]
    for c in cum[1:]:
        km.append(max(int(round(c)), km[-1] + 1))
    return km


VB_NUMBERS = {20661, 20662, 26751, 26752, 20607, 20608, 22231, 22232, 20645, 20646, 20669, 20670}
trains, skipped = [], collections.Counter()
for f in T:
    p = f['properties']
    num = p['number']
    if not str(num).isdigit() or int(num) in VB_NUMBERS:
        skipped['bad or replaced number'] += 1
        continue
    sc = sorted([s for s in by.get(num, []) if s['day'] is not None], key=lambda s: (s['day'], s['id']))
    stops, seen = [], set()
    for s in sc:
        code = (s['station_code'] or '').strip().upper()
        if not CODE.match(code) or code in seen or code not in stations:
            continue
        seen.add(code)
        stops.append([code, hm(s['arrival']), hm(s['departure']), s['day']])
    if len(stops) < 2:
        skipped['fewer than 2 stops'] += 1
        continue
    first, last = stops[0], stops[-1]
    first[1] = first[1] or first[2]
    last[2] = last[2] or last[1]
    if first[2] is None or last[1] is None:
        skipped['missing times'] += 1
        continue
    # the real halts (the data also lists stations the train passes)
    stops = [first] + [x for x in stops[1:-1] if x[1] and x[2] and x[1] != x[2]] + [last]
    km = km_along(stops, p.get('distance') or 0)
    # longest run of halts inside Karnataka
    best, run = [], []
    for i, x in enumerate(stops):
        if x[0] in karnataka:
            run.append(i)
            if len(run) > len(best):
                best = run[:]
        else:
            run = []
    if len(best) < 2:
        skipped['fewer than 2 halts in Karnataka'] += 1
        continue
    part = [stops[i][:] for i in best]
    base_km, base_day = km[best[0]], part[0][3]
    if best[0] > 0:
        part[0][1] = part[0][2]  # starts here now: arrives = departs
    if best[-1] < len(stops) - 1:
        part[-1][2] = part[-1][1]  # ends here now
    classes = [c for k, c in CL if p.get(k)]
    coaches = None
    if not classes:
        classes, coaches = ['SECOND_CLASS'], {'SECOND_CLASS': 4}
    name = title(p['name'])[:80] if p['name'].isupper() else p['name'][:80]
    out_stops = [[stations[x[0]][0], x[1], x[2], km[i] - base_km, x[3] - base_day + 1] for x, i in zip(part, best)]
    trains.append([int(num), name, TYPES.get(p.get('type'), p.get('type') or 'Express'), classes, out_stops, None, coaches])

# ---------------------------------------------------------------- Vande Bharat (public timetables, 2025)
# stop = code, arrival, departure, km from the first stop; all on Day 1
VB = [
    (20661, 'KSR Bengaluru - Dharwad Vande Bharat', 'tuesday', 8,
     [('SBC', None, '05:45', 0), ('YPR', '05:55', '05:57', 6), ('TK', '06:36', '06:38', 70), ('DVG', '09:30', '09:32', 326),
      ('HVR', '10:20', '10:22', 394), ('UBL', '11:15', '11:20', 470), ('DWR', '12:00', None, 490)]),
    (20662, 'Dharwad - KSR Bengaluru Vande Bharat', 'tuesday', 8,
     [('DWR', None, '13:30', 0), ('UBL', '13:50', '13:55', 20), ('HVR', '14:48', '14:50', 96), ('DVG', '15:33', '15:35', 164),
      ('TK', '18:09', '18:11', 420), ('YPR', '19:21', '19:23', 484), ('SBC', '19:45', None, 490)]),
    (26751, 'Belagavi - KSR Bengaluru Vande Bharat', 'wednesday', 8,
     [('BGM', None, '05:35', 0), ('DWR', '07:18', '07:20', 121), ('UBL', '07:40', '07:45', 141), ('HVR', '08:25', '08:27', 217),
      ('DVG', '09:10', '09:12', 285), ('TK', '12:08', '12:10', 541), ('YPR', '12:58', '13:00', 605), ('SBC', '13:50', None, 611)]),
    (26752, 'KSR Bengaluru - Belagavi Vande Bharat', 'wednesday', 8,
     [('SBC', None, '14:20', 0), ('YPR', '14:30', '14:32', 6), ('TK', '15:08', '15:10', 70), ('DVG', '17:48', '17:50', 326),
      ('HVR', '18:48', '18:50', 394), ('UBL', '20:00', '20:05', 470), ('DWR', '20:25', '20:27', 490), ('BGM', '22:30', None, 611)]),
    (20607, 'MGR Chennai Central - Mysuru Vande Bharat', 'wednesday', 16,
     [('MAS', None, '05:50', 0), ('KPD', '07:13', '07:15', 130), ('SBC', '10:00', '10:05', 362), ('MYS', '12:20', None, 500)]),
    (20608, 'Mysuru - MGR Chennai Central Vande Bharat', 'wednesday', 16,
     [('MYS', None, '13:05', 0), ('SBC', '14:45', '14:50', 138), ('KPD', '17:33', '17:35', 370), ('MAS', '19:20', None, 500)]),
    (22231, 'Kalaburagi - SMVT Bengaluru Vande Bharat', 'friday', 8,
     [('KLBG', None, '06:10', 0), ('YG', '06:49', '06:50', 76), ('RC', '07:38', '07:40', 145), ('MALM', '07:58', '08:00', 173),
      ('GTL', '09:00', '09:05', 266), ('ATP', '10:03', '10:05', 333), ('SSPN', '11:13', '11:15', 400), ('YNK', '12:58', '13:00', 541),
      ('SMVB', '14:10', None, 558)]),
    (22232, 'SMVT Bengaluru - Kalaburagi Vande Bharat', 'thursday', 8,
     [('SMVB', None, '14:40', 0), ('YNK', '15:09', '15:11', 18), ('SSPN', '16:45', '16:47', 159), ('ATP', '17:33', '17:35', 225),
      ('GTL', '18:37', '18:42', 293), ('MALM', '19:48', '19:50', 386), ('RC', '20:18', '20:20', 414), ('YG', '21:04', '21:05', 483),
      ('KLBG', '22:40', None, 558)]),
    (20645, 'Madgaon - Mangaluru Central Vande Bharat', 'thursday', 8,
     [('MAO', None, '18:10', 0), ('KAWR', '18:55', '18:57', 82), ('UD', '21:12', '21:14', 350), ('MAQ', '22:45', None, 437)]),
    (20646, 'Mangaluru Central - Madgaon Vande Bharat', 'thursday', 8,
     [('MAQ', None, '08:30', 0), ('UD', '09:48', '09:50', 87), ('KAWR', '12:08', '12:10', 355), ('MAO', '13:10', None, 437)]),
    (20669, 'Hubballi - Pune Vande Bharat', None, 8,
     [('UBL', None, '05:00', 0), ('DWR', '05:18', '05:20', 21), ('BGM', '07:05', '07:10', 142), ('GPB', '07:48', '07:50', 200),
      ('MRJ', '08:55', '09:00', 279), ('SLI', '09:10', '09:13', 286), ('KOV', '09:38', '09:40', 319), ('STR', '10:47', '10:50', 413),
      ('PUNE', '13:30', None, 558)]),
    (20670, 'Pune - Hubballi Vande Bharat', None, 8,
     [('PUNE', None, '14:15', 0), ('STR', '16:32', '16:35', 146), ('KOV', '17:43', '17:45', 239), ('SLI', '18:12', '18:15', 272),
      ('MRJ', '18:40', '18:45', 279), ('GPB', '19:38', '19:40', 359), ('BGM', '20:15', '20:20', 417), ('DWR', '22:13', '22:15', 538),
      ('UBL', '22:45', None, 558)]),
]
VB_DAYS = {20669: ['sunday', 'wednesday', 'friday'], 20670: ['monday', 'thursday', 'saturday']}
by_new_code = {s[0]: k for k, s in stations.items()}
for s in NEW_STATIONS:
    stations[s[0]] = s
    by_new_code[s[0]] = s[0]
vb_codes = set()
for num, name, day_off, cars, stops in VB:
    days = VB_DAYS.get(num) or [d for d in ALL if d != day_off]
    ec = 2 if cars == 16 else 1
    out_stops = []
    for code, arr, dep, km in stops:
        assert code in by_new_code, code
        vb_codes.add(by_new_code[code])
        out_stops.append([code, arr or dep, dep or arr, km, 1])
    trains.append([num, name, 'Vande Bharat', ['AC_CHAIR_CAR', 'EXECUTIVE_CHAIR_CAR'], out_stops, days,
                   {'AC_CHAIR_CAR': cars - ec, 'EXECUTIVE_CHAIR_CAR': ec}])

keep = karnataka | vb_codes | {s[0] for s in NEW_STATIONS}
st = sorted((stations[k] for k in keep), key=lambda s: s[0])
data = {"source": "Karnataka trains: DataMeet Indian Railways data (CC0, 2016) and Vande Bharat timetables (2025)",
        "stations": st, "trains": sorted(trains)}
with gzip.open(out, 'wt', encoding='utf-8') as fh:
    json.dump(data, fh, separators=(',', ':'))
types = collections.Counter(t[2] for t in trains)
used = {s[0] for t in trains for s in t[4]}
print(len(trains), 'trains', len(st), 'stations (', len(used), 'with trains,', sum(1 for s in st if s[3] == STATE), 'in Karnataka )')
print(types.most_common())
print(skipped)
