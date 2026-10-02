import json, sys, time, urllib.request

J = "http://127.0.0.1:18097"
TOKEN = open("reltest/jf.token").read().strip()

def req(method, path, body=None):
    r = urllib.request.Request(J + path, method=method)
    r.add_header("Authorization", f'MediaBrowser Client="mml", Device="test", DeviceId="t1", Version="1.0", Token="{TOKEN}"')
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r, data) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:200]

EMPTY_FETCH = {"TypeOptions": [
    {"Type": t, "MetadataFetchers": [], "ImageFetchers": [], "MetadataFetcherOrder": [], "ImageFetcherOrder": []}
    for t in ["Movie", "Series", "Season", "Episode"]
]}

libs = [
    ("MoviesKodi", "movies", "/media/movie_kodi"),
    ("MoviesEmby", "movies", "/media/movie_emby"),
    ("MoviesJellyfin", "movies", "/media/movie_jellyfin"),
    ("TVKodi", "tvshows", "/media/tv_kodi"),
    ("TVEmby", "tvshows", "/media/tv_emby"),
    ("TVJellyfin", "tvshows", "/media/tv_jellyfin"),
]
for name, ctype, path in libs:
    opts = {"EnableRealtimeMonitor": False, "EnableAutomaticSeriesGrouping": False,
            "LocalMetadataReaderOrder": ["NFO"], "PathInfos": [{"Path": path}]}
    opts.update(EMPTY_FETCH)
    code, body = req("POST", f"/Library/VirtualFolders?name={name}&collectionType={ctype}&refreshLibrary=true&api_key={TOKEN}", {"LibraryOptions": opts})
    print(f"add {name}: {code}")
    if code not in (200, 204):
        print("  body:", body)
        sys.exit(1)

# Wait for the scans to settle.
for _ in range(60):
    code, items = req("GET", f"/Items?Recursive=true&api_key={TOKEN}")
    if code == 200 and items.get("TotalRecordCount", 0) >= 9:
        break
    time.sleep(1)

code, items = req("GET", f"/Items?Recursive=true&api_key={TOKEN}&Fields=ProviderIds,ProductionYear,Overview,CommunityRating")
print("total items:", items.get("TotalRecordCount"))
for it in sorted(items["Items"], key=lambda i: (i.get("CollectionType",""), i["Name"])):
    ud = it.get("UserData") or {}
    print(json.dumps({
        "type": it.get("Type"), "name": it.get("Name"), "year": it.get("ProductionYear"),
        "tmdb": (it.get("ProviderIds") or {}).get("Tmdb"), "imdb": (it.get("ProviderIds") or {}).get("Imdb"),
        "rating": it.get("CommunityRating"), "overview": (it.get("Overview") or "")[:24],
        "index": [it.get("ParentIndexNumber"), it.get("IndexNumber"), it.get("IndexNumberEnd")],
        "playcount": ud.get("PlayCount"), "played": ud.get("Played"),
    }, ensure_ascii=False))
