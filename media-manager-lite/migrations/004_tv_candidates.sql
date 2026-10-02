-- Media Manager Lite — 004_tv_candidates.sql
--
-- Milestone 7: scored TMDB show candidates are cached on the TV show
-- between search and explicit user confirmation (same pattern as
-- media_items.candidates_json for movies).

ALTER TABLE tv_shows ADD COLUMN candidates_json TEXT NOT NULL DEFAULT '';
