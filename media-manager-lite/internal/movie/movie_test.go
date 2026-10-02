package movie

import (
	"context"
	"database/sql"
	"path/filepath"
	"testing"

	"media-manager-lite/internal/database"
	"media-manager-lite/internal/tmdb"
)

func newTestStore(t *testing.T) (*Store, *sql.DB) {
	t.Helper()
	db, err := database.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	if err := database.Migrate(context.Background(), db.DB); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	return NewStore(db.DB), db.DB
}

func seed(t *testing.T, db *sql.DB, id string) {
	t.Helper()
	_, err := db.ExecContext(context.Background(),
		`INSERT INTO libraries (id, name, path, type, created_at, updated_at)
		 VALUES (?, 'Test', ?, 'movie', '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')`, id, "/tmp/"+id)
	if err != nil {
		t.Fatal(err)
	}
	_, err = db.ExecContext(context.Background(),
		`INSERT INTO media_items (id, library_id, kind, path, status, created_at, updated_at)
		 VALUES (?, ?, 'movie', '/tmp/x/A.2019.mkv', 'new', '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')`,
		"item-1", id)
	if err != nil {
		t.Fatal(err)
	}
}

func fixtureDetail() *tmdb.MovieDetailResult {
	return &tmdb.MovieDetailResult{
		Detail: &tmdb.MovieDetail{
			TMDBID: 111, Title: "大电影", OriginalTitle: "Movie One",
			Overview: "cn overview", Tagline: "tag", ReleaseDate: "2019-05-17",
			Runtime: 96, VoteAverage: 7.8, VoteCount: 100, OriginalLanguage: "en",
			Genres:              []tmdb.Genre{{ID: 28, Name: "Action"}},
			ProductionCompanies: []tmdb.Company{{ID: 1, Name: "Example Studios"}},
			Credits: struct {
				Cast []tmdb.CastMember `json:"cast"`
				Crew []tmdb.CrewMember `json:"crew"`
			}{
				Cast: []tmdb.CastMember{{TMDBID: 9, Name: "Jane Doe", Character: "Lead"}},
				Crew: []tmdb.CrewMember{
					{TMDBID: 8, Name: "John Director", Job: "Director", Department: "Directing"},
					{TMDBID: 7, Name: "Ann Writer", Job: "Screenplay", Department: "Writing"},
				},
			},
			ExternalIDs: struct {
				IMDBID string `json:"imdb_id"`
			}{IMDBID: "tt0000111"},
		},
		Best:      tmdb.BestTitle{Title: "大电影", Overview: "cn overview", EnglishTitle: "Movie One", Source: "exact"},
		Language:  "zh-CN",
		PosterURL: "http://image.example/w342/p1.jpg",
	}
}

func TestConfirmMatchTransactional(t *testing.T) {
	store, db := newTestStore(t)
	seed(t, db, "lib-1")
	ctx := context.Background()

	m := FromTMDB("lib-1", fixtureDetail(), "US")
	if m.Year != 2019 || m.Title != "大电影" || m.Certification != "" {
		t.Errorf("mapped movie = %+v", m)
	}

	// media_items.movie_id links the item; status flips to matched.
	got, err := store.ConfirmMatch(ctx, "item-1", m)
	if err != nil {
		t.Fatal(err)
	}
	if got.ID == "" || got.TMDBID != 111 {
		t.Errorf("confirmed movie = %+v", got)
	}

	var status, movieID string
	if err := db.QueryRowContext(ctx,
		"SELECT status, COALESCE(movie_id,'') FROM media_items WHERE id = 'item-1'").
		Scan(&status, &movieID); err != nil {
		t.Fatal(err)
	}
	if status != StatusMatched || movieID != got.ID {
		t.Errorf("media item = %s/%s, want matched/%s", status, movieID, got.ID)
	}

	// Re-confirming the same TMDB id updates the row instead of duplicating.
	m2 := FromTMDB("lib-1", fixtureDetail(), "US")
	m2.Votes = 4321
	got2, err := store.ConfirmMatch(ctx, "item-1", m2)
	if err != nil {
		t.Fatal(err)
	}
	if got2.ID != got.ID {
		t.Errorf("re-confirm created a new movie row: %s vs %s", got2.ID, got.ID)
	}
	var votes int
	if err := db.QueryRowContext(ctx, "SELECT votes FROM movies WHERE id = ?", got.ID).Scan(&votes); err != nil {
		t.Fatal(err)
	}
	if votes != 4321 {
		t.Errorf("votes = %d, want 4321 (updated)", votes)
	}

	// Metadata round-trips through the JSON column.
	full, err := store.GetByID(ctx, got.ID)
	if err != nil {
		t.Fatal(err)
	}
	if full.Metadata == nil || len(full.Metadata.Genres) != 1 || full.Metadata.Genres[0] != "Action" {
		t.Errorf("metadata = %+v", full.Metadata)
	}
	if full.Metadata.EnglishTitle != "Movie One" || full.Metadata.IMDBID != "tt0000111" {
		t.Errorf("metadata = %+v", full.Metadata)
	}

	// ListByLibrary fills metadata as well.
	list, err := store.ListByLibrary(ctx, "lib-1")
	if err != nil {
		t.Fatal(err)
	}
	if len(list) != 1 || list[0].Metadata == nil {
		t.Errorf("list = %+v", list)
	}
}

