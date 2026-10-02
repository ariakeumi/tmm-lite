package http

import (
	"net/http"

	"media-manager-lite/internal/version"
)

// handleHealth reports service liveness. It deliberately does not touch the
// database so it stays meaningful even while SQLite is briefly busy.
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

type versionResponse struct {
	Version   string `json:"version"`
	Commit    string `json:"commit"`
	BuildDate string `json:"buildDate"`
}

func (s *Server) handleVersion(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, versionResponse{
		Version:   version.Version,
		Commit:    version.Commit,
		BuildDate: version.BuildDate,
	})
}
