package database

import (
	"context"
	"database/sql"
	"fmt"
	"io/fs"
	"sort"
	"strings"

	"media-manager-lite/migrations"
)

const schemaMigrations = `
CREATE TABLE IF NOT EXISTS schema_migrations (
    version    TEXT PRIMARY KEY,
    applied_at TEXT NOT NULL
)`

// Migrate applies all embedded migrations that have not been recorded in
// schema_migrations yet. Each migration runs inside a transaction (SQLite
// DDL is transactional), so a failed migration leaves the database untouched.
// Running Migrate repeatedly is a no-op once everything is applied.
func Migrate(ctx context.Context, db *sql.DB) error {
	if _, err := db.ExecContext(ctx, schemaMigrations); err != nil {
		return fmt.Errorf("migrate: create schema_migrations: %w", err)
	}

	entries, err := fs.Glob(migrations.FS, "*.sql")
	if err != nil {
		return fmt.Errorf("migrate: list embedded migrations: %w", err)
	}
	sort.Strings(entries) // "001_" < "002_" < ... ordering

	for _, name := range entries {
		applied, err := isApplied(ctx, db, name)
		if err != nil {
			return err
		}
		if applied {
			continue
		}

		body, err := fs.ReadFile(migrations.FS, name)
		if err != nil {
			return fmt.Errorf("migrate: read %s: %w", name, err)
		}

		tx, err := db.BeginTx(ctx, nil)
		if err != nil {
			return fmt.Errorf("migrate: begin %s: %w", name, err)
		}
		if _, err := tx.ExecContext(ctx, string(body)); err != nil {
			tx.Rollback()
			return fmt.Errorf("migrate: apply %s: %w", name, err)
		}
		if _, err := tx.ExecContext(ctx,
			"INSERT INTO schema_migrations (version, applied_at) VALUES (?, strftime('%Y-%m-%dT%H:%M:%fZ','now'))",
			name,
		); err != nil {
			tx.Rollback()
			return fmt.Errorf("migrate: record %s: %w", name, err)
		}
		if err := tx.Commit(); err != nil {
			return fmt.Errorf("migrate: commit %s: %w", name, err)
		}
	}
	return nil
}

func isApplied(ctx context.Context, db *sql.DB, version string) (bool, error) {
	var n int
	if err := db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM schema_migrations WHERE version = ?", version,
	).Scan(&n); err != nil {
		return false, fmt.Errorf("migrate: check %s: %w", version, err)
	}
	return n > 0, nil
}

// AppliedVersions returns the sorted list of applied migration versions.
// Used by tests and for diagnostics.
func AppliedVersions(ctx context.Context, db *sql.DB) ([]string, error) {
	rows, err := db.QueryContext(ctx, "SELECT version FROM schema_migrations ORDER BY version")
	if err != nil {
		return nil, fmt.Errorf("migrate: list applied: %w", err)
	}
	defer rows.Close()

	var out []string
	for rows.Next() {
		var v string
		if err := rows.Scan(&v); err != nil {
			return nil, fmt.Errorf("migrate: scan applied: %w", err)
		}
		out = append(out, v)
	}
	return out, rows.Err()
}

// pragmaString reads a text-valued PRAGMA, trimmed.
func pragmaString(ctx context.Context, db *sql.DB, name string) (string, error) {
	var v string
	if err := db.QueryRowContext(ctx, "PRAGMA "+name).Scan(&v); err != nil {
		return "", err
	}
	return strings.TrimSpace(v), nil
}

// DBExecutor is the subset of *sql.DB the helpers need.
type DBExecutor interface {
	ExecContext(ctx context.Context, query string, args ...any) (sql.Result, error)
}

// applyOnly applies exactly one named embedded migration (used by the
// upgrade-path test to construct a legacy-schema database).
func applyOnly(ctx context.Context, db *sql.DB, name string) error {
	if _, err := db.ExecContext(ctx, schemaMigrations); err != nil {
		return fmt.Errorf("applyOnly: tracking table: %w", err)
	}
	body, err := fs.ReadFile(migrations.FS, name)
	if err != nil {
		return fmt.Errorf("applyOnly: read %s: %w", name, err)
	}
	if _, err := db.ExecContext(ctx, string(body)); err != nil {
		return fmt.Errorf("applyOnly: exec %s: %w", name, err)
	}
	if _, err := db.ExecContext(ctx,
		"INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?)",
		name, "2026-01-01T00:00:00Z"); err != nil {
		return fmt.Errorf("applyOnly: record %s: %w", name, err)
	}
	return nil
}

// fsGlob001 is retained for diagnostics.
func fsGlob001() ([]string, error) { return fs.Glob(migrations.FS, "*.sql") }
