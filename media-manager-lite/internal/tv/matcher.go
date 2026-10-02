package tv

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"media-manager-lite/internal/matcher"
	"media-manager-lite/internal/tmdb"
)

// Show match states (tmdb_id IS NULL until confirmed).
var ErrNotMatched = errors.New("tv show is not matched to a TMDB show")

// ShowWithMetadata is a show row enriched with its parsed JSON payload.
type ShowWithMetadata struct {
	Show
	TMDBID        int            `json:"tmdbId"`
	IMDBID        string         `json:"imdbId,omitempty"`
	OriginalTitle string         `json:"originalTitle,omitempty"`
	Year          int            `json:"year,omitempty"`
	Plot          string         `json:"plot,omitempty"`
	AiredStatus   string         `json:"airedStatus,omitempty"`
	Rating        float64        `json:"rating,omitempty"`
	Votes         int            `json:"votes,omitempty"`
	Certification string         `json:"certification,omitempty"`
	ArtworkStatus string         `json:"artworkStatus,omitempty"`
	NfoStatus     string         `json:"nfoStatus,omitempty"`
	Metadata      map[string]any `json:"-"`
}

// GetShowFull loads a show with all persisted fields.
func (s *Store) GetShowFull(ctx context.Context, id string) (ShowWithMetadata, error) {
	var m ShowWithMetadata
	var tmdbID any
	var metadataJSON string
	err := s.db.QueryRowContext(ctx,
		`SELECT id, library_id, title, created_at, updated_at,
		        tmdb_id, COALESCE(imdb_id,''), COALESCE(original_title,''), COALESCE(year,0),
		        COALESCE(plot,''), COALESCE(status,''), COALESCE(rating,0), COALESCE(votes,0),
		        COALESCE(certification,''), COALESCE(metadata_json,'{}'),
		        COALESCE(artwork_status,'none'), COALESCE(nfo_status,'none')
		 FROM tv_shows WHERE id = ?`, id,
	).Scan(&m.ID, &m.LibraryID, &m.Title, &m.CreatedAt, &m.UpdatedAt,
		&tmdbID, &m.IMDBID, &m.OriginalTitle, &m.Year,
		&m.Plot, &m.AiredStatus, &m.Rating, &m.Votes,
		&m.Certification, &metadataJSON, &m.ArtworkStatus, &m.NfoStatus)
	if errors.Is(err, sql.ErrNoRows) {
		return ShowWithMetadata{}, ErrShowNotFound
	}
	if err != nil {
		return ShowWithMetadata{}, fmt.Errorf("tv store: get show full: %w", err)
	}
	if t, ok := tmdbID.(int64); ok {
		m.TMDBID = int(t)
	}
	_ = json.Unmarshal([]byte(metadataJSON), &m.Metadata)
	return m, nil
}

// SaveShowCandidates persists scored show candidates for the confirmation
// flow.
func (s *Store) SaveShowCandidates(ctx context.Context, showID string, results []matcher.TVShowMatchResult) error {
	b, err := json.Marshal(results)
	if err != nil {
		return fmt.Errorf("tv store: marshal candidates: %w", err)
	}
	_, err = s.db.ExecContext(ctx,
		"UPDATE tv_shows SET candidates_json = ?, updated_at = ? WHERE id = ?",
		string(b), time.Now().UTC().Format(time.RFC3339), showID)
	if err != nil {
		return fmt.Errorf("tv store: save candidates: %w", err)
	}
	return nil
}

// ShowCandidates returns the persisted show candidates (nil before the
// first search).
func (s *Store) ShowCandidates(ctx context.Context, showID string) ([]matcher.TVShowMatchResult, error) {
	var raw string
	err := s.db.QueryRowContext(ctx,
		"SELECT COALESCE(candidates_json,'') FROM tv_shows WHERE id = ?", showID,
	).Scan(&raw)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, ErrShowNotFound
	}
	if err != nil {
		return nil, fmt.Errorf("tv store: candidates: %w", err)
	}
	if raw == "" {
		return nil, nil
	}
	var out []matcher.TVShowMatchResult
	if err := json.Unmarshal([]byte(raw), &out); err != nil {
		return nil, fmt.Errorf("tv store: decode candidates: %w", err)
	}
	return out, nil
}

