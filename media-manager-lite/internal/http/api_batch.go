package http

import (
	"context"
	"fmt"
	"net/http"

	"media-manager-lite/internal/library"
)

// submitMovieBatch enqueues one task per movie library for a batch job
// (scrape or rename) targeting its matched items.
func (s *Server) submitMovieBatch(w http.ResponseWriter, r *http.Request, typ string) (int, error) {
	libs, err := s.deps.Libraries.List(r.Context())
	if err != nil {
		return 0, err
	}
	n := 0
	for _, l := range libs {
		if l.Type != library.TypeMovie {
			continue
		}
		if _, err := s.deps.Runner.Submit(r.Context(), typ, "library", l.ID); err != nil {
			return n, err
		}
		n++
	}
	return n, nil
}

// handleScrapeAllMovies queues artwork + NFO for every matched movie item
// in every movie library. Unmatched items are skipped by the handler.
func (s *Server) handleScrapeAllMovies(w http.ResponseWriter, r *http.Request) {
	n, err := s.submitMovieBatch(w, r, "scrape_movie_library")
	if err != nil {
		s.log.Error("submit movie scrape batch", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "could not queue scrape tasks")
		return
	}
	if isHTMX(r) {
		s.renderLibrariesPanel(w, http.StatusOK, s.librariesPanelData(r, ""))
		return
	}
	writeJSON(w, http.StatusAccepted, map[string]any{"queued": n, "type": "scrape_movie_library"})
}

// handleRenameAllMovies queues the rename workflow for every movie library
// (plan validation + execution happen inside the task; unmatched items are
// skipped there).
func (s *Server) handleRenameAllMovies(w http.ResponseWriter, r *http.Request) {
	n, err := s.submitMovieBatch(w, r, "rename_movie_library")
	if err != nil {
		s.log.Error("submit movie rename batch", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "could not queue rename tasks")
		return
	}
	if isHTMX(r) {
		s.renderLibrariesPanel(w, http.StatusOK, s.librariesPanelData(r, ""))
		return
	}
	writeJSON(w, http.StatusAccepted, map[string]any{"queued": n, "type": "rename_movie_library"})
}

// submitTVBatch enqueues one scrape task per TV library.
func (s *Server) submitTVBatch(w http.ResponseWriter, r *http.Request) (int, error) {
	libs, err := s.deps.Libraries.List(r.Context())
	if err != nil {
		return 0, err
	}
	n := 0
	for _, l := range libs {
		if l.Type != library.TypeTV {
			continue
		}
		if _, err := s.deps.Runner.Submit(r.Context(), "scrape_tv_library", "library", l.ID); err != nil {
			return n, err
		}
		n++
	}
	return n, nil
}

// handleScrapeAllTV queues the per-library TV scrape tasks (enrich +
// artwork + NFO for every show).
func (s *Server) handleScrapeAllTV(w http.ResponseWriter, r *http.Request) {
	n, err := s.submitTVBatch(w, r)
	if err != nil {
		s.log.Error("submit tv scrape batch", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "could not queue tv scrape tasks")
		return
	}
	writeJSON(w, http.StatusAccepted, map[string]any{"queued": n, "type": "scrape_tv_library"})
}

// handleScrapeTVShowLibrary runs per-show enrich + artwork + NFO for one
// TV library (task body).
func (s *Server) scrapeTVShowLibrary(ctx context.Context, libraryID string) (string, error) {
	libs, err := s.deps.Libraries.List(ctx)
	if err != nil {
		return "", err
	}
	var libName string
	for _, l := range libs {
		if l.ID == libraryID {
			libName = l.Name
		}
	}
	shows, err := s.deps.TV.ListShows(ctx, libraryID)
	if err != nil {
		return "", err
	}
	enriched, scraped, failed := 0, 0, 0
	for _, sh := range shows {
		if n, err := s.deps.TVScrape.EnrichEpisodes(ctx, sh.ID); err == nil {
			enriched += n
		}
		if _, err := s.deps.Artwork.DownloadShow(ctx, sh.ID); err != nil {
			failed++
		}
		if _, err := s.deps.NFO.WriteTVForShow(ctx, sh.ID); err != nil {
			failed++
		}
		scraped++
	}
	return fmt.Sprintf("tv library %q: %d show(s), enriched %d episode(s), %d failure(s)", libName, scraped, enriched, failed), nil
}
