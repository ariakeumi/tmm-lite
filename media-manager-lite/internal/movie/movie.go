// Package movie persists matched movie metadata and owns the transactional
// match-confirmation flow: upserting the movies row and flipping the media
// item to matched (or unmatched) happens atomically.
package movie

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"media-manager-lite/internal/tmdb"
	"media-manager-lite/internal/uid"
)

// Media item statuses involved in matching (mirrors the schema CHECK).
const (
	StatusMatched   = "matched"
	StatusUnmatched = "unmatched"
)

// ErrItemNotFound is returned when a media item id does not exist.
var ErrItemNotFound = errors.New("media item not found")

// Metadata carries everything that has no dedicated column yet (Phase 0:
// metadata as JSON until stable).
type Metadata struct {
	Genres           []string    `json:"genres,omitempty"`
	Cast             []CastEntry `json:"cast,omitempty"`
	Directors        []string    `json:"directors,omitempty"`
	Writers          []string    `json:"writers,omitempty"`
	Studios          []string    `json:"studios,omitempty"`
	OriginalLanguage string      `json:"originalLanguage,omitempty"`
	EnglishTitle     string      `json:"englishTitle,omitempty"`
	TitleSource      string      `json:"titleSource,omitempty"`
	IMDBID           string      `json:"imdbId,omitempty"`
	TMDBStatus       string      `json:"tmdbStatus,omitempty"`
	SetName          string      `json:"setName,omitempty"`
	SetTMDBID        int         `json:"setTmdbId,omitempty"`
	ScrapedAt        string      `json:"scrapedAt,omitempty"`
	ScrapedWith      string      `json:"scrapedWith,omitempty"`
}

// Artwork records the chosen TMDB artwork sources and the files written to
// the media directory (persisted in the movies.art_json column).
type Artwork struct {
	PosterURL    string `json:"posterUrl,omitempty"`
	FanartURL    string `json:"fanartUrl,omitempty"`
	PosterFile   string `json:"posterFile,omitempty"`
	FanartFile   string `json:"fanartFile,omitempty"`
	DownloadedAt string `json:"downloadedAt,omitempty"`
}

// CastEntry is one credited person.
type CastEntry struct {
	TMDBID      int    `json:"tmdbId,omitempty"`
	Name        string `json:"name"`
	Character   string `json:"character,omitempty"`
	ProfilePath string `json:"profilePath,omitempty"`
}

// Movie is a matched movie record.
type Movie struct {
	ID            string    `json:"id"`
	LibraryID     string    `json:"libraryId"`
	TMDBID        int       `json:"tmdbId"`
	IMDBID        string    `json:"imdbId,omitempty"`
	Title         string    `json:"title"`
	OriginalTitle string    `json:"originalTitle,omitempty"`
	Year          int       `json:"year,omitempty"`
	ReleaseDate   string    `json:"releaseDate,omitempty"`
	Runtime       int       `json:"runtime,omitempty"`
	Plot          string    `json:"plot,omitempty"`
	Tagline       string    `json:"tagline,omitempty"`
	Rating        float64   `json:"rating,omitempty"`
	Votes         int       `json:"votes,omitempty"`
	Certification string    `json:"certification,omitempty"`
	PosterURL     string    `json:"posterUrl,omitempty"`
	ArtworkStatus string    `json:"artworkStatus,omitempty"`
	NfoStatus     string    `json:"nfoStatus,omitempty"`
	Artwork       *Artwork  `json:"artwork,omitempty"`
	Metadata      *Metadata `json:"metadata,omitempty"`
	CreatedAt     string    `json:"createdAt"`
	UpdatedAt     string    `json:"updatedAt"`

	metadataJSON string
	artJSON      string
}

