// Package tv persists the TV show hierarchy: shows are keyed by
// (library, title), seasons by (show, number) and episodes by
// (show, season, episode) — all upserts are idempotent so repeated scans
// reconcile instead of duplicating.
package tv

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"media-manager-lite/internal/uid"
)

// ErrShowNotFound is returned for unknown show ids.
var ErrShowNotFound = errors.New("tv show not found")

// Store persists TV show/season/episode rows.
type Store struct {
	db *sql.DB
}

// NewStore returns a TV store on the given database handle.
func NewStore(db *sql.DB) *Store {
	return &Store{db: db}
}

// UpsertShow returns the show id for (libraryID, title), creating the row
// when missing.
func (s *Store) UpsertShow(ctx context.Context, libraryID, title string) (string, error) {
	var id string
	err := s.db.QueryRowContext(ctx,
		"SELECT id FROM tv_shows WHERE library_id = ? AND title = ?", libraryID, title,
	).Scan(&id)
	if err == nil {
		return id, nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return "", fmt.Errorf("tv store: lookup show: %w", err)
	}
	id = uid.New()
	now := time.Now().UTC().Format(time.RFC3339)
	_, err = s.db.ExecContext(ctx,
		`INSERT INTO tv_shows (id, library_id, title, created_at, updated_at) VALUES (?, ?, ?, ?, ?)`,
		id, libraryID, title, now, now)
	if err != nil {
		return "", fmt.Errorf("tv store: insert show: %w", err)
	}
	return id, nil
}

// UpsertSeason returns the season id for (show, number), creating the row
// when missing.
func (s *Store) UpsertSeason(ctx context.Context, showID string, number int) (string, error) {
	var id string
	err := s.db.QueryRowContext(ctx,
		"SELECT id FROM tv_seasons WHERE tv_show_id = ? AND number = ?", showID, number,
	).Scan(&id)
	if err == nil {
		return id, nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return "", fmt.Errorf("tv store: lookup season: %w", err)
	}
	id = uid.New()
	now := time.Now().UTC().Format(time.RFC3339)
	_, err = s.db.ExecContext(ctx,
		`INSERT INTO tv_seasons (id, tv_show_id, number, created_at, updated_at) VALUES (?, ?, ?, ?, ?)`,
		id, showID, number, now, now)
	if err != nil {
		return "", fmt.Errorf("tv store: insert season: %w", err)
	}
	return id, nil
}

// EpisodeUpsert is one discovered episode of a show.
type EpisodeUpsert struct {
	ShowID     string
	SeasonID   string // may be empty
	Season     int
	Episode    int
	Title      string
	ParsedJSON string
}

