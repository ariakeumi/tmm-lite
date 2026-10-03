package http

import (
	"net/http"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/version"
)

type moviesPageData struct {
	base
	HasLibraries bool
	Sections     []movieSection
}

// movieSection is one movie library's inline match workflow: the same
// rows the per-library match page renders.
type movieSection struct {
	Library library.Library
	Rows    []matchRowData
}

// handleMoviesPage renders the movie list page as a direct match
// workflow: every movie library's scanned files with their candidates
// and match/artwork/nfo actions.
func (s *Server) handleMoviesPage(w http.ResponseWriter, r *http.Request) {
	libs, err := s.deps.Libraries.List(r.Context())
	if err != nil {
		s.log.Error("movies page", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	data := moviesPageData{
		base:         base{Title: "电影", Nav: "movies", BuildVersion: version.Version},
		HasLibraries: false,
	}

	for _, l := range libs {
		if l.Type != library.TypeMovie {
			continue
		}
		if !data.HasLibraries {
			data.HasLibraries = true
		}
		section := movieSection{Library: l}
		items, err := s.deps.Media.ListByLibrary(r.Context(), l.ID)
		if err != nil {
			s.log.Error("movies page items", "error", err)
			writeAPIError(w, http.StatusInternalServerError, "internal server error")
			return
		}
		for _, it := range items {
			if it.Kind != media.KindMovie {
				continue
			}
			row := matchRowData{Item: it}
			if cands, err := s.deps.Media.Candidates(r.Context(), it.ID); err == nil {
				row.Candidates = cands
			}
			section.Rows = append(section.Rows, row)
		}
		data.Sections = append(data.Sections, section)
	}

	s.renderPage(w, http.StatusOK, "movies", data)
}
