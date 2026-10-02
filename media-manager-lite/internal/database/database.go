// Package database provides SQLite access: connection setup (WAL, foreign
// keys, busy timeout via DSN), embedded SQL migrations and small
// cross-domain helpers such as dashboard statistics.
//
// No ORM: database/sql with strictly parameterized statements. The pure-Go
// driver (modernc.org/sqlite) keeps CGO off so linux/amd64 and linux/arm64
// build identically.
package database

import (
	"context"
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"time"

	_ "modernc.org/sqlite"
)

// DB wraps the sql.DB handle used by repositories and the HTTP layer.
type DB struct {
	*sql.DB
}

// Open opens (creating if necessary) the SQLite database at path.
//
// PRAGMAs are attached to the DSN so that every pooled connection — current
// and future — runs with journal_mode=WAL, foreign_keys=ON and a 5s
// busy_timeout.
func Open(path string) (*DB, error) {
	if dir := filepath.Dir(path); dir != "" && dir != "." {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return nil, fmt.Errorf("database: create directory %s: %w", dir, err)
		}
	}

	dsn := fmt.Sprintf("file:%s?_pragma=journal_mode(WAL)&_pragma=foreign_keys(1)&_pragma=busy_timeout(5000)", path)
	handle, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("database: open %s: %w", path, err)
	}

	// A single connection sidesteps cross-connection SQLITE_BUSY contention
	// entirely (see docs/decisions.md D3). Revisit when background tasks need
	// concurrent reads.
	handle.SetMaxOpenConns(1)
	handle.SetMaxIdleConns(1)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := handle.PingContext(ctx); err != nil {
		handle.Close()
		return nil, fmt.Errorf("database: ping %s: %w", path, err)
	}
	return &DB{DB: handle}, nil
}