func TestConfirmMatchUnknownItemFailsAtomically(t *testing.T) {
	store, db := newTestStore(t)
	seed(t, db, "lib-1")
	ctx := context.Background()

	if _, err := store.ConfirmMatch(ctx, "no-such-item", FromTMDB("lib-1", fixtureDetail(), "US")); err != ErrItemNotFound {
		t.Fatalf("error = %v, want ErrItemNotFound", err)
	}
	// The transaction rolled back: no movie row may exist.
	var n int
	if err := db.QueryRowContext(ctx, "SELECT COUNT(*) FROM movies").Scan(&n); err != nil {
		t.Fatal(err)
	}
	if n != 0 {
		t.Errorf("movies rows = %d, want 0 (rollback)", n)
	}
}

func TestMarkUnmatched(t *testing.T) {
	store, db := newTestStore(t)
	seed(t, db, "lib-1")
	ctx := context.Background()

	if _, err := store.ConfirmMatch(ctx, "item-1", FromTMDB("lib-1", fixtureDetail(), "US")); err != nil {
		t.Fatal(err)
	}
	if err := store.MarkUnmatched(ctx, "item-1"); err != nil {
		t.Fatal(err)
	}
	var status string
	var movieID string
	if err := db.QueryRowContext(ctx,
		"SELECT status, COALESCE(movie_id,'') FROM media_items WHERE id = 'item-1'").
		Scan(&status, &movieID); err != nil {
		t.Fatal(err)
	}
	if status != StatusUnmatched || movieID != "" {
		t.Errorf("media item = %s/%q, want unmatched/empty", status, movieID)
	}
	if err := store.MarkUnmatched(ctx, "missing"); err != ErrItemNotFound {
		t.Errorf("error = %v, want ErrItemNotFound", err)
	}
}

func TestFromTMDBCertification(t *testing.T) {
	res := fixtureDetail()
	res.Detail.ReleaseDates.Results = []struct {
		ISO3166_1    string `json:"iso_3166_1"`
		ReleaseDates []struct {
			Certification string `json:"certification"`
			Type          int    `json:"type"`
			Date          string `json:"release_date"`
		} `json:"release_dates"`
	}{}
	res.Detail.ReleaseDates.Results = append(res.Detail.ReleaseDates.Results, struct {
		ISO3166_1    string `json:"iso_3166_1"`
		ReleaseDates []struct {
			Certification string `json:"certification"`
			Type          int    `json:"type"`
			Date          string `json:"release_date"`
		} `json:"release_dates"`
	}{ISO3166_1: "US", ReleaseDates: []struct {
		Certification string `json:"certification"`
		Type          int    `json:"type"`
		Date          string `json:"release_date"`
	}{
		{Certification: "NR", Type: 1, Date: "2019-01-01"},
		{Certification: "PG-13", Type: 3, Date: "2019-05-17"},
	}})

	m := FromTMDB("lib-1", res, "US")
	if m.Certification != "PG-13" {
		t.Errorf("certification = %q, want PG-13 (theatrical preferred)", m.Certification)
	}
	if m.Metadata.Directors == nil || len(m.Metadata.Writers) != 1 {
		t.Errorf("crew mapping = %+v", m.Metadata)
	}
}
