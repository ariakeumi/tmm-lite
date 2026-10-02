import json, time, urllib.request

J = "http://127.0.0.1:18097"
TOKEN = open("reltest/jf.token").read().strip()
UID = "ef40de8520eb4d78a85d64861da60a0c"

def req(method, path, body=None):
    r = urllib.request.Request(J + path, method=method)
    r.add_header("Authorization", f'MediaBrowser Client="mml", Device="test", DeviceId="t1", Version="1.0", Token="{TOKEN}"')
    data = json.dumps(body).encode() if body is not None else None
    if data: r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r, data) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:300]

# Per-library verification.
code, views = req("GET", f"/Users/{UID}/Views?api_key={TOKEN}")
for view in views["Items"]:
    name, vid = view["Name"], view["Id"]
    code, items = req("GET", f"/Items?ParentId={vid}&Recursive=true&UserId={UID}&api_key={TOKEN}&Fields=ProviderIds,ProductionYear,Overview,CommunityRating,UserData")
    kinds = {}
    for it in items.get("Items", []):
        kinds.setdefault(it["Type"], []).append(it)
    print(f"== {name}: {items.get('TotalRecordCount')} items, types={ {k: len(v) for k,v in kinds.items()} }")
    for t in ["Movie", "Series", "Season", "Episode"]:
        for it in sorted(kinds.get(t, []), key=lambda i: i["Name"]):
            ud = it.get("UserData") or {}
            print(f"  [{t}] {it['Name']} y={it.get('ProductionYear')} tmdb={(it.get('ProviderIds') or {}).get('Tmdb')} imdb={(it.get('ProviderIds') or {}).get('Imdb')} r={it.get('CommunityRating')} play={ud.get('PlayCount')} played={ud.get('Played')} idx=[{it.get('ParentIndexNumber')},{it.get('IndexNumber')},{it.get('IndexNumberEnd')}]")
