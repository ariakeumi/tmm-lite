-- Media Manager Lite — 001_initial.sql
--
-- Milestone 1 schema: minimal, forward-compatible. Metadata payloads are kept
-- in *_json columns (later milestones decide whether to promote columns).
-- IDs are application-generated UUIDv4 strings; timestamps are RFC3339 UTC
-- TEXT (see docs/decisions.md D4/D5).
--
-- Deleting a library cascades to its media items and movie/tv records —
-- filesystem data is never touched by the application in Milestone 1.

CREATE TABLE libraries (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL CHECK (length(trim(name)) > 0),
    path       TEXT NOT NULL,
    type       TEXT NOT NULL CHECK (type IN ('movie', 'tv')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE UNIQUE INDEX idx_libraries_path ON libraries (path);

CREATE TABLE movies (
    id             TEXT PRIMARY KEY,
    library_id     TEXT NOT NULL REFERENCES libraries (id) ON DELETE CASCADE,
    tmdb_id        INTEGER,
    imdb_id        TEXT,
    title          TEXT NOT NULL DEFAULT '',
    original_title TEXT,
    year           INTEGER,
    release_date   TEXT,
    runtime        INTEGER,
    plot           TEXT,
    tagline        TEXT,
    rating         REAL,
    votes          INTEGER,
    certification  TEXT,
    metadata_json  TEXT NOT NULL DEFAULT '{}',
    art_json       TEXT NOT NULL DEFAULT '{}',
    nfo_status     TEXT NOT NULL DEFAULT 'none',
    artwork_status TEXT NOT NULL DEFAULT 'none',
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

CREATE INDEX idx_movies_library ON movies (library_id);
CREATE INDEX idx_movies_tmdb ON movies (tmdb_id);

CREATE TABLE tv_shows (
    id             TEXT PRIMARY KEY,
    library_id     TEXT NOT NULL REFERENCES libraries (id) ON DELETE CASCADE,
    tmdb_id        INTEGER,
    imdb_id        TEXT,
    title          TEXT NOT NULL DEFAULT '',
    original_title TEXT,
    year           INTEGER,
    plot           TEXT,
    status         TEXT,
    rating         REAL,
    votes          INTEGER,
    certification  TEXT,
    metadata_json  TEXT NOT NULL DEFAULT '{}',
    art_json       TEXT NOT NULL DEFAULT '{}',
    nfo_status     TEXT NOT NULL DEFAULT 'none',
    artwork_status TEXT NOT NULL DEFAULT 'none',
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

CREATE INDEX idx_tv_shows_library ON tv_shows (library_id);

CREATE TABLE tv_seasons (
    id            TEXT PRIMARY KEY,
    tv_show_id    TEXT NOT NULL REFERENCES tv_shows (id) ON DELETE CASCADE,
    number        INTEGER NOT NULL CHECK (number >= 0),
    title         TEXT,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL
);

CREATE UNIQUE INDEX idx_tv_seasons_show_number ON tv_seasons (tv_show_id, number);

CREATE TABLE tv_episodes (
    id             TEXT PRIMARY KEY,
    tv_show_id     TEXT NOT NULL REFERENCES tv_shows (id) ON DELETE CASCADE,
    season_id      TEXT REFERENCES tv_seasons (id) ON DELETE SET NULL,
    season_number  INTEGER NOT NULL CHECK (season_number >= 0),
    episode_number INTEGER NOT NULL CHECK (episode_number >= 1),
    title          TEXT,
    plot           TEXT,
    air_date       TEXT,
    runtime        INTEGER,
    rating         REAL,
    votes          INTEGER,
    metadata_json  TEXT NOT NULL DEFAULT '{}',
    nfo_status     TEXT NOT NULL DEFAULT 'none',
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

CREATE UNIQUE INDEX idx_tv_episodes_show_se_ep
    ON tv_episodes (tv_show_id, season_number, episode_number);

CREATE TABLE media_items (
    id             TEXT PRIMARY KEY,
    library_id     TEXT NOT NULL REFERENCES libraries (id) ON DELETE CASCADE,
    kind           TEXT NOT NULL CHECK (kind IN ('movie', 'episode')),
    path           TEXT NOT NULL,
    size           INTEGER NOT NULL DEFAULT 0,
    file_ext       TEXT NOT NULL DEFAULT '',
    parsed_title   TEXT,
    parsed_year    INTEGER,
    parsed_season  INTEGER,
    parsed_episode INTEGER,
    parsed_date    TEXT,
    status         TEXT NOT NULL DEFAULT 'new'
                   CHECK (status IN ('new', 'matched', 'unmatched', 'skipped')),
    movie_id       TEXT REFERENCES movies (id) ON DELETE SET NULL,
    episode_id     TEXT REFERENCES tv_episodes (id) ON DELETE SET NULL,
    created_at     TEXT NOT NULL,
    updated_at     TEXT NOT NULL
);

CREATE UNIQUE INDEX idx_media_items_library_path ON media_items (library_id, path);
CREATE INDEX idx_media_items_status ON media_items (status);

CREATE TABLE tasks (
    id          TEXT PRIMARY KEY,
    type        TEXT NOT NULL,
    target_type TEXT,
    target_id   TEXT,
    state       TEXT NOT NULL DEFAULT 'pending'
                CHECK (state IN ('pending', 'running', 'completed', 'failed')),
    progress    REAL NOT NULL DEFAULT 0 CHECK (progress >= 0 AND progress <= 1),
    detail      TEXT,
    error       TEXT,
    created_at  TEXT NOT NULL,
    started_at  TEXT,
    finished_at TEXT
);

CREATE INDEX idx_tasks_state_created ON tasks (state, created_at);

CREATE TABLE settings (
    key        TEXT PRIMARY KEY,
    value      TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
