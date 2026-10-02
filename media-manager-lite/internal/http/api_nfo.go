package http

import (
	"errors"
	"net/http"
	"os"
	"path"
	"path/filepath"
)

// handleDownloadArtwork queues the async artwork download for a matched
// movie item (poster + fanart into the media folder).
func (s *Server) handleDownloadArtwork(w http.ResponseWriter, r *http.Request) {
	it, ok := s.mediaItemOr404(w, r)
	if !ok {
		return
	}
	if it.MovieID == "" {
		msg := "media item is not matched to a movie yet"
		if isHTMX(r) {
			s.renderLibrariesPanel(w, http.StatusOK, s.librariesPanelData(r, msg))
			return
		}
		writeAPIError(w, http.StatusBadRequest, msg)
		return
	}

	t, err := s.deps.Runner.Submit(r.Context(), "download_artwork", "media_item", it.ID)
	if err != nil {
		s.log.Error("submit artwork download", "error", err)
		if isHTMX(r) {
			s.renderLibrariesPanel(w, http.StatusOK, s.librariesPanelData(r, "could not queue artwork download"))
			return
		}
		writeAPIError(w, http.StatusInternalServerError, "could not queue artwork download")
		return
	}

	if isHTMX(r) {
		s.renderMatchRow(w, r, http.StatusAccepted, it.ID)
		return
	}
	writeJSON(w, http.StatusAccepted, t)
}

// handleWriteNFO writes the movie NFO synchronously (a local file write).
// User data from an existing NFO is merged; unchanged content is skipped.
func (s *Server) handleWriteNFO(w http.ResponseWriter, r *http.Request) {
	it, ok := s.mediaItemOr404(w, r)
	if !ok {
		return
	}
	if it.MovieID == "" {
		writeAPIError(w, http.StatusBadRequest, "media item is not matched to a movie yet")
		return
	}
	res, err := s.deps.NFO.WriteForItem(r.Context(), it.ID)
	if err != nil {
		s.log.Error("write nfo", "error", err)
		if isHTMX(r) {
			s.renderLibrariesPanel(w, http.StatusOK, s.librariesPanelData(r, "could not write NFO"))
			return
		}
		writeAPIError(w, http.StatusInternalServerError, "could not write NFO")
		return
	}
	if isHTMX(r) {
		s.renderMatchRow(w, r, http.StatusOK, it.ID)
		return
	}
	writeJSON(w, http.StatusOK, res)
}

// handleReadNFO returns the current NFO file content for inspection.
func (s *Server) handleReadNFO(w http.ResponseWriter, r *http.Request) {
	it, ok := s.mediaItemOr404(w, r)
	if !ok {
		return
	}
	dir := filepath.Dir(it.Path)
	base := path.Base(it.Filename)
	target := filepath.Join(dir, base[:len(base)-len(filepath.Ext(base))]+".nfo")
	b, err := os.ReadFile(target)
	if err != nil {
		if errors.Is(err, os.ErrNotExist) {
			writeAPIError(w, http.StatusNotFound, "no NFO written yet")
			return
		}
		s.log.Error("read nfo", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	w.Header().Set("Content-Type", "text/xml; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(b)
}
