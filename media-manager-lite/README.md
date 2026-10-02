# Media Manager Lite

A lightweight, web-based media metadata manager for Jellyfin / Emby / Kodi
libraries. Go + SQLite + HTMX, single static binary, Docker-friendly
(linux/amd64 + linux/arm64).

**Status: v1.0.** Movies and TV shows: scan → match (TMDB, zh-CN with
translation fallback) → enrich → artwork → NFO (kodi/emby/jellyfin) →
rename (dry-run + execute). The NFO output is verified against real
Jellyfin 12 and Emby 4.10 imports (see
`scripts/release-verification/README.md`).

## ⚠️ Security notice — read first

**The HTTP server has NO authentication.** Anyone who can reach the port
can create libraries, trigger scans, and (via rename) move files inside
your library roots.

- Bind to localhost only (`MEDIA_MANAGER_HTTP_ADDR=127.0.0.1:8080`), or
- keep it on a trusted intranet / VPN, or
- put it behind an authenticating reverse proxy (basic auth, SSO, …).

Never expose it directly to the internet. The TMDB API key is stored in
the local SQLite database and is never returned by the API, written to
logs, or included in error messages.

## Quick start (Docker, recommended)

```sh
docker compose up -d --build
# http://localhost:8080
```

Prebuilt multi-arch images (linux/amd64 + linux/arm64) are published to
GHCR by GitHub CI — pull:

```sh
docker pull ghcr.io/ariakeumi/media-manager-lite:latest
```

Images are built automatically: `docker-release.yml` on `v*` tags
(`:latest` + semver tags) and `docker-dev.yml` keeps branch images current
for `main`/`devel` (`.github/workflows/`). `make docker` builds the same
multi-arch image locally (~26 MB distroless).

### Volumes

| Container path | Purpose                                     |
|----------------|---------------------------------------------|
| `/config`      | SQLite database (`media-manager.db`) + WAL   |
| `/media/...`   | Your media directories (mount read-write; rename moves files) |

The container runs as user `65532` (nonroot). Make sure the host `config/`
directory is writable by that UID, e.g.
`mkdir -p config && sudo chown 65532:65532 config`.

### Workflow

1. **Libraries** — add a Movie or TV library pointing at a mounted media
   directory. Paths must exist, be directories, and be readable; they are
   canonicalized (symlinks resolved) and can never be escaped.
2. **Scan** — async task; discovers video files, parses filenames
   (title/year/resolution/codec/HDR; S01E02, multi-episode, anime and date
   formats for TV). Unidentified videos are listed under
   `GET /api/libraries/{id}/skipped`.
3. **Match** — per file/show TMDB search with scored candidates; nothing
   is written until you confirm. Show matching is transactional and then
   enriches seasons/episodes asynchronously.
4. **Artwork** — poster/fanart (movies), show poster/fanart + season
   posters (TV) downloaded into the media folders; language-aware ranking,
   atomic writes.
5. **NFO** — `movie.nfo`/`<name>.nfo`, `tvshow.nfo`, `season%02d.nfo`
   (+ `season.nfo` inside the season folder), `<episode>.nfo`
   (kodi/emby/jellyfin flavor via the `nfo_flavor` setting). Existing NFO
   user data (watched/playcount/userrating) is merged with max rules;
   unrecognized XML tags are preserved verbatim; unchanged files are
   skipped. Flavor setting:
   `sqlite3 /config/media-manager.db "INSERT OR REPLACE INTO settings(key,value,updated_at) VALUES('nfo_flavor','emby',datetime('now'))"`
   (kodi | emby | jellyfin).
6. **Rename** — preview is a pure read-only plan (dry run); execute moves
   files into `Title (Year)/Title (Year).ext` (movies) or
   `Show/Season 01/Show - S01E01 - Title.ext` (TV). Conflicting
   destinations abort the whole plan; nothing is deleted; re-running an
   executed rename is a no-op.

## Configuration

Precedence: defaults < YAML file < environment.