// FromTMDB maps a TMDB detail (with fallback-applied title) into a Movie row.
// certCountry selects the certification (theatrical release types preferred).
func FromTMDB(libraryID string, res *tmdb.MovieDetailResult, certCountry string) Movie {
	d := res.Detail
	votes := d.VoteCount
	m := Metadata{
		OriginalLanguage: d.OriginalLanguage,
		EnglishTitle:     res.Best.EnglishTitle,
		TitleSource:      res.Best.Source,
		TMDBStatus:       d.Status,
		ScrapedAt:        time.Now().UTC().Format(time.RFC3339),
		ScrapedWith:      res.Language,
		IMDBID:           d.ExternalIDs.IMDBID,
	}
	if d.BelongsToCollection != nil {
		m.SetName = d.BelongsToCollection.Name
		m.SetTMDBID = d.BelongsToCollection.ID
	}
	for _, g := range d.Genres {
		m.Genres = append(m.Genres, g.Name)
	}
	for _, c := range d.Credits.Cast {
		m.Cast = append(m.Cast, CastEntry{TMDBID: c.TMDBID, Name: c.Name, Character: c.Character, ProfilePath: c.ProfilePath})
	}
	for _, c := range d.Credits.Crew {
		switch {
		case strings.EqualFold(c.Job, "Director"):
			m.Directors = append(m.Directors, c.Name)
		case strings.EqualFold(c.Department, "Writing"):
			m.Writers = append(m.Writers, c.Name)
		}
	}
	seenStudio := map[string]bool{}
	for _, c := range d.ProductionCompanies {
		if !seenStudio[c.Name] {
			seenStudio[c.Name] = true
			m.Studios = append(m.Studios, c.Name)
		}
	}

	year := 0
	if len(d.ReleaseDate) >= 4 {
		fmt.Sscanf(d.ReleaseDate[:4], "%d", &year)
	}
	return Movie{
		LibraryID:     libraryID,
		TMDBID:        d.TMDBID,
		IMDBID:        d.ExternalIDs.IMDBID,
		Title:         res.Best.Title,
		OriginalTitle: d.OriginalTitle,
		Year:          year,
		ReleaseDate:   d.ReleaseDate,
		Runtime:       d.Runtime,
		Plot:          res.Best.Overview,
		Tagline:       res.Best.Tagline,
		Rating:        d.VoteAverage,
		Votes:         votes,
		Certification: d.Certification(certCountry),
		PosterURL:     res.PosterURL,
		Metadata:      &m,
	}
}

// Store persists movies.
type Store struct {
	db *sql.DB
}

// NewStore returns a movie store on the given database handle.
func NewStore(db *sql.DB) *Store {
	return &Store{db: db}
}

// ConfirmMatch transactionally upserts the movie record (keyed by library
// and TMDB id) and links the media item to it with status "matched".
func (s *Store) ConfirmMatch(ctx context.Context, mediaItemID string, m Movie) (Movie, error) {
	now := time.Now().UTC().Format(time.RFC3339)
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return Movie{}, fmt.Errorf("movie store: begin: %w", err)
	}
	defer tx.Rollback()

	var mj []byte
	if m.Metadata != nil {
		mj, err = json.Marshal(m.Metadata)
		if err != nil {
			return Movie{}, fmt.Errorf("movie store: marshal metadata: %w", err)
		}
	} else {
		mj = []byte("{}")
	}

	// Upsert the movie keyed by (library_id, tmdb_id).
	var id string
	err = tx.QueryRowContext(ctx,
		"SELECT id FROM movies WHERE library_id = ? AND tmdb_id = ?", m.LibraryID, m.TMDBID,
	).Scan(&id)
	switch {
	case errors.Is(err, sql.ErrNoRows):
		id = uid.New()
		m.ID, m.CreatedAt, m.UpdatedAt = id, now, now
		const ins = `INSERT INTO movies (id, library_id, tmdb_id, imdb_id, title, original_title,
		                              year, release_date, runtime, plot, tagline, rating, votes,
		                              certification, metadata_json, art_json, created_at, updated_at)
		             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '{}', ?, ?)`
		if _, err := tx.ExecContext(ctx, ins,
			m.ID, m.LibraryID, m.TMDBID, m.IMDBID, m.Title, m.OriginalTitle,
			m.Year, m.ReleaseDate, m.Runtime, m.Plot, m.Tagline, m.Rating, m.Votes,
			m.Certification, string(mj), m.CreatedAt, m.UpdatedAt,
		); err != nil {
			return Movie{}, fmt.Errorf("movie store: insert: %w", err)
		}
	case err != nil:
		return Movie{}, fmt.Errorf("movie store: lookup: %w", err)
	default:
		m.ID, m.CreatedAt, m.UpdatedAt = id, "", now
		const upd = `UPDATE movies SET imdb_id = ?, title = ?, original_title = ?, year = ?,
		                            release_date = ?, runtime = ?, plot = ?, tagline = ?,
		                            rating = ?, votes = ?, certification = ?,
		                            metadata_json = ?, updated_at = ?
		                     WHERE id = ?`
		if _, err := tx.ExecContext(ctx, upd,
			m.IMDBID, m.Title, m.OriginalTitle, m.Year, m.ReleaseDate, m.Runtime,
			m.Plot, m.Tagline, m.Rating, m.Votes, m.Certification, string(mj), now, id,
		); err != nil {
			return Movie{}, fmt.Errorf("movie store: update: %w", err)
		}
	}

	// Link the media item.
	res, err := tx.ExecContext(ctx,
		`UPDATE media_items SET movie_id = ?, status = 'matched', updated_at = ? WHERE id = ?`,
		m.ID, now, mediaItemID,
	)
	if err != nil {
		return Movie{}, fmt.Errorf("movie store: link media item: %w", err)
	}
	if n, err := res.RowsAffected(); err == nil && n == 0 {
		return Movie{}, ErrItemNotFound
	}
	if err := tx.Commit(); err != nil {
		return Movie{}, fmt.Errorf("movie store: commit: %w", err)
	}
	return m, nil
}

