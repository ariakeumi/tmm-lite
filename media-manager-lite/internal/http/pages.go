package http

import (
	"net/http"

	"media-manager-lite/internal/database"
	"media-manager-lite/internal/library"
	"media-manager-lite/internal/version"
)

// base carries the fields every page template needs.
type base struct {
	Title        string
	Nav          string
	BuildVersion string
}

type dashboardData struct {
	base
	Counts database.Stats
}

type librariesData struct {
	base
	Libraries []library.Library
	Error     string
}

type settingsData struct {
	base
}

func (s *Server) handleDashboard(w http.ResponseWriter, r *http.Request) {
	counts, err := s.db.Stats(r.Context())
	if err != nil {
		s.log.Error("dashboard stats", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	s.renderPage(w, http.StatusOK, "dashboard", dashboardData{
		base:   base{Title: "Dashboard", Nav: "dashboard", BuildVersion: version.Version},
		Counts: counts,
	})
}

func (s *Server) handleLibrariesPage(w http.ResponseWriter, r *http.Request) {
	libs, err := s.deps.Libraries.List(r.Context())
	if err != nil {
		s.log.Error("list libraries", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	s.renderPage(w, http.StatusOK, "libraries", librariesData{
		base:      base{Title: "Libraries", Nav: "libraries", BuildVersion: version.Version},
		Libraries: libs,
	})
}

func (s *Server) handleSettingsPage(w http.ResponseWriter, r *http.Request) {
	s.renderPage(w, http.StatusOK, "settings", settingsData{
		base: base{Title: "Settings", Nav: "settings", BuildVersion: version.Version},
	})
}
