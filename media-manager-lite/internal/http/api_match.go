package http

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"

	"media-manager-lite/internal/matcher"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/settings"
	"media-manager-lite/internal/tmdb"
)

// tmdbErrorStatus maps TMDB service errors to HTTP statuses and safe
// messages (never exposing the API key).
func tmdbErrorStatus(err error) (int, string) {
	switch {
	case errors.Is(err, tmdb.ErrNotConfigured):
		return http.StatusBadRequest, "TMDB API key not configured (PUT /api/settings/tmdb-api-key)"
	case errors.Is(err, tmdb.ErrUnauthorized):
		return http.StatusForbidden, "invalid TMDB API key"
	case errors.Is(err, tmdb.ErrRateLimited):
		return http.StatusTooManyRequests, "TMDB rate limit reached, retry later"
	case errors.Is(err, tmdb.ErrNotFound):
		return http.StatusNotFound, "not found on TMDB"
	default:
		return http.StatusBadGateway, "TMDB request failed"
	}
}

// mediaItemOr404 loads the media item behind the /api/movies/{id} routes
// (D14: pre-match, the "movie" is the media item). Both kinds are accepted;
// movie-only handlers validate the kind themselves.
func (s *Server) mediaItemOr404(w http.ResponseWriter, r *http.Request) (media.Item, bool) {
	it, err := s.deps.Media.GetItem(r.Context(), r.PathValue("id"))
	if err != nil {
		writeAPIError(w, http.StatusNotFound, "media item not found")
		return media.Item{}, false
	}
	return it, true
}

// requireMovieKind guards the movie-only matching endpoints.
func requireMovieKind(w http.ResponseWriter, it media.Item) bool {
	if it.Kind != media.KindMovie {
		writeAPIError(w, http.StatusBadRequest, "only movie media items can be matched in this milestone")
		return false
	}
	return true
}

