package http

import (
	"context"
	"net/http"

	"media-manager-lite/internal/media"
	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/renamer"
	"media-manager-lite/internal/version"
)

type moviePageData struct {
	base
	Item    media.Item
	Movie   *movie.Movie
	Plan    *renamer.Plan
	Error   string
	Message string
}

func (s *Server) moviePageData(ctx context.Context, it media.Item) moviePageData {
	data := moviePageData{
		base: base{Title: "Movie", Nav: "movies", BuildVersion: version.Version},
		Item: it,
	}
	if it.MovieID != "" {
		if m, err := s.deps.Movies.GetByID(ctx, it.MovieID); err == nil {
			data.Movie = &m
		}
	}
	return data
}

// handleMoviePage renders the minimal movie workflow page:
// scan → match → artwork → NFO → rename preview → confirm.
func (s *Server) handleMoviePage(w http.ResponseWriter, r *http.Request) {
	it, ok := s.mediaItemOr404(w, r)
	if !ok {
		return
	}
	data := s.moviePageData(r.Context(), it)
	data.Plan, _ = s.deps.Renamer.PlanForItem(r.Context(), it.ID)
	s.renderPage(w, http.StatusOK, "movie", data)
}

// loadRenamePanel assembles the rename panel fragment: the current item
// state plus a freshly computed plan.
func (s *Server) loadRenamePanel(ctx context.Context, itemID, errMsg, msg string) (moviePageData, error) {
	it, err := s.deps.Media.GetItem(ctx, itemID)
	if err != nil {
		return moviePageData{}, err
	}
	data := s.moviePageData(ctx, it)
	data.Error = errMsg
	data.Message = msg
	if it.MovieID != "" {
		data.Plan, _ = s.deps.Renamer.PlanForItem(ctx, itemID)
	}
	return data, nil
}

// renderRenamePanel writes the rename panel fragment.
func (s *Server) renderRenamePanel(w http.ResponseWriter, r *http.Request, status int, itemID string) {
	data, err := s.loadRenamePanel(r.Context(), itemID, "", "rename completed")
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.tpl.pages["movie"].ExecuteTemplate(w, "rename_panel", data); err != nil {
		s.log.Error("render rename panel", "error", err)
	}
}

// renderRenamePanelWithError keeps a failed plan visible for inspection.
func (s *Server) renderRenamePanelWithError(w http.ResponseWriter, r *http.Request, itemID, errMsg string, plan *renamer.Plan) {
	data, err := s.loadRenamePanel(r.Context(), itemID, errMsg, "")
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	data.Plan = plan
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	if err := s.tpl.pages["movie"].ExecuteTemplate(w, "rename_panel", data); err != nil {
		s.log.Error("render rename panel", "error", err)
	}
}