// MarkUnmatched flips a media item to "unmatched" (clearing any link).
func (s *Store) MarkUnmatched(ctx context.Context, mediaItemID string) error {
	now := time.Now().UTC().Format(time.RFC3339)
	res, err := s.db.ExecContext(ctx,
		`UPDATE media_items SET movie_id = NULL, status = 'unmatched', updated_at = ? WHERE id = ?`,
		now, mediaItemID,
	)
	if err != nil {
		return fmt.Errorf("movie store: mark unmatched: %w", err)
	}
	if n, err := res.RowsAffected(); err == nil && n == 0 {
		return ErrItemNotFound
	}
	return nil
}

// ListByLibrary returns matched movies of a library.
func (s *Store) ListByLibrary(ctx context.Context, libraryID string) ([]Movie, error) {
	const q = `SELECT id, library_id, COALESCE(tmdb_id, 0), COALESCE(imdb_id, ''), title,
	                  COALESCE(original_title, ''), COALESCE(year, 0), COALESCE(release_date, ''),
	                  COALESCE(runtime, 0), COALESCE(plot, ''), COALESCE(tagline, ''),
	                  COALESCE(rating, 0), COALESCE(votes, 0), COALESCE(certification, ''),
	                  COALESCE(metadata_json, '{}'), COALESCE(art_json, '{}'),
	                  COALESCE(artwork_status, 'none'), COALESCE(nfo_status, 'none'),
	                  created_at, updated_at
	           FROM movies WHERE library_id = ? ORDER BY title, id`
	rows, err := s.db.QueryContext(ctx, q, libraryID)
	if err != nil {
		return nil, fmt.Errorf("movie store: list: %w", err)
	}
	defer rows.Close()

	out := []Movie{}
	for rows.Next() {
		var m Movie
		if err := rows.Scan(&m.ID, &m.LibraryID, &m.TMDBID, &m.IMDBID, &m.Title,
			&m.OriginalTitle, &m.Year, &m.ReleaseDate, &m.Runtime, &m.Plot, &m.Tagline,
			&m.Rating, &m.Votes, &m.Certification, &m.metadataJSON, &m.artJSON,
			&m.ArtworkStatus, &m.NfoStatus, &m.CreatedAt, &m.UpdatedAt); err != nil {
			return nil, fmt.Errorf("movie store: scan: %w", err)
		}
		if m.metadataJSON != "" && m.metadataJSON != "{}" {
			var md Metadata
			if err := json.Unmarshal([]byte(m.metadataJSON), &md); err == nil {
				m.Metadata = &md
			}
		}
		out = append(out, m)
	}
	return out, rows.Err()
}

