package http

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	"media-manager-lite/internal/renamer"
)

// tvShowOr404 loads the show behind the /api/tv/shows/{id}/... routes.
func (s *Server) tvShowOr404(w http.ResponseWriter, r *http.Request) (string, bool) {
	id := r.PathValue("id")
	if _, err := s.deps.TV.GetShow(r.Context(), id); err != nil {
		writeAPIError(w, http.StatusNotFound, "tv show not found")
		return "", false
	}
	return id, true
}

// handleSearchTVShow runs the TMDB show search and persists candidates.
func (s *Server) handleSearchTVShow(w http.ResponseWriter, r *http.Request) {
	id, ok := s.tvShowOr404(w, r)
	if !ok {
		return
	}
	ranked, err := s.deps.TVScrape.SearchShow(r.Context(), id)
	if err != nil {
		status, msg := tmdbErrorStatus(err)
		s.log.Error("tmdb tv search", "show", id, "status", status, "error", err.Error())
		if isHTMX(r) {
			s.renderTVCandidatesError(w, r, id, msg)
			return
		}
		writeAPIError(w, status, msg)
		return
	}
	if isHTMX(r) {
		s.renderTVCandidates(w, r, http.StatusOK, id)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"showId":     id,
		"count":      len(ranked),
		"candidates": ranked,
	})
}

// handleGetTVShowCandidates returns the persisted show candidates.
func (s *Server) handleGetTVShowCandidates(w http.ResponseWriter, r *http.Request) {
	id, ok := s.tvShowOr404(w, r)
	if !ok {
		return
	}
	candidates, err := s.deps.TV.ShowCandidates(r.Context(), id)
	if err != nil {
		s.log.Error("load tv candidates", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"showId":     id,
		"count":      len(candidates),
		"candidates": candidates,
	})
}

// handleMatchTVShow confirms a show match (transactional) and queues the
// async season/episode enrichment. {"unmatched": true} clears the match.
func (s *Server) handleMatchTVShow(w http.ResponseWriter, r *http.Request) {
	id, ok := s.tvShowOr404(w, r)
	if !ok {
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
	var body struct {
		TMDBID    int  `json:"tmdbId"`
		Unmatched bool `json:"unmatched"`
	}
	ct := r.Header.Get("Content-Type")
	if strings.HasPrefix(ct, "application/json") {
		if err := decodeJSONBody(w, r, &body); err != nil {
			writeAPIError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
	} else {
		if err := r.ParseForm(); err != nil {
			writeAPIError(w, http.StatusBadRequest, "invalid form body")
			return
		}
		body.Unmatched = r.PostFormValue("unmatched") == "true"
		if v := r.PostFormValue("tmdbId"); v != "" {
			n, err := strconv.Atoi(v)
			if err != nil {
				writeAPIError(w, http.StatusBadRequest, "tmdbId must be numeric")
				return
			}
			body.TMDBID = n
		}
	}

	if body.Unmatched {
		if err := s.deps.TVScrape.UnmatchShow(r.Context(), id); err != nil {
			s.log.Error("unmatch tv show", "error", err)
			writeAPIError(w, http.StatusInternalServerError, "internal server error")
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"showId": id, "status": "unmatched"})
		return
	}

	if body.TMDBID <= 0 {
		writeAPIError(w, http.StatusBadRequest, "tmdbId must be a positive TMDB id")
		return
	}
	if err := s.deps.TVScrape.MatchShow(r.Context(), id, body.TMDBID); err != nil {
		status, msg := tmdbErrorStatus(err)
		s.log.Error("tmdb tv match", "show", id, "status", status, "error", err.Error())
		if isHTMX(r) {
			s.renderTVRow(w, r, http.StatusOK, id)
			return
		}
		writeAPIError(w, status, msg)
		return
	}

	// Enrichment of seasons/episodes runs on the worker; the confirmation
	// itself has already been committed transactionally.
	if _, err := s.deps.Runner.Submit(r.Context(), "scrape_tv_episodes", "tv_show", id); err != nil {
		s.log.Error("submit tv enrich", "error", err)
	}

	if isHTMX(r) {
		s.renderTVRow(w, r, http.StatusOK, id)
		return
	}
	show, err := s.deps.TV.GetShowFull(r.Context(), id)
	if err != nil {
		writeJSON(w, http.StatusOK, map[string]any{"showId": id, "status": "matched"})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"showId": id, "status": "matched", "show": show})
}