| Environment variable        | Default                 | Meaning                        |
|-----------------------------|-------------------------|--------------------------------|
| `MEDIA_MANAGER_CONFIG`      | _(unset)_               | YAML config file path          |
| `MEDIA_MANAGER_HTTP_ADDR`   | `:8080`                 | HTTP bind address (use `127.0.0.1:8080` for localhost-only) |
| `MEDIA_MANAGER_DB`          | `data/media-manager.db` | SQLite path                    |
| `MEDIA_MANAGER_LOG_LEVEL`   | `info`                  | debug / info / warn / error    |
| `MEDIA_MANAGER_TMDB_API_KEY`| _(unset)_               | bootstrap TMDB key (settings table wins) |
| `MEDIA_MANAGER_TMDB_BASE_URL`| TMDB v3 API            | override for tests/proxies     |

### TMDB API key

Register a free key at <https://www.themoviedb.org/settings/api>, then:

```sh
curl -X PUT http://localhost:8080/api/settings/tmdb-api-key \
     -H 'Content-Type: application/json' -d '{"apiKey":"YOUR_KEY"}'
# check: curl http://localhost:8080/api/settings/tmdb-api-key  → {"configured":true}
```

The key is stored in SQLite and never echoed back.

## Jellyfin / Emby / Kodi usage

- NFO files follow the Kodi/TMM convention: `movie.nfo` or `<video>.nfo`,
  `tvshow.nfo`, `season%02d.nfo` (+ `season.nfo` inside the season
  folder), `<video>.nfo` for episodes. Multi-episode files carry one
  `<episodedetails>` root per episode (Emby additionally gets
  `<episodenumberend>`).
- In Jellyfin/Emby add the media folders as libraries with the **NFO local
  metadata reader** enabled and online fetchers disabled to rely purely on
  the files. Verified import fields: title (incl. Chinese), year,
  TMDB/IMDb ids, ratings, overview, season/episode numbers (incl.
  multi-episode), poster/fanart.
- Kodi consumes the same files natively, including `<watched>` /
  `<playcount>`. Note: Jellyfin 12 and Emby 4.10 treat watched state as
  their own per-user data and do not import those two tags without extra
  server-side user mapping — server behavior, not a file-format issue.

## Backup / upgrade

- **Backup**: stop the container (or ensure no task is running), then copy
  `config/media-manager.db` plus `-wal`/`-shm` siblings if present. Media
  files are never touched except through explicit artwork/NFO/rename
  operations.
- **Restore**: put the DB file back and start the container; migrations
  are idempotent and applied at startup.
- **Upgrade**: pull the new image and recreate the container; the SQLite
  schema migrates automatically (append-only migrations, data preserved).
  Downgrading across schema versions is not supported.

## HTTP surface

Interactive pages: `/`, `/libraries`, `/libraries/{id}/match`,
`/movies/{id}`, `/tv`, `/tv/shows/{id}`, `/settings`. JSON API endpoints
are listed in `docs/decisions.md` and the security-relevant ones in
`scripts/release-verification/README.md`. All writes go through the task
queue; rename previews are read-only.

## Make targets

`make build` · `make test` · `make lint` · `make run` · `make docker`
(multi-arch) · `make build-linux-arm64` · `make build-linux-amd64` · `make tidy`

## Layout

```
cmd/media-manager/   entrypoint: config → db → services → http → graceful shutdown
internal/paths/      path canonicalization/validation shared by library + scanner
internal/library/    library model, repository, validation service
internal/parser/     movie + TV episode filename parsing (pure)
internal/scanner/    library walk → parse → persist (movie + tv dispatch)
internal/media/      media_items repository (scan results + match candidates)
internal/matcher/    candidate scoring (movie + tv, pure)
internal/tmdb/       TMDB client: retry/backoff/cache, zh→zh-CN, fallback chain
internal/movie/      movie records + transactional match confirmation
internal/tv/         tv show/season/episode persistence + scrape orchestration
internal/artwork/    artwork ranking + atomic downloads (movie + tv)
internal/nfo/        NFO writer/parser (kodi/emby/jellyfin) + golden tests
internal/renamer/    rename planner (pure, dry-run) + executor (strict conflicts)
internal/task/       task store + single-worker async runner (restart policy)
internal/settings/   key/value settings (TMDB key, languages, flavors, profiles)
internal/http/       net/http server: JSON API + html/template HTMX pages
web/                 embedded templates + static assets (no npm build chain)
migrations/          embedded SQL migrations (applied at startup)
tests/               cross-package integration tests
docs/                engineering decisions
scripts/release-verification/  real-server NFO import verification tooling
```