// ShowMetadata is the JSON payload persisted on the show after a match.
type ShowMetadata struct {
	Genres           []string `json:"genres,omitempty"`
	OriginalLanguage string   `json:"originalLanguage,omitempty"`
	EnglishTitle     string   `json:"englishTitle,omitempty"`
	TitleSource      string   `json:"titleSource,omitempty"`
	NumberOfSeasons  int      `json:"numberOfSeasons,omitempty"`
	NumberOfEpisodes int      `json:"numberOfEpisodes,omitempty"`
	Studios          []string `json:"studios,omitempty"`
	EpisodeGroups    []string `json:"episodeGroups,omitempty"`
	ScrapedAt        string   `json:"scrapedAt,omitempty"`
	ScrapedWith      string   `json:"scrapedWith,omitempty"`
}

// ConfirmShowMatch transactionally applies a confirmed TMDB show match:
// the show row is updated (tmdb_id, titles, stats, metadata) atomically.
func (s *Store) ConfirmShowMatch(ctx context.Context, showID string, res *tmdb.TVDetailResult, certCountry string) error {
	d := res.Detail
	md := ShowMetadata{
		OriginalLanguage: d.OriginalLanguage,
		EnglishTitle:     res.Best.EnglishTitle,
		TitleSource:      res.Best.Source,
		NumberOfSeasons:  d.NumberOfSeasons,
		NumberOfEpisodes: d.NumberOfEpisodes,
		ScrapedAt:        time.Now().UTC().Format(time.RFC3339),
		ScrapedWith:      res.Language,
	}
	for _, g := range d.Genres {
		md.Genres = append(md.Genres, g.Name)
	}
	for _, st := range d.ProductionCompanies {
		md.Studios = append(md.Studios, st.Name)
	}
	for _, eg := range d.EpisodeGroups.Results {
		md.EpisodeGroups = append(md.EpisodeGroups, eg.ID)
	}
	mj, err := json.Marshal(md)
	if err != nil {
		return fmt.Errorf("tv store: marshal show metadata: %w", err)
	}

	year := 0
	if len(d.FirstAirDate) >= 4 {
		fmt.Sscanf(d.FirstAirDate[:4], "%d", &year)
	}

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("tv store: begin: %w", err)
	}
	defer tx.Rollback()
	res2, err := tx.ExecContext(ctx,
		`UPDATE tv_shows SET tmdb_id = ?, imdb_id = ?, title = ?, original_title = ?,
		        year = ?, plot = ?, status = ?, rating = ?, votes = ?,
		        certification = ?, metadata_json = ?, candidates_json = '', updated_at = ?
		 WHERE id = ?`,
		d.TMDBID, d.ExternalIDs.IMDBID, res.Best.Title, d.OriginalName,
		year, res.Best.Overview, d.Status, d.VoteAverage, d.VoteCount,
		d.Certification(certCountry), string(mj), time.Now().UTC().Format(time.RFC3339), showID)
	if err != nil {
		return fmt.Errorf("tv store: confirm show: %w", err)
	}
	if n, err := res2.RowsAffected(); err == nil && n == 0 {
		return ErrShowNotFound
	}
	return tx.Commit()
}

