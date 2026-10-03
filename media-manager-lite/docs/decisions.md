# Engineering decisions — Milestone 1

Small decision log; append new entries as they are made.

## D1 — Project lives in `media-manager-lite/` inside the tinyMediaManager repo

The Go module is a self-contained subdirectory (own `go.mod`, Docker context,
Makefile). The upstream Java build (`pom.xml`, `src/`) is untouched; the
`.git` history stays shared until the project is extracted into its own
repository.

## D2 — `internal/http` package name

The requested tree uses `internal/http/`. A Go package named `http` may
legally `import "net/http"` (the imported name shadows nothing of its own),
so the directory name is kept verbatim. Import sites that need both packages
alias ours to `httpapi`. If this ever becomes a maintenance burden, rename
the directory to `internal/httpapi` — a mechanical change.

## D3 — SQLite connection policy

`MaxOpenConns(1)`: a single connection eliminates SQLITE_BUSY cross-connection
contention entirely; the Milestone-1 write volume is trivial. WAL +
`busy_timeout(5000)` + `foreign_keys(1)` are set via DSN so every pooled
connection (including future ones) inherits them. Revisit when background
tasks (later milestone) need concurrent reads.

## D4 — Timestamps as RFC3339 UTC TEXT

All `created_at`/`updated_at` columns are `TEXT` in RFC3339 UTC, formatted by
the application. Deterministic, human-readable, driver-independent; avoids
modernc/sqlite time-scanning quirks. Ordering by string comparison is correct
for RFC3339 UTC.

## D5 — IDs are application-generated UUIDv4 strings

No autoincrement: IDs are generated in `internal/uid` (crypto/rand, no
dependency), matching the Phase-0 observation that TMM also keys entities by
UUID.

## D6 — UI assets are embedded

`web/templates` and `web/static` are embedded via `go:embed` (`web/web.go`).
The distroless image ships a single binary with no data files. HTMX itself is
loaded from a pinned CDN (`htmx.org@2.0.4`); vendoring it into `web/static`
is a one-line change for offline deployments.

## D7 — Dual-mode responses on library endpoints

Library create/delete handlers answer with JSON for API clients (proper
400/404/409/204 statuses) and with an HTML fragment for HTMX requests
(`HX-Request: true`). In fragment mode validation errors return HTTP 200 with
the error rendered inside the swapped panel — the classic server-rendered
form pattern, which keeps behavior identical across HTMX versions (non-2xx
responses are not swapped by default). API semantics are unaffected.

## D8 — No ORM, no router, no test framework

`database/sql` + embedded SQL migrations; Go 1.22+ `ServeMux` method patterns
for routing; stdlib `testing` + `httptest`. Dependencies: `modernc.org/sqlite`
(pure Go, CGO-free, ARM64-safe) and `gopkg.in/yaml.v3` (config file).

## D9 — Go toolchain floor: 1.26 (builder: golang:1.27-alpine)

`go mod tidy` resolved the current `modernc.org/sqlite`, which requires
`go >= 1.26`, so `go.mod` declares `go 1.26.0` and the Dockerfile build stage
uses `golang:1.27-alpine` (verified multi-arch). If a lower toolchain floor is
ever required, pin an older `modernc.org/sqlite` release.

## D10 — Milestone-2 tasks run inline (no worker pool yet)

`task.Store.Run` inserts a `pending` row, flips it to `running`, executes the
job synchronously and records `completed`/`failed` — job failures live on the
task row, only database failures surface as errors. This keeps M2 free of
goroutine plumbing while the schema and JSON shape already match a future
async queue (worker pool, cancellation, progress are explicitly deferred).
Consequence: a scan blocks its HTTP request; that is acceptable at current
scale and revisited in Milestone 3.

## D11 — Scanner columns on media_items (migration 002)

`filename`, `mod_time` and `parsed_json` were added to media_items. The full
parser output lives in `parsed_json` until the matcher milestone decides
which fields deserve promoted columns; `parsed_title`/`parsed_year` are
promoted already because every list view and the upcoming TMDB search need
them. Upserts are keyed by `(library_id, path)` inside one transaction:
re-scans refresh statistics/metadata and keep id, status and created_at.

## D12 — Movie filename parser scope

