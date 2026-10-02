// Package settings provides tiny key/value persistence on the settings
// table for operational secrets and preferences (TMDB API key, metadata
// language). Values are read by services at request time; API keys are
// never returned through the HTTP API and never logged.
package settings

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"
)

// Known setting keys.
const (
	KeyTMDBAPIKey     = "tmdb_api_key"
	KeyMetaLang       = "metadata_language"
	KeyFallback       = "fallback_language"
	KeyCertCountry    = "certification_country"
	KeyNFOFlavor      = "nfo_flavor" // kodi | emby | jellyfin
	KeyRenameProfile  = "renamer_profile_json"
	KeyEpisodeProfile = "episode_profile_json"
)

// Store persists settings in SQLite.
type Store struct {
	db *sql.DB
}

// NewStore returns a settings store on the given database handle.
func NewStore(db *sql.DB) *Store {
	return &Store{db: db}
}

// Get returns the value for key and whether it exists.
func (s *Store) Get(ctx context.Context, key string) (string, bool, error) {
	var v string
	err := s.db.QueryRowContext(ctx, "SELECT value FROM settings WHERE key = ?", key).Scan(&v)
	if errors.Is(err, sql.ErrNoRows) {
		return "", false, nil
	}
	if err != nil {
		return "", false, fmt.Errorf("settings store: get %s: %w", key, err)
	}
	return v, true, nil
}

// Set writes (upserting) a setting value.
func (s *Store) Set(ctx context.Context, key, value string) error {
	const q = `INSERT INTO settings (key, value, updated_at) VALUES (?, ?, ?)
	           ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`
	_, err := s.db.ExecContext(ctx, q, key, value, time.Now().UTC().Format(time.RFC3339))
	if err != nil {
		return fmt.Errorf("settings store: set %s: %w", key, err)
	}
	return nil
}

// String returns the stored value or def when unset/empty.
func (s *Store) String(ctx context.Context, key, def string) (string, error) {
	v, ok, err := s.Get(ctx, key)
	if err != nil {
		return def, err
	}
	if !ok || v == "" {
		return def, nil
	}
	return v, nil
}
