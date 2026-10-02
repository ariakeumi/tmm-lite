package http

import (
	"net/http"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/version"
)

type moviesPageData struct {
	base
	Movies       []moviesRow
	Pending      []pendingRow
	HasLibraries bool
}

type moviesRow struct {
	ID            string
	Title         string
	Year          int
	TMDBID        int
	Rating        float64
	Library       string
	NfoStatus     string
	ArtworkStatus string
	PosterURL     string
}

type pendingRow struct {
	LibraryID   string
	LibraryName string
	Count       int
}

// handleMoviesPage renders the global movie list: every matched movie
// across libraries plus per-library counts of files awaiting a match.
func (s *Server) handleMoviesPage(w http.ResponseWriter, r *http.Request) {
	libs, err := s.deps.Libraries.List(r.Context())
	if err != nil {
		s.log.Error("movies page", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	data := moviesPageData{
		base:         base{Title: "Movies", Nav: "movies", BuildVersion: version.Version},
		HasLibraries: len(libs) > 0,
	}

	for _, l := range libs {
		if l.Type != library.TypeMovie {
			continue
		}
		movies, err := s.deps.Movies.ListByLibrary(r.Context(), l.ID)
		if err != nil {
			s.log.Error("movies page list", "error", err)
			writeAPIError(w, http.StatusInternalServerError, "internal server error")
			return
		}
		for _, m := range movies {
			poster := ""
			if m.Artwork != nil {
				poster = m.Artwork.PosterURL
			}
			data.Movies = append(data.Movies, moviesRow{
				ID: m.ID, Title: m.Title, Year: m.Year, TMDBID: m.TMDBID,
				Rating: m.Rating, Library: l.Name,
				NfoStatus: m.NfoStatus, ArtworkStatus: m.ArtworkStatus,
				PosterURL: poster,
			})
		}

		items, err := s.deps.Media.ListByLibrary(r.Context(), l.ID)
		if err != nil {
			s.log.Error("movies page items", "error", err)
			writeAPIError(w, http.StatusInternalServerError, "internal server error")
			return
		}
		pending := 0
		for _, it := range items {
			if it.Kind == media.KindMovie && it.Status != movie.StatusMatched {
				pending++
			}
		}
		if pending > 0 {
			data.Pending = append(data.Pending, pendingRow{
				LibraryID: l.ID, LibraryName: l.Name, Count: pending,
			})
		}
	}

	s.renderPage(w, http.StatusOK, "movies", data)
}