Basic, testable rules only: dots/underscores/spaces are separators (hyphens
are preserved inside titles, so pure hyphen-separated names are unsupported
for now); the year is the last standalone 1900–2029 token before the first
technical tag (so "Blade.Runner.2049" keeps 2049 in the title, and
"1917.2019" resolves to title "1917" year 2019); if consuming the year would
leave an empty title, the token is the title and the year is unknown. Year
tokens after the first technical tag are ignored ("Movie.Part.1.2009" loses
the year — accepted). Samples/trailers are skipped by exact name, `sample`
prefix or `sample|trailer` suffix; the `trailer.` *prefix* is deliberately
kept so "Trailer.Park.Boys" survives. Symlinked directories are never
traversed; symlinked files must resolve inside the library root.

## D13 — Path canonicalization shared via internal/paths

The canonicalize/validate logic moved out of the library service into
`internal/paths` so the scanner can re-validate the library root on every
walk (an unmounted volume fails loudly instead of yielding an empty scan).
The library sentinel errors alias the paths errors, so HTTP status mapping
is unchanged.

## D14 — `/api/movies/{id}` addresses the media item until the match exists

Phase 0's schema matches at media_item granularity (`media_items.movie_id`,
`status new→matched/unmatched`), so before a match is confirmed there is no
movie record to address. The M3 routes therefore interpret `{id}` in
`/api/movies/{id}/search|candidates|match` as the **media item id**; after
confirmation, `GET /api/movies/{id}` returns the item plus its linked movie
and `GET /api/movies?libraryId=` lists real movie rows. Documented here so
the API contract is explicit rather than accidental.

## D15 — Task runner: single worker, explicit restart policy

`task.Runner` keeps the M1 schema and the M2 JSON shape but executes on one
background worker: `Submit` inserts `pending` and returns immediately
(HTTP 202); the worker flips to `running` and records `completed`/`failed`.
Restart policy: stale `running` rows from a previous process are marked
`failed` ("interrupted by restart"); `pending` rows are re-enqueued because
the only current job (library scan) is idempotent. Handlers are registered
per task type so recovery can rebuild the job from `(type, target)`. No
retry, scheduling or worker pool yet. Current jobs receive the shutdown
context, so a SIGTERM mid-scan fails that task — it will be re-enqueued on
the next start only if it was still pending (tracked as a known issue).

## D16 — TMDB configuration and language defaults

The API key lives in the `settings` table (`tmdb_api_key`, written via
`PUT /api/settings/tmdb-api-key`) with `MEDIA_MANAGER_TMDB_API_KEY` as a
bootstrap fallback; it is never echoed by the API, never logged, and error
strings are redacted (a sanitized wrapper keeps errors.Is working for
context cancellation). Default metadata language is `zh` (expanded to
`zh-CN` per the TMM language→country table); fallback language `en-US`;
certification country `US` (settings-overridable, not yet surfaced in the
UI). Translation fallback chain, loose-match exclusions (es-MX/pt-BR/fr-CA)
and the search cascade (year filter dropped when the strict search is empty)
follow Phase 0 §3.2.

## D17 — NFO writer scope and determinism

The writer replicates the Phase-0 connector spec for movies: fixed element
order, `<id>` = IMDb (written even when empty), `uniqueid` default on IMDb
(else TMDB), Kodi-style `<ratings>` with the TMDB source renamed
"themoviedb", `<set tmdbcolid>` for basic movie-set info, Emby
credits/directors-after-actors + `<lockdata>`, Jellyfin
`<collectionnumber>`. Round-trip: user data (watched/playcount merged with
max rules, lastplayed/dateadded/userrating kept) comes from the first
parseable existing NFO (`<basename>.nfo`, then `movie.nfo`); every unknown
root-level element is re-emitted verbatim (raw inner XML via encoding/xml's
innerxml) except `lockdata`, which TMM also drops. Writes are skipped when
the document is unchanged modulo comments, so repeated runs are stable.
Deviation from TMM, documented: no `<fileinfo>` yet (media info parsing is a
future milestone — pre-existing fileinfo elements survive verbatim), mpaa is
the raw certification and `certification` gets a `US:` country prefix, and
the fixed file names are `poster.jpg`/`fanart.jpg` (naming variants arrive
with the renamer).

## D18 — Renamer: planner/executor split and folder semantics

