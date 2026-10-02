-- Media Manager Lite — 002_scanner_columns.sql
--
-- Milestone 2: the scanner records file statistics and parsed metadata on
-- media_items. Parsed technical tags (resolution, source, codec, ...) are
-- carried in parsed_json until the matcher milestone promotes the stable
-- ones (see docs/decisions.md D11).

ALTER TABLE media_items ADD COLUMN filename    TEXT NOT NULL DEFAULT '';
ALTER TABLE media_items ADD COLUMN mod_time    TEXT NOT NULL DEFAULT '';
ALTER TABLE media_items ADD COLUMN parsed_json TEXT NOT NULL DEFAULT '{}';
