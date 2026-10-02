// Package media implements the media_items persistence layer: video file
// candidates discovered by the scanner, with their parsed filename metadata.
package media

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"time"

	"media-manager-lite/internal/parser"
	"media-manager-lite/internal/uid"
)

// Media item kinds and statuses (mirrored by the schema CHECK constraints).
const (
	KindMovie   = "movie"
	KindEpisode = "episode"

	StatusNew     = "new"
	StatusSkipped = "skipped"
)

// Item is one discovered media file candidate.
type Item struct {
	ID            string         `json:"id"`
	LibraryID     string         `json:"libraryId"`
	Kind          string         `json:"kind"`
	Path          string         `json:"path"`
	Filename      string         `json:"filename"`
	Ext           string         `json:"ext"`
	Size          int64          `json:"size"`
	ModTime       string         `json:"modTime"`
	ParsedTitle   string         `json:"parsedTitle,omitempty"`
	ParsedYear    int            `json:"parsedYear,omitempty"`
	ParsedSeason  *int           `json:"parsedSeason,omitempty"`
	ParsedEpisode *int           `json:"parsedEpisode,omitempty"`
	Parsed        *parser.Parsed `json:"parsed,omitempty"`
	Status        string         `json:"status"`
	MovieID       string         `json:"movieId,omitempty"`
	EpisodeID     string         `json:"episodeId,omitempty"`
	CreatedAt     string         `json:"createdAt"`
	UpdatedAt     string         `json:"updatedAt"`

	// Detail is a free-form payload for skipped items (the skip reason);
	// regular items carry parsed data in the unexported parsedJSON.
	Detail string `json:"detail,omitempty"`

	parsedJSON string
}

// Store persists media items in SQLite.
type Store struct {
	db *sql.DB
}

func nullable(s string) any {
	if s == "" {
		return nil
	}
	return s
}

func nullableInt(n int) any {
	if n == 0 {
		return nil
	}
	return n
}

// NewStore returns a media item store on the given database handle.
func NewStore(db *sql.DB) *Store {
	return &Store{db: db}
}

// UpsertBatch inserts items, keyed by (library_id, path) inside a single
// transaction. Existing paths keep their id, status and created_at; file
// statistics and parsed metadata are refreshed. It returns how many items
// were inserted versus updated.
func (s *Store) UpsertBatch(ctx context.Context, items []Item) (inserted, updated int, err error) {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, 0, fmt.Errorf("media store: begin: %w", err)
	}
	defer tx.Rollback()

	now := time.Now().UTC().Format(time.RFC3339)
	for i := range items {
		it := &items[i]
		pj := "{}"
		if it.Detail != "" {
			pj = it.Detail
		} else if it.Parsed != nil {
			b, err := json.Marshal(it.Parsed)
			if err != nil {
				return 0, 0, fmt.Errorf("media store: marshal parsed: %w", err)
			}
			pj = string(b)
		}
		it.parsedJSON = pj

		var exists bool
		err := tx.QueryRowContext(ctx,
			"SELECT EXISTS(SELECT 1 FROM media_items WHERE library_id = ? AND path = ?)",
			it.LibraryID, it.Path,
		).Scan(&exists)
		if err != nil {
			return 0, 0, fmt.Errorf("media store: exists: %w", err)
		}

		if exists {
			const upd = `UPDATE media_items SET filename = ?, size = ?, file_ext = ?,
			                    mod_time = ?, parsed_title = ?, parsed_year = ?,
			                    parsed_season = ?, parsed_episode = ?,
			                    parsed_json = ?, episode_id = CASE WHEN ? IS NULL THEN episode_id ELSE ? END,
			                    updated_at = ?
			             WHERE library_id = ? AND path = ?`
			_, err := tx.ExecContext(ctx, upd,
				it.Filename, it.Size, it.Ext, it.ModTime,
				it.ParsedTitle, it.ParsedYear, it.ParsedSeason, it.ParsedEpisode,
				pj, nullable(it.EpisodeID), nullable(it.EpisodeID), now,
				it.LibraryID, it.Path,
			)
			if err != nil {
				return 0, 0, fmt.Errorf("media store: update: %w", err)
			}
			updated++
			continue
		}

		if it.ID == "" {
			it.ID = uid.New()
		}
		if it.Status == "" {
			it.Status = StatusNew
		}
		if it.CreatedAt == "" {
			it.CreatedAt = now
		}
		it.UpdatedAt = now
		const ins = `INSERT INTO media_items (id, library_id, kind, path, filename, size,
		                              file_ext, mod_time, parsed_title, parsed_year,
		                              parsed_season, parsed_episode, parsed_json, status,
		                              episode_id, created_at, updated_at)
		             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
		_, err = tx.ExecContext(ctx, ins,
			it.ID, it.LibraryID, it.Kind, it.Path, it.Filename, it.Size,
			it.Ext, it.ModTime, it.ParsedTitle, it.ParsedYear,
			it.ParsedSeason, it.ParsedEpisode,
			pj, it.Status, nullable(it.EpisodeID), it.CreatedAt, it.UpdatedAt,
		)
		if err != nil {
			return 0, 0, fmt.Errorf("media store: insert: %w", err)
		}
		inserted++
	}

	if err := tx.Commit(); err != nil {
		return 0, 0, fmt.Errorf("media store: commit: %w", err)
	}
	return inserted, updated, nil
}

// ListByLibrary returns all media items of a library ordered by path.
func (s *Store) ListByLibrary(ctx context.Context, libraryID string) ([]Item, error) {
	const q = `SELECT id, library_id, kind, path, filename, size, file_ext, mod_time,
	                  COALESCE(parsed_title, ''), COALESCE(parsed_year, 0),
	                  COALESCE(parsed_season, 0), COALESCE(parsed_episode, 0),
	                  parsed_json, status, COALESCE(movie_id, ''), COALESCE(episode_id, ''),
	                  created_at, updated_at
	           FROM media_items WHERE library_id = ? ORDER BY path`
	rows, err := s.db.QueryContext(ctx, q, libraryID)
	if err != nil {
		return nil, fmt.Errorf("media store: list: %w", err)
	}
	defer rows.Close()

	out := []Item{}
	for rows.Next() {
		var it Item
		var typ string
		if err := rows.Scan(&it.ID, &it.LibraryID, &typ, &it.Path, &it.Filename,
			&it.Size, &it.Ext, &it.ModTime, &it.ParsedTitle, &it.ParsedYear,
			&it.ParsedSeason, &it.ParsedEpisode,
			&it.parsedJSON, &it.Status, &it.MovieID, &it.EpisodeID, &it.CreatedAt, &it.UpdatedAt); err != nil {
			return nil, fmt.Errorf("media store: scan: %w", err)
		}
		it.Kind = typ
		if it.parsedJSON != "" && it.parsedJSON != "{}" {
			var p parser.Parsed
			if err := json.Unmarshal([]byte(it.parsedJSON), &p); err == nil && p.Title != "" {
				it.Parsed = &p
			}
		}
		out = append(out, it)
	}
	return out, rows.Err()
}

// CountByLibrary returns the number of media items in a library.
func (s *Store) CountByLibrary(ctx context.Context, libraryID string) (int, error) {
	var n int
	err := s.db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM media_items WHERE library_id = ?", libraryID,
	).Scan(&n)
	if err != nil {
		return 0, fmt.Errorf("media store: count: %w", err)
	}
	return n, nil
}
