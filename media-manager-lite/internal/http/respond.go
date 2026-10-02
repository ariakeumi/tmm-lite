package http

import (
	"encoding/json"
	"errors"
	"net/http"

	"media-manager-lite/internal/library"
)

// maxBodyBytes caps request body size for all API writes.
const maxBodyBytes = 1 << 20 // 1 MiB

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeAPIError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

// isHTMX reports whether the request originates from an HTMX fragment swap.
func isHTMX(r *http.Request) bool {
	return r.Header.Get("HX-Request") == "true"
}

// statusForLibraryError maps domain errors to HTTP statuses without leaking
// filesystem internals.
func statusForLibraryError(err error) int {
	switch {
	case errors.Is(err, library.ErrNotFound):
		return http.StatusNotFound
	case errors.Is(err, library.ErrDuplicatePath):
		return http.StatusConflict
	case errors.Is(err, library.ErrInvalidName),
		errors.Is(err, library.ErrInvalidType),
		errors.Is(err, library.ErrInvalidPath),
		errors.Is(err, library.ErrPathNotDir),
		errors.Is(err, library.ErrPathNotAccessible):
		return http.StatusBadRequest
	default:
		return http.StatusInternalServerError
	}
}

// handleAPINotFound returns a JSON 404 for unknown /api/ routes.
func (s *Server) handleAPINotFound(w http.ResponseWriter, r *http.Request) {
	writeAPIError(w, http.StatusNotFound, "not found")
}
