package http

import (
	"context"
	"net/http"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/matcher"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/renamer"
	"media-manager-lite/internal/version"
)

// matchRowData is one row of the match page: the media item plus its stored
// candidates (nil before the first search).
type matchRowData struct {
	Item       media.Item
	Candidates []matcher.MatchResult
	PosterURL  string
	Error      string
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
	posters, err := s.moviePosterURLs(r.Context(), lib.ID)
	if err != nil {
		s.log.Error("list movie artwork", "error", err)
	}
	for _, it := range items {
		if it.Kind != media.KindMovie {
			continue
		}
		cands, err := s.deps.Media.Candidates(r.Context(), it.ID)
		if err != nil {
			cands = nil
		}
		rows = append(rows, matchRowData{Item: it, Candidates: cands, PosterURL: posters[it.MovieID]})
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
	row := matchRowData{Item: it, Candidates: cands}
	if it.MovieID != "" {
		if m, err := s.deps.Movies.GetByID(ctx, it.MovieID); err == nil && m.Artwork != nil {
			row.PosterURL = m.Artwork.PosterURL
		}
	}
	return row, nil
}

func (s *Server) moviePosterURLs(ctx context.Context, libraryID string) (map[string]string, error) {
	movies, err := s.deps.Movies.ListByLibrary(ctx, libraryID)
	if err != nil {
		return nil, err
	}
	posters := make(map[string]string, len(movies))
	for _, m := range movies {
		if m.Artwork != nil {
			posters[m.ID] = m.Artwork.PosterURL
		}
	}
	return posters, nil
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

// renderModalCandidates writes the modal candidates fragment after a
// search (HTMX swaps it into #modal-content).
func (s *Server) renderModalCandidates(w http.ResponseWriter, r *http.Request, status int, itemID string) {
	row, err := s.loadMatchRow(r.Context(), itemID)
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.tpl.pages["match"].ExecuteTemplate(w, "modal_candidates", row); err != nil {
		s.log.Error("render modal candidates", "error", err)
	}
}

// renderModalCandidatesError renders the modal fragment with an inline
// error (search failures keep the dialog open).
func (s *Server) renderModalCandidatesError(w http.ResponseWriter, r *http.Request, itemID, msg string) {
	row, err := s.loadMatchRow(r.Context(), itemID)
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	row.Error = msg
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusOK) // fragment mode: swap regardless (see D7)
	if err := s.tpl.pages["match"].ExecuteTemplate(w, "modal_candidates", row); err != nil {
		s.log.Error("render modal candidates error", "error", err)
	}
}

// renderModalRename renders the rename plan modal fragment. On execution
// errors the modal stays open with the message (HTMX-Retarget in the
// handler moves the swap back into the dialog).
func (s *Server) renderModalRename(w http.ResponseWriter, r *http.Request, status int, itemID string, plan *renamer.Plan, errMsg string) {
	data := struct {
		ItemID string
		Plan   *renamer.Plan
		Error  string
	}{itemID, plan, errMsg}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.tpl.pages["match"].ExecuteTemplate(w, "modal_rename", data); err != nil {
		s.log.Error("render modal rename", "error", err)
	}
}
