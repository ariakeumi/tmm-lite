// Package http implements the application's HTTP layer: server-side rendered
// HTMX pages plus the JSON REST API. It uses only net/http (Go 1.22+ method
// patterns) — no third-party router.
//
// The package is named http on purpose (requested layout); inside its own
// files the identifier http always refers to the standard library import.
package http

import (
	"context"
	"log/slog"
	"net/http"
	"time"

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
	"media-manager-lite/web"
)

// Deps bundles the application services the HTTP layer orchestrates.
type Deps struct {
	Libraries *library.Service
	Media     *media.Store
	Scanner   *scanner.Service
	Tasks     *task.Store
	Runner    *task.Runner
	Settings  *settings.Store
	Movies    *movie.Store
	TMDB      *tmdb.Service
	Artwork   *artwork.Service
	NFO       *nfo.Service
	Renamer   *renamer.Service
	TV        *tv.Store
	TVScrape  *tv.ScrapeService
}

// Server owns the mux and the underlying http.Server.
type Server struct {
	cfg        config.Config
	log        *slog.Logger
	db         *database.DB
	deps       Deps
	tpl        *templates
	mux        *http.ServeMux
	httpServer *http.Server
}

// New builds the server: routes, templates and the http.Server with sane
// timeouts. Template parsing errors surface here so they fail at startup.
func New(cfg config.Config, log *slog.Logger, db *database.DB, deps Deps) (*Server, error) {
	tpl, err := newTemplates()
	if err != nil {
		return nil, err
	}

	s := &Server{cfg: cfg, log: log, db: db, deps: deps, tpl: tpl}
	mux := http.NewServeMux()
	s.routes(mux)
	s.mux = mux

	handler := withLogging(log, withRecover(log, mux))

	s.httpServer = &http.Server{
		Addr:              cfg.HTTPAddr,
		Handler:           handler,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       120 * time.Second,
		MaxHeaderBytes:    1 << 16,
	}
	return s, nil
}

func (s *Server) routes(mux *http.ServeMux) {
	// Pages (HTMX, server-rendered).
	mux.HandleFunc("GET /{$}", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/movies", http.StatusSeeOther)
	})
	mux.HandleFunc("GET /libraries", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/settings", http.StatusSeeOther)
	})
	mux.HandleFunc("GET /settings", s.handleSettingsPage)
	mux.HandleFunc("GET /libraries/{id}/match", s.handleMatchPage)
	mux.HandleFunc("GET /movies", s.handleMoviesPage)
	mux.HandleFunc("GET /tvshows", s.handleTVPage)
	mux.Handle("GET /static/", http.StripPrefix("/static/", http.FileServerFS(web.Static())))

	// JSON API.
	mux.HandleFunc("GET /api/health", s.handleHealth)
	mux.HandleFunc("GET /api/version", s.handleVersion)
	mux.HandleFunc("GET /api/libraries", s.handleListLibraries)
	mux.HandleFunc("POST /api/libraries", s.handleCreateLibrary)
	mux.HandleFunc("GET /api/libraries/{id}", s.handleGetLibrary)
	mux.HandleFunc("DELETE /api/libraries/{id}", s.handleDeleteLibrary)
	mux.HandleFunc("POST /api/libraries/{id}/scan", s.handleScanLibrary)
	mux.HandleFunc("GET /api/libraries/{id}/media", s.handleListMedia)

	// Matching (see docs/decisions.md D14: {id} is the media item id — a
	// movie record only exists after the match is confirmed).
	mux.HandleFunc("GET /api/movies", s.handleListMovies)
	mux.HandleFunc("GET /api/movies/{id}", s.handleGetMovieItem)
	mux.HandleFunc("POST /api/movies/{id}/search", s.handleSearchMovie)
	mux.HandleFunc("GET /api/movies/{id}/candidates", s.handleGetCandidates)
	mux.HandleFunc("POST /api/movies/{id}/match", s.handleMatchMovie)

	// Tasks + settings.
	mux.HandleFunc("GET /api/tasks", s.handleListTasks)
	mux.HandleFunc("GET /api/settings/tmdb-api-key", s.handleGetTMDBKey)
	mux.HandleFunc("PUT /api/settings/tmdb-api-key", s.handlePutTMDBKey)
	mux.HandleFunc("POST /api/movies/{id}/artwork", s.handleDownloadArtwork)
	mux.HandleFunc("POST /api/movies/{id}/nfo", s.handleWriteNFO)
	mux.HandleFunc("GET /api/movies/{id}/nfo", s.handleReadNFO)
	mux.HandleFunc("GET /movies/{id}", s.handleMoviePage)
	mux.HandleFunc("GET /api/movies/{id}/rename", s.handlePlanRename)
	mux.HandleFunc("POST /api/movies/{id}/rename", s.handleExecuteRename)
	mux.HandleFunc("GET /api/tv/shows", s.handleListTVShows)
	mux.HandleFunc("GET /api/tv/shows/{id}", s.handleGetTVShow)
	mux.HandleFunc("GET /api/tv/shows/{id}/episodes", s.handleListTVEpisodes)
	mux.HandleFunc("GET /tv", s.handleTVPage)
	mux.HandleFunc("GET /tv/shows/{id}", s.handleTVShowPage)
	mux.HandleFunc("POST /api/tv/shows/{id}/search", s.handleSearchTVShow)
	mux.HandleFunc("GET /api/tv/shows/{id}/candidates", s.handleGetTVShowCandidates)
	mux.HandleFunc("POST /api/tv/shows/{id}/match", s.handleMatchTVShow)
	mux.HandleFunc("POST /api/tv/shows/{id}/scrape", s.handleScrapeTVShow)
	mux.HandleFunc("POST /api/movies/{id}/scrape", s.handleScrapeMovie)
	mux.HandleFunc("POST /api/movies/scrape-all", s.handleScrapeAllMovies)
	mux.HandleFunc("POST /api/movies/rename-all", s.handleRenameAllMovies)
	mux.HandleFunc("POST /api/tv/scrape-all", s.handleScrapeAllTV)
	mux.HandleFunc("POST /api/tv/shows/{id}/nfo", s.handleWriteTVNFO)
	mux.HandleFunc("POST /api/tv/shows/{id}/artwork", s.handleDownloadTVArtwork)
	mux.HandleFunc("GET /api/libraries/{id}/skipped", s.handleListSkipped)
	mux.HandleFunc("GET /api/episodes/{id}/rename", s.handlePlanEpisodeRename)
	mux.HandleFunc("POST /api/episodes/{id}/rename", s.handleExecuteEpisodeRename)

	mux.HandleFunc("/api/", s.handleAPINotFound)
}

// Handler returns the fully wrapped handler (used by tests).
func (s *Server) Handler() http.Handler {
	return s.httpServer.Handler
}

// ListenAndServe starts serving; block on it or on Shutdown.
func (s *Server) ListenAndServe() error {
	return s.httpServer.ListenAndServe()
}

// Shutdown gracefully drains active requests.
func (s *Server) Shutdown(ctx context.Context) error {
	return s.httpServer.Shutdown(ctx)
}
