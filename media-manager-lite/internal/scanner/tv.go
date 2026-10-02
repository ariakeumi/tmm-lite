package scanner

import (
	"context"
	"encoding/json"
	"fmt"
	"path/filepath"
	"strings"
	"time"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/parser"
	"media-manager-lite/internal/paths"
	"media-manager-lite/internal/tv"
)

// ScanTVLibrary walks a TV library, parses every discovered filename as an
// episode and upserts the show/season/episode hierarchy plus media_items.
// Files without a recognizable episode marker are counted as skipped. The
// filesystem is only read.
func (s *Service) ScanTVLibrary(ctx context.Context, lib library.Library) (Stats, error) {
	root, err := paths.CanonicalizeDir(lib.Path)
	if err != nil {
		return Stats{}, fmt.Errorf("scan library %q: %w", lib.Name, err)
	}
	cands, err := Walk(root)
	if err != nil {
		return Stats{}, fmt.Errorf("scan library %q: %w", lib.Name, err)
	}

	stats := Stats{Found: len(cands)}
	for _, c := range cands {
		parsed := parser.ParseEpisode(c.Name)
		if !parsed.Detected() {
			// Persist the unidentified video so the scan feedback API can
			// list it (status "skipped"; a rescan replaces the row).
			stats.Skipped++
			reason := "filename does not contain a recognizable episode marker"
			if pj, err := json.Marshal(map[string]string{"reason": reason}); err == nil {
				_, _, _ = s.media.UpsertBatch(ctx, []media.Item{{
					LibraryID: lib.ID,
					Kind:      media.KindEpisode,
					Path:      c.Path,
					Filename:  c.Name,
					Ext:       strings.ToLower(filepath.Ext(c.Name)),
					Size:      c.Size,
					ModTime:   c.ModTime.UTC().Format(time.RFC3339),
					Detail:    string(pj),
					Status:    media.StatusSkipped,
				}})
			}
			continue
		}

		showTitle := parser.EpisodeShowTitle(root, c.Path, c.Name)
		if showTitle == "" {
			showTitle = "Unknown Show"
		}
		showID, err := s.tv.UpsertShow(ctx, lib.ID, showTitle)
		if err != nil {
			return stats, fmt.Errorf("scan library %q: %w", lib.Name, err)
		}
		seasonID, err := s.tv.UpsertSeason(ctx, showID, parsed.Season)
		if err != nil {
			return stats, fmt.Errorf("scan library %q: %w", lib.Name, err)
		}

		parsedJSON, err := json.Marshal(parsed)
		if err != nil {
			return stats, fmt.Errorf("scan library %q: %w", lib.Name, err)
		}

		// Multi-episode files create one episode row per number; the media
		// item links to the first one (the full list lives in parsed_json).
		firstEpisodeID := ""
		for _, ep := range parsed.Episodes {
			epID, err := s.tv.UpsertEpisode(ctx, tv.EpisodeUpsert{
				ShowID:     showID,
				SeasonID:   seasonID,
				Season:     parsed.Season,
				Episode:    ep,
				Title:      parsed.Title,
				ParsedJSON: string(parsedJSON),
			})
			if err != nil {
				return stats, fmt.Errorf("scan library %q: %w", lib.Name, err)
			}
			if firstEpisodeID == "" {
				firstEpisodeID = epID
			}
			stats.Episodes++
		}

		season := parsed.Season
		episode := parsed.Episodes[0]
		items := []media.Item{{
			LibraryID:     lib.ID,
			Kind:          media.KindEpisode,
			Path:          c.Path,
			Filename:      c.Name,
			Ext:           strings.ToLower(filepath.Ext(c.Name)),
			Size:          c.Size,
			ModTime:       c.ModTime.UTC().Format(time.RFC3339),
			ParsedTitle:   parsed.Title,
			ParsedSeason:  &season,
			ParsedEpisode: &episode,
			EpisodeID:     firstEpisodeID,
			Status:        media.StatusNew,
		}}
		inserted, updated, err := s.media.UpsertBatch(ctx, items)
		if err != nil {
			return stats, fmt.Errorf("scan library %q: %w", lib.Name, err)
		}
		stats.New += inserted
		stats.Updated += updated
	}
	return stats, nil
}
