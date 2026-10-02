package media

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"time"

	"media-manager-lite/internal/matcher"
	"media-manager-lite/internal/parser"
)

// ErrUnknown is returned when a media item id does not exist.
var ErrUnknown = errors.New("media item not found")

// GetItem returns one media item by id.
func (s *Store) GetItem(ctx context.Context, id string) (Item, error) {
	const q = `SELECT id, library_id, kind, path, filename, size, file_ext, mod_time,
	                  COALESCE(parsed_title, ''), COALESCE(parsed_year, 0),
	                  COALESCE(parsed_season, 0), COALESCE(parsed_episode, 0),
	                  parsed_json, status, COALESCE(movie_id, ''), COALESCE(episode_id, ''),
	                  created_at, updated_at
	           FROM media_items WHERE id = ?`
	var it Item
	var typ string
	err := s.db.QueryRowContext(ctx, q, id).Scan(
		&it.ID, &it.LibraryID, &typ, &it.Path, &it.Filename, &it.Size, &it.Ext,
		&it.ModTime, &it.ParsedTitle, &it.ParsedYear, &it.ParsedSeason, &it.ParsedEpisode,
		&it.parsedJSON, &it.Status, &it.MovieID, &it.EpisodeID, &it.CreatedAt, &it.UpdatedAt,
	)
	if errors.Is(err, sql.ErrNoRows) {
		return Item{}, ErrUnknown
	}
	if err != nil {
		return Item{}, fmt.Errorf("media store: get item: %w", err)
	}
	it.Kind = typ
	if it.parsedJSON != "" && it.parsedJSON != "{}" {
		var p parser.Parsed
		if err := json.Unmarshal([]byte(it.parsedJSON), &p); err == nil && p.Title != "" {
			it.Parsed = &p
		}
	}
	return it, nil
}

// SaveCandidates persists scored match candidates for a media item.
func (s *Store) SaveCandidates(ctx context.Context, itemID string, results []matcher.MatchResult) error {
	b, err := json.Marshal(results)
	if err != nil {
		return fmt.Errorf("media store: marshal candidates: %w", err)
	}
	if _, err := s.db.ExecContext(ctx,
		"UPDATE media_items SET candidates_json = ?, updated_at = datetime('now') WHERE id = ?",
		string(b), itemID,
	); err != nil {
		return fmt.Errorf("media store: save candidates: %w", err)
	}
	return nil
}

// Candidates returns the persisted match candidates of a media item
// (nil when a search has not run yet).
func (s *Store) Candidates(ctx context.Context, itemID string) ([]matcher.MatchResult, error) {
	var raw string
	err := s.db.QueryRowContext(ctx,
		"SELECT COALESCE(candidates_json, '') FROM media_items WHERE id = ?", itemID,
	).Scan(&raw)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrUnknown
	}
	if err != nil {
		return nil, fmt.Errorf("media store: candidates: %w", err)
	}
	if raw == "" {
		return nil, nil
	}
	var out []matcher.MatchResult
	if err := json.Unmarshal([]byte(raw), &out); err != nil {
		return nil, fmt.Errorf("media store: decode candidates: %w", err)
	}
	return out, nil
}

// UpdatePath moves a media item's recorded path (after a rename) and
// refreshes its filename to the new basename.
func (s *Store) UpdatePath(ctx context.Context, itemID, newPath string) error {
	name := filepath.Base(newPath)
	_, err := s.db.ExecContext(ctx,
		"UPDATE media_items SET path = ?, filename = ?, updated_at = ? WHERE id = ?",
		newPath, name, time.Now().UTC().Format(time.RFC3339), itemID)
	if err != nil {
		return fmt.Errorf("media store: update path: %w", err)
	}
	return nil
}

// ListSkippedByLibrary returns the skipped media items of a library with
// their skip reasons (from the free-form detail payload).
func (s *Store) ListSkippedByLibrary(ctx context.Context, libraryID string) ([]SkippedItem, error) {
	const q = `SELECT path, filename, COALESCE(parsed_json,'{}')
	           FROM media_items WHERE library_id = ? AND status = 'skipped' ORDER BY path`
	rows, err := s.db.QueryContext(ctx, q, libraryID)
	if err != nil {
		return nil, fmt.Errorf("media store: list skipped: %w", err)
	}
	defer rows.Close()

	out := []SkippedItem{}
	for rows.Next() {
		var it SkippedItem
		var raw string
		if err := rows.Scan(&it.Path, &it.Filename, &raw); err != nil {
			return nil, fmt.Errorf("media store: scan skipped: %w", err)
		}
		var detail struct {
			Reason string `json:"reason"`
		}
		if err := json.Unmarshal([]byte(raw), &detail); err == nil {
			it.Reason = detail.Reason
		}
		if it.Reason == "" {
			it.Reason = "unrecognized file"
		}
		out = append(out, it)
	}
	return out, rows.Err()
}

// SkippedItem is one video the scanner could not identify.
type SkippedItem struct {
	Path     string `json:"path"`
	Filename string `json:"filename"`
	Reason   string `json:"reason"`
}
