package database

import (
	"context"
	"time"
)

// Stats carries the dashboard counters.
type Stats struct {
	Libraries   int64 `json:"libraries"`
	Movies      int64 `json:"movies"`
	TvShows     int64 `json:"tvShows"`
	ActiveTasks int64 `json:"activeTasks"`
}

// Stats returns dashboard counts. ActiveTasks counts tasks that are pending
// or running.
func (db *DB) Stats(ctx context.Context) (Stats, error) {
	ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()

	var s Stats
	if err := db.QueryRowContext(ctx, "SELECT COUNT(*) FROM libraries").Scan(&s.Libraries); err != nil {
		return Stats{}, err
	}
	if err := db.QueryRowContext(ctx, "SELECT COUNT(*) FROM movies").Scan(&s.Movies); err != nil {
		return Stats{}, err
	}
	if err := db.QueryRowContext(ctx, "SELECT COUNT(*) FROM tv_shows").Scan(&s.TvShows); err != nil {
		return Stats{}, err
	}
	if err := db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM tasks WHERE state IN ('pending', 'running')",
	).Scan(&s.ActiveTasks); err != nil {
		return Stats{}, err
	}
	return s, nil
}
