package http

import (
	"bytes"
	"context"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"

	"media-manager-lite/internal/artwork"
	"media-manager-lite/internal/config"
	"media-manager-lite/internal/database"
	"media-manager-lite/internal/library"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/nfo"
	"media-manager-lite/internal/renamer"
	"media-manager-lite/internal/scanner"
	"media-manager-lite/internal/settings"
	"media-manager-lite/internal/task"
	"media-manager-lite/internal/tmdb"
	"media-manager-lite/internal/tv"
)

// newTestServer builds a fully wired Server on a temporary SQLite database.
// The task worker runs so submitted scans complete.
func newTestServer(t *testing.T) *httptest.Server {
	t.Helper()
	ts, _ := newTestServerWithDeps(t, nil, "")
	return ts
}

// newTestServerWithDeps additionally returns the deps for direct inspection
// and can point the TMDB service at a fixture server.
func newTestServerWithDeps(t *testing.T, logOut io.Writer, tmdbBaseURL string) (*httptest.Server, Deps) {
	t.Helper()

	db, err := database.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	if err := database.Migrate(t.Context(), db.DB); err != nil {
		t.Fatal(err)
	}

	if logOut == nil {
		logOut = io.Discard
	}
	cfg := config.Defaults()
	log := slog.New(slog.NewTextHandler(logOut, nil))
	libs := library.NewService(library.NewStore(db.DB))
	mediaStore := media.NewStore(db.DB)
	tvStore := tv.NewStore(db.DB)
	movies := movie.NewStore(db.DB)
	scanSvc := scanner.NewService(mediaStore, tvStore, movies)
	tasks := task.NewStore(db.DB)
	runner := task.NewRunner(tasks, log, 64)
	runner.Register("scan_library", scanner.ScanHandler(libs, scanSvc))
	if err := runner.Start(t.Context()); err != nil {
		t.Fatal(err)
	}
	settingsStore := settings.NewStore(db.DB)
	tmdbSvc := tmdb.NewService(settingsStore, "test-key-1234567890", tmdbBaseURL)
	nfoSvc := nfo.NewService(mediaStore, movies, settingsStore, tmdbSvc, tvStore, libs)
	artworkSvc := artwork.NewService(tmdbSvc, mediaStore, movies, tvStore, libs, nfoSvc)
	renamerSvc := renamer.NewService(mediaStore, movies, libs, settingsStore, tvStore)
	tvScrape := tv.NewScrapeService(tvStore, tmdbSvc, func(ctx context.Context) string {
		v, _ := settingsStore.String(ctx, "certification_country", "US")
		return v
	})

	runner.Register("download_artwork", artwork.DownloadHandler(mediaStore, artworkSvc))
	runner.Register("download_tv_artwork", artwork.TVDownloadHandler(tvStore, artworkSvc))
	runner.Register("scrape_tv_episodes", tv.EnrichHandler(tvStore, tvScrape))

	deps := Deps{
		Libraries: libs,
		Media:     mediaStore,
		Scanner:   scanSvc,
		Tasks:     tasks,
		Runner:    runner,
		Settings:  settingsStore,
		Movies:    movies,
		TMDB:      tmdbSvc,
		Artwork:   artworkSvc,
		NFO:       nfoSvc,
		Renamer:   renamerSvc,
		TV:        tvStore,
		TVScrape:  tvScrape,
	}
	srv, err := New(cfg, log, db, deps)
	if err != nil {
		t.Fatalf("New() error = %v", err)
	}
	ts := httptest.NewServer(srv.Handler())
	t.Cleanup(ts.Close)
	return ts, deps
}

func TestPagesRender(t *testing.T) {
	ts := newTestServer(t)

	cases := []struct {
		path     string
		contains []string
	}{
		{"/", []string{"电影"}},             // redirects to /movies
		{"/libraries", []string{"添加媒体库"}}, // redirects to /settings
		{"/settings", []string{"设置", "TMDB API Key", "添加媒体库"}},
		{"/movies", []string{"电影"}},
		{"/tvshows", []string{"电视剧"}},
	}
	for _, tc := range cases {
		t.Run(tc.path, func(t *testing.T) {
			resp, err := http.Get(ts.URL + tc.path)
			if err != nil {
				t.Fatal(err)
			}
			defer resp.Body.Close()
			body, _ := io.ReadAll(resp.Body)

			if resp.StatusCode != http.StatusOK {
				t.Errorf("GET %s status = %d, want 200", tc.path, resp.StatusCode)
			}
			if ct := resp.Header.Get("Content-Type"); ct != "text/html; charset=utf-8" {
				t.Errorf("Content-Type = %q", ct)
			}
			for _, want := range tc.contains {
				if !bytes.Contains(body, []byte(want)) {
					t.Errorf("GET %s body missing %q", tc.path, want)
				}
			}
		})
	}
}

func TestStaticCSS(t *testing.T) {
	ts := newTestServer(t)
	resp, err := http.Get(ts.URL + "/static/style.css")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Errorf("status = %d, want 200", resp.StatusCode)
	}
	if ct := resp.Header.Get("Content-Type"); ct != "text/css; charset=utf-8" {
		t.Errorf("Content-Type = %q", ct)
	}
}

func TestUnknownAPIRouteReturnsJSON404(t *testing.T) {
	ts := newTestServer(t)
	resp, err := http.Get(ts.URL + "/api/nope")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("status = %d, want 404", resp.StatusCode)
	}
	if ct := resp.Header.Get("Content-Type"); ct != "application/json; charset=utf-8" {
		t.Errorf("Content-Type = %q, want JSON", ct)
	}
}

func TestMethodNotAllowed(t *testing.T) {
	ts := newTestServer(t)

	// The /api/ catch-all answers unknown method+path combinations with a
	// JSON 404 (see routes()), so POST /api/health falls through to it.
	resp, err := http.Post(ts.URL+"/api/health", "application/json", nil)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("POST /api/health status = %d, want 404 (JSON catch-all)", resp.StatusCode)
	}
	if ct := resp.Header.Get("Content-Type"); ct != "application/json; charset=utf-8" {
		t.Errorf("Content-Type = %q, want JSON", ct)
	}

	// Page routes have no catch-all, so the mux answers 405 itself.
	resp2, err := http.Post(ts.URL+"/libraries", "application/x-www-form-urlencoded", nil)
	if err != nil {
		t.Fatal(err)
	}
	resp2.Body.Close()
	if resp2.StatusCode != http.StatusMethodNotAllowed {
		t.Errorf("POST /libraries status = %d, want 405", resp2.StatusCode)
	}
}
