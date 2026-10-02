package database

import (
	"context"
	"path/filepath"
	"testing"
)

// TestUpgradePathFromLegacySchema simulates a v0-era database (migration
// 001 only, with data) being opened by the current binary: Migrate applies
// 002–004 idempotently and every stored row survives with its data intact.
func TestUpgradePathFromLegacySchema(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "upgrade.db")
	ctx := context.Background()

	// Stage 1: only the initial migration (an old binary's database).
	db1, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	entries, err := fsGlob001()
	if err != nil {
		t.Fatal(err)
	}
	_ = entries
	if err := applyOnly(ctx, db1.DB, "001_initial.sql"); err != nil {
		t.Fatal(err)
	}

	// Seed legacy data: a library, a media item, a movie, a task.
	now := "2026-01-01T00:00:00Z"
	mustExec(t, db1.DB,
		`INSERT INTO libraries (id, name, path, type, created_at, updated_at)
		 VALUES ('lib1', 'Movies', '/media/movies', 'movie', ?, ?)`, now, now)
	mustExec(t, db1.DB,
		`INSERT INTO movies (id, library_id, tmdb_id, title, metadata_json, created_at, updated_at)
		 VALUES ('mov1', 'lib1', 27205, 'Inception', '{"scrapedAt":"2026-01-01"}', ?, ?)`, now, now)
	mustExec(t, db1.DB,
		`INSERT INTO media_items (id, library_id, kind, path, status, movie_id, created_at, updated_at)
		 VALUES ('mi1', 'lib1', 'movie', '/media/movies/i.mkv', 'matched', 'mov1', ?, ?)`, now, now)
	mustExec(t, db1.DB,
		`INSERT INTO tasks (id, type, state, created_at) VALUES ('t1', 'scan_library', 'completed', ?)`, now)
	mustExec(t, db1.DB,
		`INSERT INTO settings (key, value, updated_at) VALUES ('tmdb_api_key', 'legacy-key', ?)`, now)
	if err := db1.Close(); err != nil {
		t.Fatal(err)
	}

	// Stage 2: the current binary opens the same file and migrates.
	db2, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	defer db2.Close()
	if err := Migrate(ctx, db2.DB); err != nil {
		t.Fatalf("upgrade migrate: %v", err)
	}

	versions, err := AppliedVersions(ctx, db2.DB)
	if err != nil {
		t.Fatal(err)
	}
	if len(versions) != 4 {
		t.Errorf("applied versions = %v, want 4", versions)
	}

	// Second run: idempotent.
	if err := Migrate(ctx, db2.DB); err != nil {
		t.Fatalf("second migrate: %v", err)
	}

	// Data preserved and new columns usable.
	var title, metaJSON, artJSON, artStatus, nfoStatus string
	if err := db2.QueryRowContext(ctx,
		`SELECT title, metadata_json, art_json, artwork_status, nfo_status
		 FROM movies WHERE id = 'mov1'`).Scan(&title, &metaJSON, &artJSON, &artStatus, &nfoStatus); err != nil {
		t.Fatalf("movie row lost: %v", err)
	}
	if title != "Inception" {
		t.Errorf("title = %q", title)
	}
	if metaJSON != `{"scrapedAt":"2026-01-01"}` {
		t.Errorf("metadata_json = %q (must be preserved)", metaJSON)
	}
	if artStatus != "none" || nfoStatus != "none" {
		t.Errorf("new-column defaults = %q/%q, want none/none", artStatus, nfoStatus)
	}

	var status, movieID string
	if err := db2.QueryRowContext(ctx,
		`SELECT status, COALESCE(movie_id,'') FROM media_items WHERE id = 'mi1'`).
		Scan(&status, &movieID); err != nil {
		t.Fatalf("media item lost: %v", err)
	}
	if status != "matched" || movieID != "mov1" {
		t.Errorf("media item = %s/%s", status, movieID)
	}

	var keyValue string
	if err := db2.QueryRowContext(ctx,
		`SELECT value FROM settings WHERE key = 'tmdb_api_key'`).Scan(&keyValue); err != nil {
		t.Fatalf("settings lost: %v", err)
	}
	if keyValue != "legacy-key" {
		t.Errorf("settings value = %q", keyValue)
	}

	var taskState string
	if err := db2.QueryRowContext(ctx,
		`SELECT state FROM tasks WHERE id = 't1'`).Scan(&taskState); err != nil {
		t.Fatalf("task lost: %v", err)
	}
	if taskState != "completed" {
		t.Errorf("task state = %q", taskState)
	}
}

func mustExec(t *testing.T, db DBExecutor, q string, args ...any) {
	t.Helper()
	if _, err := db.ExecContext(context.Background(), q, args...); err != nil {
		t.Fatalf("seed %q: %v", q, err)
	}
}