// handleSearchMovie runs the TMDB search for a media item, scores the
// candidates and persists them. Search results are never auto-confirmed.
func (s *Server) handleSearchMovie(w http.ResponseWriter, r *http.Request) {
	it, ok := s.mediaItemOr404(w, r)
	if !ok || !requireMovieKind(w, it) {
		return
	}
	if it.ParsedTitle == "" {
		writeAPIError(w, http.StatusBadRequest, "filename did not yield a searchable title")
		return
	}

	results, err := s.deps.TMDB.SearchMovieByText(r.Context(), it.ParsedTitle, it.ParsedYear)
	if err != nil {
		status, msg := tmdbErrorStatus(err)
		s.log.Error("tmdb search", "item", it.ID, "status", status, "error", err.Error())
		writeAPIError(w, status, msg)
		return
	}

	candidates := make([]matcher.Candidate, 0, len(results))
	for _, sr := range results {
		candidates = append(candidates, matcher.Candidate{
			TMDBID:        sr.TMDBID,
			Title:         sr.Title,
			OriginalTitle: sr.OriginalTitle,
			Year:          sr.Year(),
			Overview:      sr.Overview,
			PosterPath:    sr.PosterPath,
			ReleaseDate:   sr.ReleaseDate,
		})
	}
	ranked := matcher.Rank(matcher.Local{Title: it.ParsedTitle, Year: it.ParsedYear}, candidates)

	if err := s.deps.Media.SaveCandidates(r.Context(), it.ID, ranked); err != nil {
		s.log.Error("save candidates", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	if isHTMX(r) {
		s.renderMatchCandidates(w, r, http.StatusOK, it.ID)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"itemId":      it.ID,
		"parsedTitle": it.ParsedTitle,
		"count":       len(ranked),
		"candidates":  ranked,
	})
}

// handleGetCandidates returns the persisted candidates of a media item.
func (s *Server) handleGetCandidates(w http.ResponseWriter, r *http.Request) {
	it, ok := s.mediaItemOr404(w, r)
	if !ok || !requireMovieKind(w, it) {
		return
	}
	candidates, err := s.deps.Media.Candidates(r.Context(), it.ID)
	if err != nil {
		s.log.Error("load candidates", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"itemId":     it.ID,
		"status":     it.Status,
		"count":      len(candidates),
		"candidates": candidates,
	})
}

type matchRequest struct {
	TMDBID    int
	Unmatched bool
}

func decodeMatchRequest(w http.ResponseWriter, r *http.Request) (matchRequest, error) {
	var req matchRequest
	ct := r.Header.Get("Content-Type")
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
	if strings.HasPrefix(ct, "application/json") {
		var body struct {
			TMDBID    int  `json:"tmdbId"`
			Unmatched bool `json:"unmatched"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			return req, err
		}
		return matchRequest{TMDBID: body.TMDBID, Unmatched: body.Unmatched}, nil
	}
	if err := r.ParseForm(); err != nil {
		return req, err
	}
	req.Unmatched = r.PostFormValue("unmatched") == "true"
	if v := r.PostFormValue("tmdbId"); v != "" {
		id, err := strconv.Atoi(v)
		if err != nil {
			return req, err
		}
		req.TMDBID = id
	}
	return req, nil
}

// handleMatchMovie confirms a candidate: fetches full TMDB details and
// transactionally upserts the movie + links the media item (matched), or
// marks the item unmatched when body.unmatched is set.
func (s *Server) handleMatchMovie(w http.ResponseWriter, r *http.Request) {
	it, ok := s.mediaItemOr404(w, r)
	if !ok || !requireMovieKind(w, it) {
		return
	}
	req, err := decodeMatchRequest(w, r)
	if err != nil {
		writeAPIError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	if req.Unmatched {
		if err := s.deps.Movies.MarkUnmatched(r.Context(), it.ID); err != nil {
			if errors.Is(err, movie.ErrItemNotFound) {
				writeAPIError(w, http.StatusNotFound, "media item not found")
				return
			}
			s.log.Error("mark unmatched", "error", err)
			writeAPIError(w, http.StatusInternalServerError, "internal server error")
			return
		}
		if isHTMX(r) {
			s.renderMatchRow(w, r, http.StatusOK, it.ID)
			return
		}
		writeJSON(w, http.StatusOK, map[string]any{"itemId": it.ID, "status": movie.StatusUnmatched})
		return
	}

	if req.TMDBID <= 0 {
		writeAPIError(w, http.StatusBadRequest, "tmdbId must be a positive TMDB id (or set unmatched=true)")
		return
	}

	res, err := s.deps.TMDB.MovieDetail(r.Context(), req.TMDBID)
	if err != nil {
		status, msg := tmdbErrorStatus(err)
		s.log.Error("tmdb detail", "item", it.ID, "status", status, "error", err.Error())
		writeAPIError(w, status, msg)
		return
	}

	certCountry, err := s.deps.Settings.String(r.Context(), "certification_country", "US")
	if err != nil {
		certCountry = "US"
	}
	m, err := s.deps.Movies.ConfirmMatch(r.Context(), it.ID, movie.FromTMDB(it.LibraryID, res, certCountry))
	if err != nil {
		if errors.Is(err, movie.ErrItemNotFound) {
			writeAPIError(w, http.StatusNotFound, "media item not found")
			return
		}
		s.log.Error("confirm match", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}

	if isHTMX(r) {
		s.renderMatchRow(w, r, http.StatusOK, it.ID)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"itemId": it.ID,
		"status": movie.StatusMatched,
		"movie":  m,
	})
}

// handleGetMovieItem returns a media item with its linked movie (if any).
func (s *Server) handleGetMovieItem(w http.ResponseWriter, r *http.Request) {
	it, ok := s.mediaItemOr404(w, r)
	if !ok {
		return
	}
	resp := map[string]any{
		"itemId": it.ID,
		"status": it.Status,
		"path":   it.Path,
		"parsed": it.Parsed,
	}
	if it.MovieID != "" {
		if m, err := s.deps.Movies.GetByID(r.Context(), it.MovieID); err == nil {
			resp["movie"] = m
		}
	}
	writeJSON(w, http.StatusOK, resp)
}

// handleListMovies returns matched movies, optionally filtered by library.
func (s *Server) handleListMovies(w http.ResponseWriter, r *http.Request) {
	libID := r.URL.Query().Get("libraryId")
	if libID == "" {
		writeAPIError(w, http.StatusBadRequest, "libraryId query parameter is required")
		return
	}
	movies, err := s.deps.Movies.ListByLibrary(r.Context(), libID)
	if err != nil {
		s.log.Error("list movies", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"movies": movies, "count": len(movies)})
}

// handlePutTMDBKey stores the user's own TMDB API key. Accepts JSON
// ({"apiKey": "..."}) or a form-encoded body (settings page form). The key
// is never echoed back, never logged.
func (s *Server) handlePutTMDBKey(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
	var apiKey string
	if strings.HasPrefix(r.Header.Get("Content-Type"), "application/json") {
		var body struct {
			APIKey string `json:"apiKey"`
		}
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			writeAPIError(w, http.StatusBadRequest, "invalid JSON body")
			return
		}
		apiKey = body.APIKey
	} else {
		if err := r.ParseForm(); err != nil {
			writeAPIError(w, http.StatusBadRequest, "invalid form body")
			return
		}
		apiKey = r.PostFormValue("apiKey")
	}
	apiKey = strings.TrimSpace(apiKey)
	if len(apiKey) < 8 || len(apiKey) > 255 {
		writeAPIError(w, http.StatusBadRequest, "apiKey looks invalid")
		return
	}
	if err := s.deps.Settings.Set(r.Context(), settings.KeyTMDBAPIKey, apiKey); err != nil {
		s.log.Error("store tmdb key", "error", err)
		if isHTMX(r) {
			s.renderKeyStatus(w, r, "保存失败")
			return
		}
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	s.deps.TMDB.ResetConfigurationCache()
	s.log.Info("tmdb api key updated")
	if isHTMX(r) {
		s.renderKeyStatus(w, r, "")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"configured": true})
}

// renderKeyStatus renders the key status fragment for the settings page.
func (s *Server) renderKeyStatus(w http.ResponseWriter, r *http.Request, errMsg string) {
	configured := errMsg == ""
	data := struct {
		KeyConfigured bool
		Error         string
	}{configured, errMsg}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	if err := s.tpl.pages["settings"].ExecuteTemplate(w, "key_status", data); err != nil {
		s.log.Error("render key status", "error", err)
	}
}

// handleGetTMDBKey reports whether a key is configured — without the key.
func (s *Server) handleGetTMDBKey(w http.ResponseWriter, r *http.Request) {
	configured, err := s.deps.TMDB.KeyConfigured(r.Context())
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"configured": configured})
}