## Configuration

Precedence: defaults < YAML file < environment. If the file named by
`MEDIA_MANAGER_CONFIG` does not exist, defaults are used silently.

| Environment variable      | Default                   | Meaning                     |
|---------------------------|---------------------------|-----------------------------|
| `MEDIA_MANAGER_CONFIG`    | _(unset)_                 | Path to a YAML config file  |
| `MEDIA_MANAGER_HTTP_ADDR` | `:8080`                   | HTTP listen address         |
| `MEDIA_MANAGER_DB`        | `data/media-manager.db`   | SQLite database path        |
| `MEDIA_MANAGER_LOG_LEVEL` | `info`                    | debug / info / warn / error |

YAML keys mirror the fields: `http_addr`, `db_path`, `log_level`.

## HTTP surface

| Route                              | Description                                  |
|------------------------------------|----------------------------------------------|
| `GET /`                            | Dashboard (library/movie/task counts)        |
| `GET /libraries`                   | Libraries page (add/scan/delete via HTMX)    |
| `GET /settings`                    | Settings placeholder                         |
| `GET /api/health`                  | `{"status":"ok"}`                            |
| `GET /api/version`                 | Version/commit/build date (ldflags-injected) |
| `GET /api/libraries`               | List libraries                               |
| `POST /api/libraries`              | Create library (`{name, path, type}`; type `movie`\|`tv`) |
| `GET /api/libraries/{id}`          | Library details                              |
| `DELETE /api/libraries/{id}`       | Delete the library **database record only** — media files are never touched |
| `POST /api/libraries/{id}/scan`    | Queue an async scan (single worker; 202 + pending task; TV libraries rejected until milestone 4). Poll `GET /api/tasks` |
| `GET /api/libraries/{id}/media`    | Media items discovered by the last scan (path, size, mtime, parsed title/year/tags) |
| `GET /api/libraries/{id}/skipped`  | Videos the scan could not identify (path, filename, reason) |
| `GET /libraries/{id}/match`        | HTMX match page: search per file, review candidates, confirm |
| `POST /api/movies/{id}/search`     | TMDB search for a media item ({id} = media item id, see decisions D14); scores + persists candidates |
| `GET /api/movies/{id}/candidates`  | Persisted candidates for a media item |
| `POST /api/movies/{id}/match`      | Confirm a match (`{"tmdbId": N}`) or mark unmatched (`{"unmatched": true}`) — transactional |
| `POST /api/movies/{id}/artwork`    | Queue async poster/fanart download into the media folder (ranking: zh → en → votes) |
| `POST /api/movies/{id}/nfo`        | Write the movie NFO (kodi/emby/jellyfin via the `nfo_flavor` setting); merges user data, skips unchanged |
| `GET /api/movies/{id}/nfo`         | Return the current NFO file content |
| `GET /movies/{id}`                 | Movie workflow page: match state, artwork/NFO status, rename preview + execute |
| `GET /api/movies/{id}/rename`      | Rename plan (dry-run — read-only, zero filesystem writes) |
| `POST /api/movies/{id}/rename`     | Execute the rename plan (video/folder strict-conflict, NFO/artwork variants idempotent) |
| `GET /api/movies/{id}`             | Media item + linked movie (post-match) |
| `GET /api/movies?libraryId=`       | Matched movies of a library |
| `GET /api/tv/shows`                | TV shows (`?libraryId=` filter) |
| `GET /api/tv/shows/{id}`           | Show details + seasons |
| `GET /api/tv/shows/{id}/episodes`  | Episodes ordered by season/episode |
| `GET /tv`, `GET /tv/shows/{id}`    | TV pages: show list + show match/confirm flow (HTMX) |
| `POST /api/tv/shows/{id}/search`   | TMDB show search; scores + persists candidates |
| `GET /api/tv/shows/{id}/candidates`| Persisted show candidates |
| `POST /api/tv/shows/{id}/match`    | Confirm show match (`{"tmdbId": N}`, transactional) or unmatch; episode enrichment runs async |
| `POST /api/tv/shows/{id}/nfo`      | Write tvshow.nfo + season%02d.nfo + episode NFOs (kodi/emby/jellyfin via `nfo_flavor`) |
| `POST /api/tv/shows/{id}/artwork`  | Queue async TV artwork (show poster/fanart + season posters) |
| `GET /api/episodes/{id}/rename`    | Episode rename plan (dry-run — read-only) |
| `POST /api/episodes/{id}/rename`   | Execute episode rename into ShowName/Season NN (idempotent) |
| `GET /api/tasks`                   | 50 most recent tasks |
| `PUT /api/settings/tmdb-api-key`   | Store the user's own TMDB API key (never echoed, never logged) |
| `GET /api/settings/tmdb-api-key`   | `{"configured": bool}` |