// UpsertEpisode inserts or updates an episode row keyed by
// (show, season, episode).
func (s *Store) UpsertEpisode(ctx context.Context, e EpisodeUpsert) (string, error) {
	var id string
	err := s.db.QueryRowContext(ctx,
		"SELECT id FROM tv_episodes WHERE tv_show_id = ? AND season_number = ? AND episode_number = ?",
		e.ShowID, e.Season, e.Episode,
	).Scan(&id)
	if err == nil {
		_, err = s.db.ExecContext(ctx,
			`UPDATE tv_episodes SET season_id = ?, title = ?, metadata_json = ?, updated_at = ? WHERE id = ?`,
			nullable(e.SeasonID), e.Title, e.ParsedJSON, time.Now().UTC().Format(time.RFC3339), id)
		if err != nil {
			return "", fmt.Errorf("tv store: update episode: %w", err)
		}
		return id, nil
	}
	if !errors.Is(err, sql.ErrNoRows) {
		return "", fmt.Errorf("tv store: lookup episode: %w", err)
	}
	id = uid.New()
	now := time.Now().UTC().Format(time.RFC3339)
	_, err = s.db.ExecContext(ctx,
		`INSERT INTO tv_episodes (id, tv_show_id, season_id, season_number, episode_number, title, metadata_json, created_at, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
		id, e.ShowID, nullable(e.SeasonID), e.Season, e.Episode, e.Title, e.ParsedJSON, now, now)
	if err != nil {
		return "", fmt.Errorf("tv store: insert episode: %w", err)
	}
	return id, nil
}

func nullable(s string) any {
	if s == "" {
		return nil
	}
	return s
}

// Show is one TV show row.
type Show struct {
	ID        string `json:"id"`
	LibraryID string `json:"libraryId"`
	Title     string `json:"title"`
	CreatedAt string `json:"createdAt"`
	UpdatedAt string `json:"updatedAt"`
}

// Season is one season row.
type Season struct {
	ID        string `json:"id"`
	TvShowID  string `json:"tvShowId"`
	Number    int    `json:"number"`
	Title     string `json:"title,omitempty"`
	CreatedAt string `json:"createdAt"`
}

// Episode is one episode row. TMDBID is extracted from metadata_json
// (nested "tmdb.tmdbId" written by the enrich step, or the legacy flat
// "tmdbId"); it stays 0 for episodes never scraped.
type Episode struct {
	ID            string  `json:"id"`
	TvShowID      string  `json:"tvShowId"`
	SeasonID      string  `json:"seasonId,omitempty"`
	SeasonNumber  int     `json:"seasonNumber"`
	EpisodeNumber int     `json:"episodeNumber"`
	Title         string  `json:"title,omitempty"`
	Plot          string  `json:"plot,omitempty"`
	AirDate       string  `json:"airDate,omitempty"`
	Runtime       int     `json:"runtime,omitempty"`
	Rating        float64 `json:"rating,omitempty"`
	Votes         int     `json:"votes,omitempty"`
	TMDBID        int     `json:"tmdbId,omitempty"`
	CreatedAt     string  `json:"createdAt"`

	metadataJSON string
}

// episodeTMDBID extracts the episode-level TMDB id from a metadata_json
// payload, supporting both the nested ("tmdb"."tmdbId") and the legacy flat
// ("tmdbId") layouts.
func episodeTMDBID(metadataJSON string) int {
	if metadataJSON == "" || metadataJSON == "{}" {
		return 0
	}
	var m map[string]any
	if err := json.Unmarshal([]byte(metadataJSON), &m); err != nil {
		return 0
	}
	if t, ok := m["tmdb"].(map[string]any); ok {
		if id, ok := t["tmdbId"].(float64); ok && id > 0 {
			return int(id)
		}
	}
	if id, ok := m["tmdbId"].(float64); ok && id > 0 {
		return int(id)
	}
	return 0
}

// ListShows returns the shows of a library (all libraries when empty).
func (s *Store) ListShows(ctx context.Context, libraryID string) ([]Show, error) {
	q := `SELECT id, library_id, title, created_at, updated_at FROM tv_shows`
	args := []any{}
	if libraryID != "" {
		q += " WHERE library_id = ?"
		args = append(args, libraryID)
	}
	q += " ORDER BY title, id"
	rows, err := s.db.QueryContext(ctx, q, args...)
	if err != nil {
		return nil, fmt.Errorf("tv store: list shows: %w", err)
	}
	defer rows.Close()

	out := []Show{}
	for rows.Next() {
		var sh Show
		if err := rows.Scan(&sh.ID, &sh.LibraryID, &sh.Title, &sh.CreatedAt, &sh.UpdatedAt); err != nil {
			return nil, fmt.Errorf("tv store: scan show: %w", err)
		}
		out = append(out, sh)
	}
	return out, rows.Err()
}

// GetShow returns one show.
func (s *Store) GetShow(ctx context.Context, id string) (Show, error) {
	var sh Show
	err := s.db.QueryRowContext(ctx,
		"SELECT id, library_id, title, created_at, updated_at FROM tv_shows WHERE id = ?", id,
	).Scan(&sh.ID, &sh.LibraryID, &sh.Title, &sh.CreatedAt, &sh.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return Show{}, ErrShowNotFound
	}
	if err != nil {
		return Show{}, fmt.Errorf("tv store: get show: %w", err)
	}
	return sh, nil
}

// ListSeasons returns the seasons of a show ordered by number.
func (s *Store) ListSeasons(ctx context.Context, showID string) ([]Season, error) {
	rows, err := s.db.QueryContext(ctx,
		"SELECT id, tv_show_id, number, COALESCE(title,''), created_at FROM tv_seasons WHERE tv_show_id = ? ORDER BY number", showID)
	if err != nil {
		return nil, fmt.Errorf("tv store: list seasons: %w", err)
	}
	defer rows.Close()

	out := []Season{}
	for rows.Next() {
		var se Season
		if err := rows.Scan(&se.ID, &se.TvShowID, &se.Number, &se.Title, &se.CreatedAt); err != nil {
			return nil, fmt.Errorf("tv store: scan season: %w", err)
		}
		out = append(out, se)
	}
	return out, rows.Err()
}

// ListEpisodes returns the episodes of a show ordered by season/episode.
func (s *Store) ListEpisodes(ctx context.Context, showID string) ([]Episode, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT id, tv_show_id, COALESCE(season_id,''), season_number, episode_number,
		        COALESCE(title,''), COALESCE(plot,''), COALESCE(air_date,''),
		        COALESCE(runtime,0), COALESCE(rating,0), COALESCE(votes,0),
		        COALESCE(metadata_json,'{}'), created_at
		 FROM tv_episodes WHERE tv_show_id = ? ORDER BY season_number, episode_number`, showID)
	if err != nil {
		return nil, fmt.Errorf("tv store: list episodes: %w", err)
	}
	defer rows.Close()

	out := []Episode{}
	for rows.Next() {
		var e Episode
		if err := rows.Scan(&e.ID, &e.TvShowID, &e.SeasonID, &e.SeasonNumber, &e.EpisodeNumber,
			&e.Title, &e.Plot, &e.AirDate, &e.Runtime, &e.Rating, &e.Votes,
			&e.metadataJSON, &e.CreatedAt); err != nil {
			return nil, fmt.Errorf("tv store: scan episode: %w", err)
		}
		e.TMDBID = episodeTMDBID(e.metadataJSON)
		out = append(out, e)
	}
	return out, rows.Err()
}

