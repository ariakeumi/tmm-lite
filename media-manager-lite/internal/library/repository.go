package library

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
)

// Store is the persistence interface for libraries.
type Store interface {
	Create(ctx context.Context, lib Library) error
	Get(ctx context.Context, id string) (Library, error)
	List(ctx context.Context) ([]Library, error)
	Delete(ctx context.Context, id string) error
	ExistsByPath(ctx context.Context, path string) (bool, error)
}

// NewStore returns the SQLite-backed Store implementation.
func NewStore(db *sql.DB) Store {
	return &sqliteStore{db: db}
}

type sqliteStore struct {
	db *sql.DB
}

const libraryColumns = "id, name, path, type, created_at, updated_at"

func (s *sqliteStore) Create(ctx context.Context, lib Library) error {
	const q = `INSERT INTO libraries (id, name, path, type, created_at, updated_at)
	           VALUES (?, ?, ?, ?, ?, ?)`
	_, err := s.db.ExecContext(ctx, q,
		lib.ID, lib.Name, lib.Path, string(lib.Type), lib.CreatedAt, lib.UpdatedAt,
	)
	if err != nil {
		return fmt.Errorf("library store: create: %w", err)
	}
	return nil
}

func (s *sqliteStore) Get(ctx context.Context, id string) (Library, error) {
	const q = "SELECT " + libraryColumns + " FROM libraries WHERE id = ?"
	row := s.db.QueryRowContext(ctx, q, id)
	lib, err := scanLibrary(row)
	if errors.Is(err, sql.ErrNoRows) {
		return Library{}, ErrNotFound
	}
	return lib, err
}

func (s *sqliteStore) List(ctx context.Context) ([]Library, error) {
	const q = "SELECT " + libraryColumns + " FROM libraries ORDER BY created_at, id"
	rows, err := s.db.QueryContext(ctx, q)
	if err != nil {
		return nil, fmt.Errorf("library store: list: %w", err)
	}
	defer rows.Close()

	libs := []Library{}
	for rows.Next() {
		lib, err := scanLibrary(rows)
		if err != nil {
			return nil, err
		}
		libs = append(libs, lib)
	}
	return libs, rows.Err()
}

func (s *sqliteStore) Delete(ctx context.Context, id string) error {
	res, err := s.db.ExecContext(ctx, "DELETE FROM libraries WHERE id = ?", id)
	if err != nil {
		return fmt.Errorf("library store: delete: %w", err)
	}
	if n, err := res.RowsAffected(); err == nil && n == 0 {
		return ErrNotFound
	}
	return nil
}

func (s *sqliteStore) ExistsByPath(ctx context.Context, path string) (bool, error) {
	var exists bool
	err := s.db.QueryRowContext(ctx,
		"SELECT EXISTS(SELECT 1 FROM libraries WHERE path = ?)", path,
	).Scan(&exists)
	if err != nil {
		return false, fmt.Errorf("library store: exists by path: %w", err)
	}
	return exists, nil
}

type rowScanner interface {
	Scan(dest ...any) error
}

func scanLibrary(row rowScanner) (Library, error) {
	var lib Library
	var typ string
	if err := row.Scan(&lib.ID, &lib.Name, &lib.Path, &typ, &lib.CreatedAt, &lib.UpdatedAt); err != nil {
		return Library{}, err
	}
	lib.Type = Type(typ)
	return lib, nil
}