`PlanRename` is pure: it renders `${token}` templates (TMM-compatible token
names), applies the createDestination sanitation pipeline (illegal
characters removed, empty bracket groups stripped, whitespace collapsed,
edge separators/trailing dots trimmed — which also defuses `../` fragments)
and computes the action list against a read-only FSView. `Execute` does all
filesystem work: preflight re-validation, folder rename, video rename
(strict — an existing different destination fails the whole plan before any
modification), then idempotent derived copies for the NFO/artwork naming
variants (overwrites allowed, matching TMM's copy-1:N semantics; this is
what makes re-runs safe).

Folder semantics: a movie sharing its folder with other videos never gets
a folder rename (the move would strand them), and **the library root itself
is never renamed** — discovered while testing: renaming a directory into
its own subdirectory is impossible and catastrophic. Like TMM's
"upgrade" case, movies sitting directly in the root get their files
gathered into a newly created subfolder (`create_dir` action). All
destinations are validated to stay inside the library root (defense in
depth: sanitizer + within-root check on every action).

DB follow-up after a successful execution (service layer, not the
executor): `media_items.path`/filename and the `art_json` file paths are
updated. Unwanted stale variants (e.g. an old `<oldbase>.nfo` after a
basename change) are NOT deleted yet — cleanup requires the TMM-style
backup mechanism and is deferred deliberately (M1 rule: never delete user
files by default).

## D19 — TV episode parsing and hierarchy persistence