// EpisodeItemRef is one media item linked to a show via its episodes.
type EpisodeItemRef struct {
	ItemID    string
	EpisodeID string
	Path      string
	Filename  string
	CreatedAt string
}

// ListEpisodeItems returns the media items of a show through the
// media_items.episode_id join, ordered by path.
func (s *Store) ListEpisodeItems(ctx context.Context, showID string) ([]EpisodeItemRef, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT mi.id, mi.episode_id, mi.path, mi.filename, mi.created_at
		 FROM media_items mi
		 JOIN tv_episodes e ON mi.episode_id = e.id
		 WHERE e.tv_show_id = ?
		 ORDER BY mi.path`, showID)
	if err != nil {
		return nil, fmt.Errorf("tv store: list episode items: %w", err)
	}
	defer rows.Close()

	out := []EpisodeItemRef{}
	for rows.Next() {
		var r EpisodeItemRef
		if err := rows.Scan(&r.ItemID, &r.EpisodeID, &r.Path, &r.Filename, &r.CreatedAt); err != nil {
			return nil, fmt.Errorf("tv store: scan episode item: %w", err)
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

// EpisodeNumbersRow is the parsed episode numbering of one media item.
type EpisodeNumbersRow struct {
	Season     int
	Episodes   []int
	LocalTitle string
}

// EpisodeNumbersForItem extracts the parsed numbering of a media item from
// its linked episode's metadata_json (the ParsedEpisode payload written by
// the scanner).
func (s *Store) EpisodeNumbersForItem(ctx context.Context, itemID string) (EpisodeNumbersRow, error) {
	var season int
	var metadataJSON string
	var title string
	err := s.db.QueryRowContext(ctx,
		`SELECT e.season_number, e.metadata_json, COALESCE(e.title,'')
		 FROM media_items mi JOIN tv_episodes e ON mi.episode_id = e.id
		 WHERE mi.id = ?`, itemID,
	).Scan(&season, &metadataJSON, &title)
	if err != nil {
		return EpisodeNumbersRow{}, fmt.Errorf("tv store: episode numbers: %w", err)
	}
	var parsed struct {
		Episodes []int  `json:"Episodes"`
		Title    string `json:"Title"`
	}
	_ = json.Unmarshal([]byte(metadataJSON), &parsed)
	if len(parsed.Episodes) == 0 {
		return EpisodeNumbersRow{}, fmt.Errorf("tv store: no episode numbers for item %s", itemID)
	}
	return EpisodeNumbersRow{Season: season, Episodes: parsed.Episodes, LocalTitle: title}, nil
}

// ShowIDForItem returns the show id of the episode a media item links to.
func (s *Store) ShowIDForItem(ctx context.Context, itemID string) (string, error) {
	var showID string
	err := s.db.QueryRowContext(ctx,
		`SELECT e.tv_show_id FROM media_items mi JOIN tv_episodes e ON mi.episode_id = e.id
		 WHERE mi.id = ?`, itemID).Scan(&showID)
	if err != nil {
		return "", fmt.Errorf("tv store: show id for item: %w", err)
	}
	return showID, nil
}

// UpdateShowArtwork persists the show artwork record and flips
// artwork_status.
func (s *Store) UpdateShowArtwork(ctx context.Context, showID string, aw any) error {
	b, err := json.Marshal(aw)
	if err != nil {
		return fmt.Errorf("tv store: marshal artwork: %w", err)
	}
	_, err = s.db.ExecContext(ctx,
		`UPDATE tv_shows SET art_json = ?, artwork_status = 'downloaded', updated_at = ? WHERE id = ?`,
		string(b), time.Now().UTC().Format(time.RFC3339), showID)
	if err != nil {
		return fmt.Errorf("tv store: update artwork: %w", err)
	}
	return nil
}
