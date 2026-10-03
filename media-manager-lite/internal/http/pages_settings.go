package http

import (
	"net/http"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/version"
)

type settingsData struct {
	base
	Libraries     []library.Library
	KeyConfigured bool
	Error         string
}

func (s *Server) handleSettingsPage(w http.ResponseWriter, r *http.Request) {
	data := settingsData{
		base:      base{Title: "设置", Nav: "settings", BuildVersion: version.Version},
		Libraries: s.librariesPanelData(r, "").Libraries,
	}
	configured, err := s.deps.TMDB.KeyConfigured(r.Context())
	if err != nil {
		s.log.Error("key configured", "error", err)
	}
	data.KeyConfigured = configured
	s.renderPage(w, http.StatusOK, "settings", data)
}