HTMX form submissions (`HX-Request: true`) receive HTML fragments; all other
clients get JSON with proper status codes (400 validation, 404, 409 duplicate
path).

## Make targets

`make build` · `make test` · `make lint` · `make run` · `make docker`
(multi-arch) · `make docker-arm64` · `make build-linux-arm64` ·
`make build-linux-amd64` · `make tidy`

## Layout

```
cmd/media-manager/   entrypoint: config → db → services → http → graceful shutdown
internal/config/     env + optional YAML config
internal/database/   SQLite open (WAL, foreign_keys, busy_timeout) + migrations + stats
internal/paths/      path canonicalization/validation shared by library + scanner
internal/library/    library model, repository, validation service
internal/parser/     pure movie filename parsing (title/year/resolution/codec/HDR/audio/part)
internal/scanner/    recursive library walk → video candidates → parse → persist
internal/media/      media_items repository (scan results + match candidates)
internal/matcher/    candidate scoring (StrikeAMatch, Jaro-Winkler, year penalty) — pure
internal/tmdb/       TMDB client: search/details/find, zh→zh-CN, translation fallback
internal/artwork/    poster/fanart ranking + atomic .part download into the media folder
internal/nfo/        movie NFO writer/parser (kodi/emby/jellyfin) with golden-file tests
internal/renamer/    rename planner (pure, dry-run) + executor (strict conflicts) + service
internal/tv/         tv_shows/tv_seasons/tv_episodes persistence (idempotent upserts)
internal/movie/      movie records + transactional match confirmation
internal/settings/   key/value settings (TMDB API key, metadata language)
internal/task/       task store + single-worker async runner (restart policy)
internal/http/       net/http server: JSON API + html/template HTMX pages
internal/uid/        dependency-free UUIDv4 generation
internal/version/    ldflags-injected build metadata
web/                 embedded templates + static assets (no npm build chain)
migrations/          embedded SQL migrations (applied at startup)
tests/               cross-package integration tests
docs/                engineering decisions
```

## Security baseline (Milestone 1)

- All user-supplied paths are canonicalized (`Clean` + `EvalSymlinks`) and
  must exist, be a directory, and be readable before a library is accepted.
- SQLite access is strictly parameterized; HTML is rendered with
  `html/template` (auto-escaping).
- `DELETE` on a library only removes database rows (with `ON DELETE CASCADE`
  for future child records) — never filesystem data.
- Request bodies are size-limited (1 MiB); no request bodies are logged.
- API errors never expose server filesystem details.
