-- Media Manager Lite — 003_candidates_column.sql
--
-- Milestone 3: scored TMDB search candidates are cached on the media item
-- between the search and the explicit user confirmation.

ALTER TABLE media_items ADD COLUMN candidates_json TEXT NOT NULL DEFAULT '';