// decodeJSONBody decodes a bounded JSON request body.
func decodeJSONBody(w http.ResponseWriter, r *http.Request, v any) error {
	return json.NewDecoder(r.Body).Decode(v)
}

// handleWriteTVNFO writes all NFO files of a show (synchronous, like the
// movie variant — local file I/O only).
func (s *Server) handleWriteTVNFO(w http.ResponseWriter, r *http.Request) {
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
	results, err := s.deps.NFO.WriteTVForShow(r.Context(), id)
	if err != nil {
		s.log.Error("write tv nfo", "error", err)
		if isHTMX(r) {
			s.renderTVShowPanel(w, r, http.StatusOK, id, "could not write NFO: "+err.Error())
			return
		}
		writeAPIError(w, http.StatusInternalServerError, "could not write NFO")
		return
	}
	if isHTMX(r) {
		s.renderTVShowPanel(w, r, http.StatusOK, id, "")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"results": results, "count": len(results)})
}

// handlePlanEpisodeRename returns the dry-run plan for one episode item.
func (s *Server) handlePlanEpisodeRename(w http.ResponseWriter, r *http.Request) {
	itemID := r.PathValue("id")
	plan, err := s.deps.Renamer.PlanEpisodeForItem(r.Context(), itemID)
	if err != nil {
		writeAPIError(w, http.StatusBadRequest, err.Error())
		return
	}
	if isHTMX(r) {
		s.renderEpisodeRenamePanel(w, r, http.StatusOK, itemID, plan, "")
		return
	}
	writeJSON(w, http.StatusOK, plan)
}

// handleExecuteEpisodeRename executes the plan and updates the DB.
func (s *Server) handleExecuteEpisodeRename(w http.ResponseWriter, r *http.Request) {
	itemID := r.PathValue("id")
	plan, results, err := s.deps.Renamer.ExecuteEpisodeForItem(r.Context(), itemID)
	if err != nil {
		s.log.Error("execute episode rename", "error", err)
		if isHTMX(r) {
			s.renderEpisodeRenamePanel(w, r, http.StatusOK, itemID, plan, err.Error())
			return
		}
		writeJSON(w, http.StatusConflict, map[string]any{"error": err.Error(), "plan": plan, "executed": results})
		return
	}
	if isHTMX(r) {
		s.renderEpisodeRenamePanel(w, r, http.StatusOK, itemID, plan, "rename completed")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"executed": results, "plan": plan})
}

// renderEpisodeRenamePanel renders the per-episode rename fragment.
func (s *Server) renderEpisodeRenamePanel(w http.ResponseWriter, r *http.Request, status int, itemID string, plan *renamer.Plan, msg string) {
	data := struct {
		ItemID  string
		Plan    *renamer.Plan
		Message string
	}{itemID, plan, msg}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.tpl.pages["tvshow"].ExecuteTemplate(w, "episode_rename_panel", data); err != nil {
		s.log.Error("render episode rename panel", "error", err)
	}
}

// handleDownloadTVArtwork queues the async TV artwork download (show
// poster/fanart + season posters).
func (s *Server) handleDownloadTVArtwork(w http.ResponseWriter, r *http.Request) {
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
	t, err := s.deps.Runner.Submit(r.Context(), "download_tv_artwork", "tv_show", id)
	if err != nil {
		s.log.Error("submit tv artwork", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "could not queue artwork download")
		return
	}
	if isHTMX(r) {
		s.renderTVShowPanel(w, r, http.StatusAccepted, id, "")
		return
	}
	writeJSON(w, http.StatusAccepted, t)
}

// handleListSkipped returns the videos a library scan could not identify.
func (s *Server) handleListSkipped(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if _, err := s.deps.Libraries.Get(r.Context(), id); err != nil {
		writeAPIError(w, statusForLibraryError(err), statusTextForLibraryError(err))
		return
	}
	items, err := s.deps.Media.ListSkippedByLibrary(r.Context(), id)
	if err != nil {
		s.log.Error("list skipped", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"skipped": items, "count": len(items)})
}