// GetByID returns one movie by its id.
func (s *Store) GetByID(ctx context.Context, id string) (Movie, error) {
	const q = `SELECT id, library_id, COALESCE(tmdb_id, 0), COALESCE(imdb_id, ''), title,
	                  COALESCE(original_title, ''), COALESCE(year, 0), COALESCE(release_date, ''),
	                  COALESCE(runtime, 0), COALESCE(plot, ''), COALESCE(tagline, ''),
	                  COALESCE(rating, 0), COALESCE(votes, 0), COALESCE(certification, ''),
	                  COALESCE(metadata_json, '{}'), COALESCE(art_json, '{}'),
	                  COALESCE(artwork_status, 'none'), COALESCE(nfo_status, 'none'),
	                  created_at, updated_at
	           FROM movies WHERE id = ?`
	var m Movie
	err := s.db.QueryRowContext(ctx, q, id).Scan(
		&m.ID, &m.LibraryID, &m.TMDBID, &m.IMDBID, &m.Title, &m.OriginalTitle,
		&m.Year, &m.ReleaseDate, &m.Runtime, &m.Plot, &m.Tagline, &m.Rating,
		&m.Votes, &m.Certification, &m.metadataJSON, &m.artJSON,
		&m.ArtworkStatus, &m.NfoStatus, &m.CreatedAt, &m.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return Movie{}, fmt.Errorf("movie store: %w", sql.ErrNoRows)
	}
	if err != nil {
		return Movie{}, fmt.Errorf("movie store: get: %w", err)
	}
	if m.metadataJSON != "" && m.metadataJSON != "{}" {
		var md Metadata
		if err := json.Unmarshal([]byte(m.metadataJSON), &md); err == nil {
			m.Metadata = &md
		}
	}
	if m.artJSON != "" && m.artJSON != "{}" {
		var aw Artwork
		if err := json.Unmarshal([]byte(m.artJSON), &aw); err == nil && (aw.PosterURL != "" || aw.PosterFile != "") {
			m.Artwork = &aw
		}
	}
	return m, nil
}

// CountByLibrary returns the number of matched movies in a library.
func (s *Store) CountByLibrary(ctx context.Context, libraryID string) (int, error) {
	var n int
	err := s.db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM movies WHERE library_id = ?", libraryID).Scan(&n)
	if err != nil {
		return 0, fmt.Errorf("movie store: count: %w", err)
	}
	return n, nil
}

// UpdateArtwork persists the artwork record and flips artwork_status.
func (s *Store) UpdateArtwork(ctx context.Context, movieID string, aw *Artwork) error {
	b, err := json.Marshal(aw)
	if err != nil {
		return fmt.Errorf("movie store: marshal artwork: %w", err)
	}
	_, err = s.db.ExecContext(ctx,
		`UPDATE movies SET art_json = ?, artwork_status = 'downloaded', updated_at = ? WHERE id = ?`,
		string(b), time.Now().UTC().Format(time.RFC3339), movieID)
	if err != nil {
		return fmt.Errorf("movie store: update artwork: %w", err)
	}
	return nil
}

// SetNfoStatus records the NFO state of a movie.
func (s *Store) SetNfoStatus(ctx context.Context, movieID, status string) error {
	_, err := s.db.ExecContext(ctx,
		`UPDATE movies SET nfo_status = ?, updated_at = ? WHERE id = ?`,
		status, time.Now().UTC().Format(time.RFC3339), movieID)
	if err != nil {
		return fmt.Errorf("movie store: set nfo status: %w", err)
	}
	return nil
}

// DeleteIfUnreferenced removes a movie row when no media item links to it
// anymore (a multi-version movie with several files stays). Returns whether
// the row was deleted.
func (s *Store) DeleteIfUnreferenced(ctx context.Context, movieID string) (bool, error) {
	var refs int
	if err := s.db.QueryRowContext(ctx,
		"SELECT COUNT(*) FROM media_items WHERE movie_id = ?", movieID).Scan(&refs); err != nil {
		return false, fmt.Errorf("movie store: count refs: %w", err)
	}
	if refs > 0 {
		return false, nil
	}
	res, err := s.db.ExecContext(ctx, "DELETE FROM movies WHERE id = ?", movieID)
	if err != nil {
		return false, fmt.Errorf("movie store: delete unreferenced: %w", err)
	}
	n, err := res.RowsAffected()
	if err != nil {
		return false, err
	}
	return n > 0, nil
}
