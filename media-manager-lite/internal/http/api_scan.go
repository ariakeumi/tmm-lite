package http

import (
	"net/http"
)

// handleScanLibrary submits an asynchronous scan task (single worker) and
// returns immediately: 202 Accepted with the pending task for API clients,
// the refreshed panel fragment for HTMX clients. Job failures are recorded
// on the task row (inspect /api/tasks).
func (s *Server) handleScanLibrary(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")

	if _, err := s.deps.Libraries.Get(r.Context(), id); err != nil {
		s.renderLibraryError(w, r, err)
		return
	}
	t, err := s.deps.Runner.Submit(r.Context(), "scan_library", "library", id)
	if err != nil {
		s.log.Error("submit scan", "error", err)
		if isHTMX(r) {
			s.renderLibrariesPanel(w, http.StatusOK, s.librariesPanelData(r, "could not queue scan: "+err.Error()))
			return
		}
		writeAPIError(w, http.StatusInternalServerError, "could not queue scan")
		return
	}

	if isHTMX(r) {
		s.renderLibrariesPanel(w, http.StatusAccepted, s.librariesPanelData(r, ""))
		return
	}
	writeJSON(w, http.StatusAccepted, t)
}

// handleListMedia returns the media items discovered for a library.
func (s *Server) handleListMedia(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if _, err := s.deps.Libraries.Get(r.Context(), id); err != nil {
		writeAPIError(w, statusForLibraryError(err), statusTextForLibraryError(err))
		return
	}
	items, err := s.deps.Media.ListByLibrary(r.Context(), id)
	if err != nil {
		s.log.Error("list media", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"mediaItems": items, "count": len(items)})
}

// handleListTasks returns the most recent tasks.
func (s *Server) handleListTasks(w http.ResponseWriter, r *http.Request) {
	tasks, err := s.deps.Tasks.List(r.Context(), 50)
	if err != nil {
		s.log.Error("list tasks", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"tasks": tasks, "count": len(tasks)})
}