`parser.ParseEpisode` follows the TMM `TvShowEpisodeAndSeasonParser`
behavior for the common cases: SxxEyy with chained stacked/range markers
(S01E02E03, S01E02-E03 — dash ranges expand, chains must be adjacent and
plausible next-episode numbers so "1080p" is never swallowed), 1x02, word
forms in several languages (Season/Saison/Staffel/Temporada + Episode),
date-based daily shows (**Season = year, Episodes = [month*100+day]**
because the struct has no date slot; ambiguous 03.05.2021 resolves as
DD.MM like TMM), Part/Disc markers with roman numerals (season 1) and
anime styles (leading [Group] tags, trailing 2–3 digit absolute numbers
with optional v2 suffix, bracket numbering [13][1080p] → Absolute set).
The show title comes from the first path component under the library root
(season containers are not show names; flat layouts fall back to the
text before the marker, like TMM's cleanEpisodeTitle).

Persistence: shows keyed by (library, title), seasons by (show, number),
episodes by (show, season, episode) — all upserts idempotent. Multi-
episode files create one episode row per number while the media item
links to the first; the full ParsedEpisode JSON lives in parsed_json.
Files without a recognizable marker are skipped and counted in the scan
stats (not persisted as broken rows).

## D20 — TMDB TV matching flow and EPISODE_TRANS

TV matching reuses the M3 machinery end to end (client, language expansion,
translation fallback chain, error mapping, key redaction); only the
endpoint set is new (search/tv, tv/{id}, season/{n}, episode/{n},
episode_group/{id}).

**EPISODE_TRANS** (`IsGenericEpisodeTitle`): TMDB machine-translates
episode names, so a zh-CN season list arrives as "第 1 集"-style garbage.
A title is generic when it reduces to a localized "episode" word after
stripping digits/punctuation (the word list mirrors TMM's language
coverage: en/de/fr/es/pt/pl/ru/nl/sv/fi/tr/cs/el/he/ja/ko/zh), when it
matches the CJK 第…集/第…話/第…话/第…季 pattern, or when it is
number-only/empty. `TVSeasonWithFallback` merges two tiers (requested
language + fallback language, TMM uses four): generic titles are replaced
by the fallback-language name only when that name is itself specific;
season names get the same treatment. The simple "第 1 季"/"Episode 1"
pair stays as requested — nothing better exists.

**Confirmation split**: `POST /api/tv/shows/{id}/match` fetches the show
detail and commits the show row transactionally (tmdb_id, best-title,
stats, metadata_json, candidates cleared); the per-season episode
enrichment then runs as an async `scrape_tv_episodes` task so a 20-season
show never blocks the HTTP request. The confirmation itself is
transactional as required; the enrichment is idempotent and re-runnable.
Episode rows are enriched by season/episode number match; TMDB episodes
the scanner has not seen are skipped (they appear after the next scan).
Unmatch clears the show fields and resets scraped episode fields to the
parsed-from-filename state. Show candidates persist in
`tv_shows.candidates_json` (migration 004), mirroring media_items.

## D21 — TV NFO and per-file TV renamer

**TV NFO**: three new builders reuse the movie machinery (builder, flavor
deltas, atomic write, unchanged-skip, unknown-element preservation).
`tvshow.nfo` lands in the common episode directory — or its parent when
that directory is a season folder (Show/Season layouts), mirroring TMM's
show-folder placement; `season%02d.nfo` (specials: `season-specials.nfo`)
sits next to the season's episodes; episodes get `<basename>.nfo` with
multi-episode files emitting one `<episodedetails>` root per number (XML
declaration and comment on the first block only, Emby `episodenumberend`).
Episode `<id>`/uniqueid use the TMDB episode id where known; the show
`<id>` stays empty (no TVDB integration, matching TMM's TVDB-first element
with an empty value). User-data round-trip aggregates per-block with
additive playcount / first-wins dates / max userrating.

**Enrich payload preservation**: the M7 enrich step now nests its data
under `metadata_json.tmdb` instead of overwriting the scanner's
ParsedEpisode payload — M8's NFO/renamer flows read the episode numbering
from that payload (`EpisodeNumbersForItem`).

**TV renamer**: `PlanEpisodeRename` reuses the strict-conflict executor and
containment checks but never RENAMES existing folders — it creates the
missing `ShowName/Season NN` structure (create_dir) and moves only the
target file plus its NFO basename variant into place. Rationale: a show
folder may own several season folders with differently named siblings; a
folder rename would relocate them all, and old-name season folders ("S01",
"Season 1") cannot be safely merged without a cleanup policy (deferred
since M5). Per-file moves are idempotent: a second run finds the file in
place and yields zero actions. The season folder is recognized as
already-present when the current directory's parent is the show folder and
its basename matches the sanitized pattern; files nested deeper keep their
location (never moved up). Multi-episode numbering follows the profile
style: REPEAT ("05E06") or RANGE ("05-E06", contiguous only, non-
contiguous falls back to REPEAT).

## D22 — M9 polish: TV artwork, episode ids, group mapping, scan feedback

**TV artwork** reuses the movie download stack (BestImage ranking, magic
validation, atomic temp+rename): show `poster.jpg`/`fanart.jpg` land in the
show folder (same common-dir rule as the TV NFO writer) and
`seasonXX-poster.jpg` in every scanned season folder, via the async
`download_tv_artwork` task. `artwork_status` on tv_shows mirrors movies.

**Episode TMDB id**: `tv.Episode.TMDBID` is extracted on read from
`metadata_json` (nested `tmdb.tmdbId` from enrich, legacy flat `tmdbId`
also honored) — no schema change, old rows keep working. The episode NFO
writer now emits `<id>` + `<uniqueid type="tmdb" default="true">` from it.

**Episode group mapping** (Phase 0 §3.2 item 4): EnrichEpisodes was
restructured — the show detail is fetched once, all TMDB seasons
(1..numberOfSeasons) are indexed by id and S/E, and each scanned episode
row matches directly by S/E. For absolute-order groups (type 2) scanned
season-1 episodes instead map through `group order + 1` to the real TMDB
S/E placement (recorded as `tmdbRealSeason/tmdbRealEpisode` in metadata).
Normal S/E shows behave exactly as before. Season titles now update from
the season fetches (EPISODE_TRANS fallback applied).

**Scan feedback**: videos without a recognizable episode marker are
persisted as `media_items` with status `skipped` (reason in the detail
payload) and exposed via `GET /api/libraries/{id}/skipped`
(path/filename/reason). Rescans replace the rows; movie libraries keep
skipping silently (movie markers are inherently ambiguous).

**Compatibility verification**: `internal/nfo/compat_test.go` parses the
generated NFO documents with encoding/xml and asserts the documented
import contract of Jellyfin/Emby/Kodi readers — required element presence
(title/year/plot/ids/uniqueid/ratings/set/premiered/season/episode/aired),
exactly one `default="true"` uniqueid, Jellyfin `collectionnumber`, Emby
`lockdata` + multi-episode `episodenumberend` (absent on Kodi/Jellyfin
singles), and Chinese content surviving the XML round-trip. Run alongside
the 12 golden files as the 1.0 NFO gate.

## D23 — v1.0 release hardening

**TMDB reliability (P0-3)**: the client now retries with bounded policy —
429 honors `Retry-After` (capped at 30 s like TMM; larger values abort
instead of blocking), 5xx and network errors back off exponentially
(1 s/2 s/4 s), max 3 attempts total, unrecoverable 4xx (401/404/400)
return immediately, and context cancellation is honored during backoff so
a canceled task never waits. Error mapping is unchanged. A 15-minute
in-memory response cache (TMM semantics, ≤512 entries) serves repeat GETs;
the cache key excludes the API key.

**Write containment (P0-4)**: artwork and NFO writers re-validate at write
time that the destination directory resolves (symlinks included) inside
the library root — a directory swapped for a symlink after a scan cannot
redirect writes out of the root (regression test swaps the dir and asserts
refusal + zero files outside). The rename executor re-checks every
destination against the plan root as defense in depth. Strict operations
(folder/video moves) still fail closed on any existing different
destination; derived copies remain idempotent by design.

**Upgrade path (P1)**: a legacy-schema database (migration 001 only with
seeded data) upgrades through 002–004 idempotently; rows, metadata_json,
settings and task history survive; new columns get safe defaults. Verified
empty-DB boot in the final container as well.

**Real-server verification (P0-2)**: scripts/release-verification holds
the fixture generator (built on internal/nfo builders) and the Jellyfin/
Emby import drivers. Results recorded there. Finding: neither Jellyfin 12
nor Emby 4.10 applies NFO `<watched>`/`<playcount>` at import time without
extra server-side user mapping (Jellyfin's xbmcmetadata UserId config moved
into its DB in 12.x; Emby gates userdata on its own settings). The tags
follow the Kodi/TMM spec verbatim and are imported natively by Kodi — no
writer change made.

**Security documentation (P0-5)**: README leads with the no-auth notice
and deployment guidance; an integration test sweeps every GET endpoint
(and the JSON 404/error surface) with a configured key asserting it never
appears in any response.

## D24 — GHCR distribution and root container

The compose file consumes `ghcr.io/<owner>/<repo>:latest` (override with
`MEDIA_MANAGER_IMAGE`), and two GitHub Actions workflows build it from the
`media-manager-lite/` subdirectory: `docker-release.yml` (v* tags → latest
+ semver, multi-arch) and `docker-dev.yml` (main/devel branch images,
path-filtered). The container image runs as **root** by default: NAS media
trees are commonly root-owned and /config ownership varies; a nonroot
default caused "unable to open database file" in real deployments. Root is
verified (DB files land as uid 0); dropping privileges remains a one-line
compose change (`user:`) and the write-containment rules (library-root
checks, no deletion, strict conflict semantics) are the actual safety
boundary, not the UID. GHCR auth in CI uses the built-in GITHUB_TOKEN with
`packages: write`; no PAT or secret configuration is required.

## D25 — Subtitle moves, NFO relocation, vanished-file sweep

**Subtitles travel with their video**: `PlanRename`/`PlanEpisodeRename`
append strict moves for every same-basename subtitle (`start.srt`,
`start.zh.ass` — language suffix preserved) into the new location, and the
executor treats them like video moves (existing different destination =
abort). Unrelated subtitles are never touched.

**NFO relocation**: the primary movie/episode NFO now MOVES with the video
instead of staying behind and being re-derived — metadata belongs next to
its media. The executor's preflight skips existence checks for NFO moves
and the move itself overwrites (temp+rename), since the content is derived
from the database. Additional NFO naming variants still copy.

**Vanished files**: a rescan deletes `media_items` rows whose video file
no longer exists on disk — **matched or unmatched** (the list reflects the
actual library contents; D25's "keep unmatched" behavior was superseded by
user decision). Matched items cascade: the movie row is deleted when no
other media item references it (multi-version movies survive). Skipped
(unparseable) videos are unaffected — they are re-evaluated by filename on
every scan.

## D26 — re-matching: the search button stays on matched items

Search is deliberately available on matched movies and TV shows (button
label "重新搜索" on matched rows): users fix wrong matches by searching,
reviewing candidates, and confirming again — both match flows (movie
ConfirmMatch / TV ConfirmShowMatch) already update an existing matched row
transactionally instead of failing, so re-matching is safe by design. The
TV list page always shows the search button; the TV detail page keeps it
outside the matched-state conditional. One nuance: a new search overwrites
the stored candidates and the modal shows the new ranking, but the current
match stays in effect until the user explicitly confirms a different
candidate (or marks unmatched).
