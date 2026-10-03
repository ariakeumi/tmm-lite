package http

import (
	"fmt"
	"net/http"
)

// handleScrapeMovie runs the combined scrape workflow for a matched movie:
// poster/fanart download followed by NFO write. Artwork failure does not
// block the NFO (poster-less NFO is still written); both results are
// reported. HTMX callers receive the refreshed row; the detail page form
// gets redirected back.
func (s *Server) handleScrapeMovie(w http.ResponseWriter, r *http.Request) {
	it, ok := s.mediaItemOr404(w, r)
	if !ok || !requireMovieKind(w, it) {
		return
	}
	if it.MovieID == "" {
		writeAPIError(w, http.StatusBadRequest, "media item is not matched to a movie yet")
		return
	}

	var artworkErr, nfoErr error
	aw, err := s.deps.Artwork.Download(r.Context(), it.ID)
	if err != nil {
		artworkErr = err
		s.log.Warn("movie scrape artwork", "item", it.ID, "error", err.Error())
	}
	nfoRes, err := s.deps.NFO.WriteForItem(r.Context(), it.ID)
	if err != nil {
		nfoErr = err
		s.log.Error("movie scrape nfo", "item", it.ID, "error", err)
	}
	if artworkErr != nil && nfoErr != nil {
		writeAPIError(w, http.StatusBadGateway, "scrape failed: artwork and NFO")
		return
	}

	if isHTMX(r) {
		s.renderMatchRow(w, r, http.StatusOK, it.ID)
		return
	}
	// Detail-page form: back to the movie page with updated statuses.
	http.Redirect(w, r, "/movies/"+it.ID, http.StatusSeeOther)
	_ = aw
	_ = nfoRes
	_ = fmt.Sprintf
}

// handleScrapeTVShow runs the combined scrape workflow for a matched TV
// show: show poster/fanart + season posters, then all tvshow/season/episode
// NFO files. Artwork failure does not block the NFO.
func (s *Server) handleScrapeTVShow(w http.ResponseWriter, r *http.Request) {
	id, ok := s.tvShowOr404(w, r)
	if !ok {
		return
	}
	full, err := s.deps.TV.GetShowFull(r.Context(), id)
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	if full.TMDBID <= 0 {
		writeAPIError(w, http.StatusBadRequest, "tv show is not matched to a TMDB show yet")
		return
	}

	var artworkErr, nfoErr error
	if _, err := s.deps.Artwork.DownloadShow(r.Context(), id); err != nil {
		artworkErr = err
		s.log.Warn("tv scrape artwork", "show", id, "error", err.Error())
	}
	if _, err := s.deps.NFO.WriteTVForShow(r.Context(), id); err != nil {
		nfoErr = err
		s.log.Error("tv scrape nfo", "show", id, "error", err)
	}
	if artworkErr != nil && nfoErr != nil {
		writeAPIError(w, http.StatusBadGateway, "scrape failed: artwork and NFO")
		return
	}

	if isHTMX(r) {
		s.renderTVShowPanel(w, r, http.StatusOK, id, "")
		return
	}
	http.Redirect(w, r, "/tv/shows/"+id, http.StatusSeeOther)
}
