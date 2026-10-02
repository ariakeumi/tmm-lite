package http

import (
	"net/http"
)

// handleListTVShows returns TV shows, optionally filtered by libraryId.
func (s *Server) handleListTVShows(w http.ResponseWriter, r *http.Request) {
	shows, err := s.deps.TV.ListShows(r.Context(), r.URL.Query().Get("libraryId"))
	if err != nil {
		s.log.Error("list tv shows", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"shows": shows, "count": len(shows)})
}

// handleGetTVShow returns one show (full fields incl. match state) with
// its seasons.
func (s *Server) handleGetTVShow(w http.ResponseWriter, r *http.Request) {
	show, err := s.deps.TV.GetShowFull(r.Context(), r.PathValue("id"))
	if err != nil {
		writeAPIError(w, http.StatusNotFound, "tv show not found")
		return
	}
	seasons, err := s.deps.TV.ListSeasons(r.Context(), show.ID)
	if err != nil {
		s.log.Error("list tv seasons", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"show": show, "seasons": seasons, "count": len(seasons)})
}

// handleListTVEpisodes returns the episodes of a show.
func (s *Server) handleListTVEpisodes(w http.ResponseWriter, r *http.Request) {
	if _, err := s.deps.TV.GetShow(r.Context(), r.PathValue("id")); err != nil {
		writeAPIError(w, http.StatusNotFound, "tv show not found")
		return
	}
	episodes, err := s.deps.TV.ListEpisodes(r.Context(), r.PathValue("id"))
	if err != nil {
		s.log.Error("list tv episodes", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"episodes": episodes, "count": len(episodes)})
}
