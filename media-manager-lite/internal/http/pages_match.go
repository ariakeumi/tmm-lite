package http

import (
	"context"
	"net/http"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/matcher"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/version"
)

// matchRowData is one row of the match page: the media item plus its stored
// candidates (nil before the first search).
type matchRowData struct {
	Item       media.Item
	Candidates []matcher.MatchResult
}

type matchPageData struct {
	base
	Library library.Library
	Rows    []matchRowData
}

// handleMatchPage renders the minimal HTMX confirmation flow for a movie
// library: unmatched files, search button per file, candidate list with a
// Match button each.
func (s *Server) handleMatchPage(w http.ResponseWriter, r *http.Request) {
	lib, err := s.deps.Libraries.Get(r.Context(), r.PathValue("id"))
	if err != nil {
		writeAPIError(w, statusForLibraryError(err), statusTextForLibraryError(err))
		return
	}
	if lib.Type != library.TypeMovie {
		writeAPIError(w, http.StatusBadRequest, "matching is available for movie libraries in this milestone")
		return
	}

	items, err := s.deps.Media.ListByLibrary(r.Context(), lib.ID)
	if err != nil {
		s.log.Error("list media for match page", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	rows := make([]matchRowData, 0, len(items))
	for _, it := range items {
		if it.Kind != media.KindMovie {
			continue
		}
		cands, err := s.deps.Media.Candidates(r.Context(), it.ID)
		if err != nil {
			cands = nil
		}
		rows = append(rows, matchRowData{Item: it, Candidates: cands})
	}

	s.renderPage(w, http.StatusOK, "match", matchPageData{
		base:    base{Title: "Match", Nav: "match", BuildVersion: version.Version},
		Library: lib,
		Rows:    rows,
	})
}

// loadMatchRow assembles the fragment data for one media item.
func (s *Server) loadMatchRow(ctx context.Context, itemID string) (matchRowData, error) {
	it, err := s.deps.Media.GetItem(ctx, itemID)
	if err != nil {
		return matchRowData{}, err
	}
	cands, _ := s.deps.Media.Candidates(ctx, itemID)
	return matchRowData{Item: it, Candidates: cands}, nil
}

// renderMatchRow writes the refreshed row fragment after a match/unmatch.
func (s *Server) renderMatchRow(w http.ResponseWriter, r *http.Request, status int, itemID string) {
	row, err := s.loadMatchRow(r.Context(), itemID)
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.tpl.pages["match"].ExecuteTemplate(w, "match_row", row); err != nil {
		s.log.Error("render match row", "error", err)
	}
}

// renderMatchCandidates writes the candidates fragment after a search.
func (s *Server) renderMatchCandidates(w http.ResponseWriter, r *http.Request, status int, itemID string) {
	row, err := s.loadMatchRow(r.Context(), itemID)
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.tpl.pages["match"].ExecuteTemplate(w, "match_candidates", row); err != nil {
		s.log.Error("render match candidates", "error", err)
	}
}
