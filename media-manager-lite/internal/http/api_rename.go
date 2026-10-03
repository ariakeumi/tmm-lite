package http

import (
	"net/http"
)

// handlePlanRename returns the rename plan. This is the dry-run: it only
// reads the directory and database — zero filesystem writes.
func (s *Server) handlePlanRename(w http.ResponseWriter, r *http.Request) {
	it, ok := s.mediaItemOr404(w, r)
	if !ok {
		return
	}
	plan, err := s.deps.Renamer.PlanForItem(r.Context(), it.ID)
	if err != nil {
		if isHTMX(r) {
			s.renderModalRename(w, r, http.StatusOK, it.ID, nil, err.Error())
			return
		}
		writeAPIError(w, http.StatusBadRequest, err.Error())
		return
	}
	if isHTMX(r) {
		s.renderModalRename(w, r, http.StatusOK, it.ID, plan, "")
		return
	}
	writeJSON(w, http.StatusOK, plan)
}

// handleExecuteRename plans and executes the rename, then updates the
// database. Never called implicitly — the client must POST explicitly.
func (s *Server) handleExecuteRename(w http.ResponseWriter, r *http.Request) {
	it, ok := s.mediaItemOr404(w, r)
	if !ok {
		return
	}
	plan, results, err := s.deps.Renamer.ExecuteForItem(r.Context(), it.ID)
	if err != nil {
		s.log.Error("execute rename", "error", err)
		if isHTMX(r) {
			// Retarget the swap into the still-open modal and show the error.
			w.Header().Set("HX-Retarget", "#modal-content")
			w.Header().Set("HX-Reswap", "innerHTML")
			s.renderModalRename(w, r, http.StatusOK, it.ID, plan, err.Error())
			return
		}
		writeJSON(w, http.StatusConflict, map[string]any{
			"error":    err.Error(),
			"plan":     plan,
			"executed": results,
		})
		return
	}
	if isHTMX(r) {
		s.renderMatchRow(w, r, http.StatusOK, it.ID)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"executed": results,
		"plan":     plan,
	})
}
