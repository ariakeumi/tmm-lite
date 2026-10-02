package database

import (
	"context"
	"path/filepath"
	"reflect"
	"testing"
)

// openTestDB opens a migrated database in a fresh temporary directory.
func openTestDB(t *testing.T) *DB {
	t.Helper()
	db, err := Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("Open() error = %v", err)
	}
	t.Cleanup(func() { db.Close() })

	if err := Migrate(context.Background(), db.DB); err != nil {
		t.Fatalf("Migrate() error = %v", err)
	}
	return db
}

func TestOpenAppliesPragmas(t *testing.T) {
	db := openTestDB(t)
	ctx := context.Background()

	journal, err := pragmaString(ctx, db.DB, "journal_mode")
	if err != nil {
		t.Fatal(err)
	}
	if journal != "wal" {
		t.Errorf("journal_mode = %q, want wal", journal)
	}

	var fk int
	if err := db.QueryRow("PRAGMA foreign_keys").Scan(&fk); err != nil {
		t.Fatal(err)
	}
	if fk != 1 {
		t.Errorf("foreign_keys = %d, want 1", fk)
	}

	var busy int
	if err := db.QueryRow("PRAGMA busy_timeout").Scan(&busy); err != nil {
		t.Fatal(err)
	}
	if busy != 5000 {
		t.Errorf("busy_timeout = %d, want 5000", busy)
	}
}

func TestMigrateCreatesAllTables(t *testing.T) {
	db := openTestDB(t)

	want := map[string]bool{
		"libraries": false, "movies": false, "tv_shows": false,
		"tv_seasons": false, "tv_episodes": false, "media_items": false,
		"tasks": false, "settings": false, "schema_migrations": false,
	}
	rows, err := db.Query("SELECT name FROM sqlite_master WHERE type = 'table'")
	if err != nil {
		t.Fatal(err)
	}
	defer rows.Close()
	for rows.Next() {
		var name string
		if err := rows.Scan(&name); err != nil {
			t.Fatal(err)
		}
		if _, ok := want[name]; ok {
			want[name] = true
		}
	}
	if err := rows.Err(); err != nil {
		t.Fatal(err)
	}
	for table, found := range want {
		if !found {
			t.Errorf("table %q missing after migration", table)
		}
	}
}

func TestMigrateIsIdempotent(t *testing.T) {
	db := openTestDB(t)
	ctx := context.Background()

	// Second run must succeed and not duplicate rows.
	if err := Migrate(ctx, db.DB); err != nil {
		t.Fatalf("second Migrate() error = %v", err)
	}
	versions, err := AppliedVersions(ctx, db.DB)
	if err != nil {
		t.Fatal(err)
	}
	want := []string{"001_initial.sql", "002_scanner_columns.sql", "003_candidates_column.sql", "004_tv_candidates.sql"}
	if !reflect.DeepEqual(versions, want) {
		t.Fatalf("applied versions = %v, want %v", versions, want)
	}
}

func TestForeignKeysEnforced(t *testing.T) {
	db := openTestDB(t)
	ctx := context.Background()

	// Inserting a movie for a non-existent library must fail.
	_, err := db.ExecContext(ctx,
		`INSERT INTO movies (id, library_id, created_at, updated_at)
		 VALUES ('m1', 'missing-library', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')`)
	if err == nil {
		t.Fatal("insert with missing library_id should violate the foreign key")
	}
}

func TestStatsZeroAndTasksCount(t *testing.T) {
	db := openTestDB(t)
	ctx := context.Background()

	s, err := db.Stats(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if s != (Stats{}) {
		t.Fatalf("Stats() on empty db = %+v, want zero", s)
	}

	now := "2026-10-01T00:00:00Z"
	_, err = db.ExecContext(ctx,
		`INSERT INTO tasks (id, type, state, created_at) VALUES ('t1', 'scan', 'pending', ?)`, now)
	if err != nil {
		t.Fatal(err)
	}
	_, err = db.ExecContext(ctx,
		`INSERT INTO tasks (id, type, state, created_at) VALUES ('t2', 'scan', 'running', ?)`, now)
	if err != nil {
		t.Fatal(err)
	}
	_, err = db.ExecContext(ctx,
		`INSERT INTO tasks (id, type, state, created_at) VALUES ('t3', 'scan', 'completed', ?)`, now)
	if err != nil {
		t.Fatal(err)
	}

	s, err = db.Stats(ctx)
	if err != nil {
		t.Fatal(err)
	}
	if s.ActiveTasks != 2 {
		t.Errorf("ActiveTasks = %d, want 2 (pending + running)", s.ActiveTasks)
	}
}

func TestPersistenceAcrossReopen(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "persist.db")
	ctx := context.Background()

	db, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	if err := Migrate(ctx, db.DB); err != nil {
		t.Fatal(err)
	}
	_, err = db.ExecContext(ctx,
		`INSERT INTO libraries (id, name, path, type, created_at, updated_at)
		 VALUES ('l1', 'Movies', '/media/movies', 'movie', '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')`)
	if err != nil {
		t.Fatal(err)
	}
	if err := db.Close(); err != nil {
		t.Fatal(err)
	}

	// Reopen: the row and the applied migration must survive.
	db2, err := Open(path)
	if err != nil {
		t.Fatal(err)
	}
	defer db2.Close()
	if err := Migrate(ctx, db2.DB); err != nil {
		t.Fatalf("Migrate() after reopen error = %v", err)
	}
	var name, typ string
	if err := db2.QueryRowContext(ctx,
		"SELECT name, type FROM libraries WHERE id = 'l1'").Scan(&name, &typ); err != nil {
		t.Fatalf("row lost across reopen: %v", err)
	}
	if name != "Movies" || typ != "movie" {
		t.Errorf("row = (%q, %q), want (Movies, movie)", name, typ)
	}
}
