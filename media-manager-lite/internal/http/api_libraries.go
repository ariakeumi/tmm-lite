package http

import (
	"encoding/json"
	"net/http"
	"strings"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/version"
)

// libraryRequest is the shared body shape for JSON and HTMX form posts.
type libraryRequest struct {
	Name string
	Path string
	Type string
}

func (s *Server) handleListLibraries(w http.ResponseWriter, r *http.Request) {
	libs, err := s.deps.Libraries.List(r.Context())
	if err != nil {
		s.log.Error("list libraries", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"libraries": libs})
}

// handleCreateLibrary accepts JSON (API) or form-encoded (HTMX) bodies.
// HTMX requests receive an HTML panel fragment; validation errors render the
// panel with the error message (HTTP 200 in fragment mode, proper 4xx in
// JSON mode — see docs/decisions.md D7).
func (s *Server) handleCreateLibrary(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)

	req, err := decodeLibraryRequest(r)
	if err != nil {
		if isHTMX(r) {
			s.renderLibrariesPanel(w, http.StatusOK, s.librariesPanelData(r, "Invalid request body."))
			return
		}
		writeAPIError(w, http.StatusBadRequest, "invalid request body")
		return
	}

	lib, err := s.deps.Libraries.Create(r.Context(), req.Name, req.Path, library.Type(req.Type))
	if err != nil {
		s.renderLibraryError(w, r, err)
		return
	}

	if isHTMX(r) {
		s.renderLibrariesPanel(w, http.StatusCreated, s.librariesPanelData(r, ""))
		return
	}
	writeJSON(w, http.StatusCreated, lib)
}

func (s *Server) handleGetLibrary(w http.ResponseWriter, r *http.Request) {
	lib, err := s.deps.Libraries.Get(r.Context(), r.PathValue("id"))
	if err != nil {
		writeAPIError(w, statusForLibraryError(err), statusTextForLibraryError(err))
		return
	}
	writeJSON(w, http.StatusOK, lib)
}

func (s *Server) handleDeleteLibrary(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	// The browser never sees raw IDs from user input, but stay defensive:
	// an ID is a server-generated UUID.
	if id == "" || len(id) > 64 || strings.ContainsAny(id, "/\\") {
		writeAPIError(w, http.StatusBadRequest, "invalid library id")
		return
	}

	err := s.deps.Libraries.Delete(r.Context(), id)
	if err != nil {
		s.renderLibraryError(w, r, err)
		return
	}

	if isHTMX(r) {
		s.renderLibrariesPanel(w, http.StatusOK, s.librariesPanelData(r, ""))
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// decodeLibraryRequest parses JSON or form bodies into a libraryRequest.
func decodeLibraryRequest(r *http.Request) (libraryRequest, error) {
	var req libraryRequest
	ct := r.Header.Get("Content-Type")
	if strings.HasPrefix(ct, "application/json") {
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			return libraryRequest{}, err
		}
		return req, nil
	}
	if err := r.ParseForm(); err != nil {
		return libraryRequest{}, err
	}
	req.Name = r.PostFormValue("name")
	req.Path = r.PostFormValue("path")
	req.Type = r.PostFormValue("type")
	return req, nil
}

// renderLibraryError emits a JSON error for API clients or the panel
// fragment with the error message for HTMX clients.
func (s *Server) renderLibraryError(w http.ResponseWriter, r *http.Request, err error) {
	status := statusForLibraryError(err)
	if status == http.StatusInternalServerError {
		s.log.Error("library operation failed", "error", err)
	}
	if isHTMX(r) {
		s.renderLibrariesPanel(w, http.StatusOK, s.librariesPanelData(r, statusTextForLibraryError(err)))
		return
	}
	writeAPIError(w, status, statusTextForLibraryError(err))
}

func (s *Server) librariesPanelData(r *http.Request, errMsg string) librariesData {
	libs, err := s.deps.Libraries.List(r.Context())
	if err != nil {
		s.log.Error("list libraries for panel", "error", err)
		libs = []library.Library{}
	}
	return librariesData{
		base:      base{Title: "Libraries", Nav: "libraries", BuildVersion: version.Version},
		Libraries: libs,
		Error:     errMsg,
	}
}

// statusTextForLibraryError converts domain errors into user-facing messages.
// The sentinel errors carry only user-actionable information.
func statusTextForLibraryError(err error) string {
	switch {
	case err == nil:
		return ""
	default:
		return err.Error()
	}
}
