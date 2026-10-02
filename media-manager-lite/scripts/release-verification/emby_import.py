import json, time, urllib.request

E = "http://127.0.0.1:18098/emby"
TOKEN = open("reltest/emby.token").read().strip()

def req(method, path, body=None):
    r = urllib.request.Request(E + path, method=method)
    r.add_header("Authorization", f'MediaBrowser Client="mml", Device="test", DeviceId="t1", Version="1.0", Token="{TOKEN}"')
    data = json.dumps(body).encode() if body is not None else None
    if data: r.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(r, data) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()[:300]

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
            "LocalMetadataReaderOrder": ["NFO"], "PathInfos": [{"Path": path}],
            "TypeOptions": [
                {"Type": t, "MetadataFetchers": [], "ImageFetchers": [],
                 "MetadataFetcherOrder": [], "ImageFetcherOrder": []}
                for t in ["Movie", "Series", "Season", "Episode"]]}
    code, body = req("POST", f"/Library/VirtualFolders?name={name}&collectionType={ctype}&refreshLibrary=true&api_key={TOKEN}", {"LibraryOptions": opts})
    print(f"add {name}: {code}")
    if code not in (200, 204):
        print("  body:", body); sys.exit(1)

for _ in range(45):
    code, items = req("GET", f"/Items?Recursive=true&api_key={TOKEN}&IncludeItemTypes=Movie,Series,Episode")
    if code == 200 and items.get("TotalRecordCount", 0) >= 9:
        break
    time.sleep(2)

UID = "09071ad9362c40839d4faa3d18507551"
code, items = req("GET", f"/Items?Recursive=true&UserId={UID}&api_key={TOKEN}&Fields=ProviderIds,ProductionYear,Overview,CommunityRating")
print("total:", items.get("TotalRecordCount"))
for it in sorted(items["Items"], key=lambda i: (i.get("Type",""), i["Name"])):
    if it.get("Type") == "Folder": continue
    ud = it.get("UserData") or {}
    print(json.dumps({
        "type": it.get("Type"), "name": it.get("Name"), "year": it.get("ProductionYear"),
        "tmdb": (it.get("ProviderIds") or {}).get("Tmdb"), "imdb": (it.get("ProviderIds") or {}).get("Imdb"),
        "rating": it.get("CommunityRating"), "overview": (it.get("Overview") or "")[:20],
        "idx": [it.get("ParentIndexNumber"), it.get("IndexNumber"), it.get("IndexNumberEnd")],
        "playcount": ud.get("PlayCount"), "played": ud.get("Played"),
    }, ensure_ascii=False))
