# Release verification (v1.0)

Scripts used for the real-server NFO import verification and reproducible
fixture generation. They are documentation-grade tooling, not part of the
build.

- `nfo_fixture_gen.go.txt` — generates 18 NFO files (movie/tvshow/season/
  episode × kodi/emby/jellyfin) from the real `internal/nfo` builders.
  Copy it under the module (e.g. `reltest/_gen/main.go`) and run with
  `go run ./reltest/_gen` to write `reltest/jfmedia/`.
- `jf_import.py` / `jf_verify.py` — drives a Jellyfin (12.x) server: startup
  wizard, authentication, six libraries (three flavors × movie/tv) with the
  NFO local reader and online fetchers disabled, then per-library assertion
  of title/year/TMDB/IMDb/rating/overview/episode indexes (incl. multi-
  episode IndexNumberEnd).
- `emby_import.py` — the same flow against Emby 4.10.

## Verified results (2026-10-01, Jellyfin 12.1.0 / Emby 4.10.1)

- Movies: 盗梦空间, year 2010, tmdbId 27205, imdb tt1375666, rating 8.4,
  Chinese overview — all three flavors.
- TV: Series 北斗剧集 (tmdb 555, imdb tt555, rating 8.1), Season 1,
  episodes 启程 (tmdbId 9001) and multi-episode 连续两集 with
  IndexNumber=2 / IndexNumberEnd=3 — all three flavors.

## Known server-side nuance (not a writer defect)

Neither server applies NFO `<watched>`/`<playcount>` on import by default:
Jellyfin 12 requires the NFO plugin's UserId mapping (moved into its
database in 12.x — not configurable via file/API surface we could find),
Emby 4.10 applies userdata only through its own user-linking settings. The
tags follow the Kodi/TMM spec verbatim; Kodi itself imports them natively.
