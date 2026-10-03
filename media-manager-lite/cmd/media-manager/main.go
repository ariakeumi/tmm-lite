// Command media-manager is the Media Manager Lite server binary.
//
// main only wires the application together: load config, open + migrate the
// database, build application services, start the HTTP server and shut down
// gracefully on SIGINT/SIGTERM (stop accepting HTTP, drain requests, close
// database).
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"media-manager-lite/internal/artwork"
	"media-manager-lite/internal/config"
	"media-manager-lite/internal/database"
	httpapi "media-manager-lite/internal/http"
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
	"media-manager-lite/internal/version"
)

func main() {
	if err := run(); err != nil {
		fmt.Fprintln(os.Stderr, "fatal:", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load(os.Getenv)
	if err != nil {
		return fmt.Errorf("load config: %w", err)
	}

	logger := newLogger(cfg)
	slog.SetDefault(logger)
	logger.Info("starting media-manager-lite",
		"version", version.Version,
		"commit", version.Commit,
		"build_date", version.BuildDate,
	)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	db, err := database.Open(cfg.DBPath)
	if err != nil {
		return fmt.Errorf("open database: %w", err)
	}
	defer db.Close()

	if err := database.Migrate(ctx, db.DB); err != nil {
		return fmt.Errorf("migrate database: %w", err)
	}

	libs := library.NewService(library.NewStore(db.DB))
	mediaStore := media.NewStore(db.DB)
	tvStore := tv.NewStore(db.DB)
	movies := movie.NewStore(db.DB)
	scanSvc := scanner.NewService(mediaStore, tvStore, movies)
	tasks := task.NewStore(db.DB)
	runner := task.NewRunner(tasks, logger, 64)
	runner.Register("scan_library", scanner.ScanHandler(libs, scanSvc))
	settingsStore := settings.NewStore(db.DB)
	tmdbSvc := tmdb.NewService(settingsStore, cfg.TMDBAPIKey, cfg.TMDBBaseURL)
	nfoSvc := nfo.NewService(mediaStore, movies, settingsStore, tmdbSvc, tvStore, libs)
	artworkSvc := artwork.NewService(tmdbSvc, mediaStore, movies, tvStore, libs, nfoSvc)
	renamerSvc := renamer.NewService(mediaStore, movies, libs, settingsStore, tvStore)
	tvScrape := tv.NewScrapeService(tvStore, tmdbSvc, func(ctx context.Context) string {
		v, _ := settingsStore.String(ctx, settings.KeyCertCountry, "US")
		return v
	})

	runner.Register("download_artwork", artwork.DownloadHandler(mediaStore, artworkSvc))
	runner.Register("scrape_tv_episodes", tv.EnrichHandler(tvStore, tvScrape))
	runner.Register("scrape_tv_library", func(ctx context.Context, _ /*targetType*/, libraryID string) (string, error) {
		shows, err := tvStore.ListShows(ctx, libraryID)
		if err != nil {
			return "", err
		}
		enriched, scraped, failed := 0, 0, 0
		for _, sh := range shows {
			if n, err := tvScrape.EnrichEpisodes(ctx, sh.ID); err == nil {
				enriched += n
			}
			if _, err := artworkSvc.DownloadShow(ctx, sh.ID); err != nil {
				failed++
			}
			if _, err := nfoSvc.WriteTVForShow(ctx, sh.ID); err != nil {
				failed++
			}
			scraped++
		}
		return fmt.Sprintf("tv library: %d show(s), enriched %d episode(s), %d failure(s)", scraped, enriched, failed), nil
	})
	runner.Register("download_tv_artwork", artwork.TVDownloadHandler(tvStore, artworkSvc))
	runner.Register("scrape_movie_library", artwork.MovieLibraryHandler(mediaStore, movies, nfoSvc, artworkSvc))
	runner.Register("rename_movie_library", renamer.RenameMovieLibraryHandler(mediaStore, renamerSvc))

	// Apply the restart policy and start the single worker before serving.
	if err := runner.Start(ctx); err != nil {
		return fmt.Errorf("start task runner: %w", err)
	}

	srv, err := httpapi.New(cfg, logger, db, httpapi.Deps{
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
	})
	if err != nil {
		return fmt.Errorf("init http server: %w", err)
	}

	serveErr := make(chan error, 1)
	go func() {
		serveErr <- srv.ListenAndServe()
	}()
	logger.Info("http server started", "addr", cfg.HTTPAddr)

	select {
	case err := <-serveErr:
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			return fmt.Errorf("http server: %w", err)
		}
	case <-ctx.Done():
		logger.Info("shutdown signal received")
	}

	// 1) stop accepting HTTP, 2) drain active requests, 3) close DB (defer).
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Error("http shutdown incomplete", "error", err)
	}
	logger.Info("server stopped")
	return nil
}

func newLogger(cfg config.Config) *slog.Logger {
	return slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{
		Level: cfg.SlogLevel(),
	}))
}