// MarkShowUnmatched clears a confirmed match (episodes keep their local
// parsed data — the scraped fields are reset).
func (s *Store) MarkShowUnmatched(ctx context.Context, showID string) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("tv store: begin: %w", err)
	}
	defer tx.Rollback()
	now := time.Now().UTC().Format(time.RFC3339)
	if _, err := tx.ExecContext(ctx,
		`UPDATE tv_shows SET tmdb_id = NULL, imdb_id = NULL, original_title = NULL,
		        plot = NULL, status = NULL, rating = NULL, votes = NULL,
		        certification = NULL, metadata_json = '{}', candidates_json = '', updated_at = ?
		 WHERE id = ?`, now, showID); err != nil {
		return fmt.Errorf("tv store: unmatch show: %w", err)
	}
	if _, err := tx.ExecContext(ctx,
		`UPDATE tv_episodes SET title = '', plot = NULL, air_date = NULL,
		        runtime = NULL, rating = NULL, votes = NULL, metadata_json = '{}',
		        updated_at = ?
		 WHERE tv_show_id = ?`, now, showID); err != nil {
		return fmt.Errorf("tv store: unmatch episodes: %w", err)
	}
	return tx.Commit()
}

// EpisodeRow is a DB episode row for the enrich step.
type EpisodeRow struct {
	ID           string
	Season       int
	Episode      int
	MetadataJSON string
}

// EpisodeRows returns all episode rows of a show.
func (s *Store) EpisodeRows(ctx context.Context, showID string) ([]EpisodeRow, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT id, season_number, episode_number, COALESCE(metadata_json,'{}')
		 FROM tv_episodes WHERE tv_show_id = ? ORDER BY season_number, episode_number`, showID)
	if err != nil {
		return nil, fmt.Errorf("tv store: episode rows: %w", err)
	}
	defer rows.Close()

	out := []EpisodeRow{}
	for rows.Next() {
		var r EpisodeRow
		if err := rows.Scan(&r.ID, &r.Season, &r.Episode, &r.MetadataJSON); err != nil {
			return nil, fmt.Errorf("tv store: scan episode row: %w", err)
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

// EnrichEpisodeRow applies TMDB episode data to one DB row. The scanner's
// ParsedEpisode payload is preserved (nested under "tmdb" so
// EpisodeNumbersForItem keeps working); the real TMDB season/episode is
// recorded when it differs from the scanned placement (absolute groups).
func (s *Store) EnrichEpisodeRow(ctx context.Context, row EpisodeRow, e tmdb.Episode) error {
	now := time.Now().UTC().Format(time.RFC3339)
	existing := map[string]any{}
	if err := json.Unmarshal([]byte(row.MetadataJSON), &existing); err != nil {
		existing = map[string]any{}
	}
	existing["tmdb"] = map[string]any{
		"tmdbId": e.TMDBID, "overview": e.Overview, "scrapedAt": now,
	}
	if e.SeasonNumber != row.Season || e.EpisodeNumber != row.Episode {
		existing["tmdbRealSeason"] = e.SeasonNumber
		existing["tmdbRealEpisode"] = e.EpisodeNumber
	}
	md, _ := json.Marshal(existing)
	if _, err := s.db.ExecContext(ctx,
		`UPDATE tv_episodes SET title = ?, plot = ?, air_date = ?, runtime = ?,
		        rating = ?, votes = ?, metadata_json = ?, updated_at = ?
		 WHERE id = ?`,
		e.Name, e.Overview, nullable(e.AirDate), nullableInt2(e.Runtime),
		nullableFloat(e.VoteAverage), nullableInt2(e.VoteCount), string(md), now, row.ID); err != nil {
		return fmt.Errorf("tv store: enrich episode row: %w", err)
	}
	return nil
}

// UpdateSeasonTitle updates a season's title (requested language, with the
// EPISODE_TRANS fallback already applied by the service).
func (s *Store) UpdateSeasonTitle(ctx context.Context, showID string, number int, title string) error {
	if title == "" {
		return nil
	}
	_, err := s.db.ExecContext(ctx,
		`UPDATE tv_seasons SET title = ?, updated_at = ? WHERE tv_show_id = ? AND number = ?`,
		title, time.Now().UTC().Format(time.RFC3339), showID, number)
	if err != nil {
		return fmt.Errorf("tv store: update season title: %w", err)
	}
	return nil
}

func nullableInt2(n int) any {
	if n == 0 {
		return nil
	}
	return n
}

func nullableFloat(f float64) any {
	if f == 0 {
		return nil
	}
	return f
}

var _ = strings.TrimSpace
