package http

import (
	"context"
	"net/http"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/version"
)

type tvPageData struct {
	base
	Shows       []tvShowRow
	TVLibraries []library.Library
}

type tvShowRow struct {
	ID            string
	Title         string
	TMDBID        int
	SeasonCount   int
	EpisodeCount  int
	AiredStatus   string
	ArtworkStatus string
}

// handleTVPage lists all TV shows with links to their detail pages.
func (s *Server) handleTVPage(w http.ResponseWriter, r *http.Request) {
	libs, err := s.deps.Libraries.List(r.Context())
	if err != nil {
		s.log.Error("tv page", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	shows, err := s.deps.TV.ListShows(r.Context(), "")
	if err != nil {
		s.log.Error("tv page", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	rows := []tvShowRow{}
	for _, sh := range shows {
		row := tvShowRow{ID: sh.ID, Title: sh.Title}
		if full, err := s.deps.TV.GetShowFull(r.Context(), sh.ID); err == nil {
			row.TMDBID = full.TMDBID
			row.AiredStatus = full.AiredStatus
		}
		if eps, err := s.deps.TV.ListEpisodes(r.Context(), sh.ID); err == nil {
			row.EpisodeCount = len(eps)
		}
		if se, err := s.deps.TV.ListSeasons(r.Context(), sh.ID); err == nil {
			row.SeasonCount = len(se)
		}
		rows = append(rows, row)
	}
	tvLibs := []library.Library{}
	for _, l := range libs {
		if l.Type == library.TypeTV {
			tvLibs = append(tvLibs, l)
		}
	}
	s.renderPage(w, http.StatusOK, "tv", tvPageData{
		base:        base{Title: "电视剧", Nav: "tv", BuildVersion: version.Version},
		Shows:       rows,
		TVLibraries: tvLibs,
	})
}

type tvShowPageData struct {
	base
	Show       *tvShowRow
	Seasons    []tvSeasonRow
	Candidates []tvCandidateRow
	Error      string
}

type tvSeasonRow struct {
	Number   int
	Title    string
	Episodes []tvEpisodeRow
}

type tvEpisodeRow struct {
	SeasonNumber  int
	EpisodeNumber int
	Title         string
	AirDate       string
	ItemID        string
}

type tvCandidateRow struct {
	Score     float64
	TMDBID    int
	Name      string
	Year      int
	IDMatch   bool
	PosterURL string
}

// handleTVShowPage renders the minimal HTMX confirmation flow for one show.
func (s *Server) handleTVShowPage(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	sh, err := s.deps.TV.GetShow(r.Context(), id)
	if err != nil {
		writeAPIError(w, http.StatusNotFound, "tv show not found")
		return
	}
	data, err := s.tvShowPageData(r, id, sh.Title)
	if err != nil {
		s.log.Error("tv show page", "error", err)
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	s.renderPage(w, http.StatusOK, "tvshow", data)
}

// showRowData assembles one show row (list page) — row + candidates.
func (s *Server) showRowData(ctx context.Context, id string) (tvShowRow, []tvCandidateRow, error) {
	full, err := s.deps.TV.GetShowFull(ctx, id)
	if err != nil {
		return tvShowRow{}, nil, err
	}
	row := tvShowRow{ID: full.ID, Title: full.Title, TMDBID: full.TMDBID,
		AiredStatus: full.AiredStatus, ArtworkStatus: full.ArtworkStatus}
	seasons, _ := s.deps.TV.ListSeasons(ctx, id)
	if eps, err := s.deps.TV.ListEpisodes(ctx, id); err == nil {
		row.EpisodeCount = len(eps)
	}
	row.SeasonCount = len(seasons)

	var cands []tvCandidateRow
	if persisted, err := s.deps.TV.ShowCandidates(ctx, id); err == nil {
		for _, c := range persisted {
			cands = append(cands, tvCandidateRow{
				Score: c.Score, TMDBID: c.Candidate.TMDBID, Name: c.Candidate.Name,
				Year: c.Candidate.Year, IDMatch: c.IDMatch, PosterURL: c.Candidate.PosterURL,
			})
		}
	}
	return row, cands, nil
}

func (s *Server) tvShowPageData(r *http.Request, id, fallbackTitle string) (tvShowPageData, error) {
	data := tvShowPageData{
		base: base{Title: fallbackTitle, Nav: "tv", BuildVersion: version.Version},
	}
	full, err := s.deps.TV.GetShowFull(r.Context(), id)
	if err != nil {
		return data, err
	}
	row := tvShowRow{ID: full.ID, Title: full.Title, TMDBID: full.TMDBID, AiredStatus: full.AiredStatus,
		ArtworkStatus: full.ArtworkStatus}
	seasons, _ := s.deps.TV.ListSeasons(r.Context(), id)
	itemsByEpisode := map[string]string{} // episode id → media item id
	if refs, err := s.deps.TV.ListEpisodeItems(r.Context(), id); err == nil {
		for _, ref := range refs {
			itemsByEpisode[ref.EpisodeID] = ref.ItemID
		}
	}
	for _, se := range seasons {
		sr := tvSeasonRow{Number: se.Number, Title: se.Title}
		if eps, err := s.deps.TV.ListEpisodes(r.Context(), id); err == nil {
			for _, e := range eps {
				if e.SeasonNumber == se.Number {
					sr.Episodes = append(sr.Episodes, tvEpisodeRow{
						SeasonNumber: e.SeasonNumber, EpisodeNumber: e.EpisodeNumber,
						Title: e.Title, ItemID: itemsByEpisode[e.ID],
					})
				}
			}
		}
		data.Seasons = append(data.Seasons, sr)
	}
	row.EpisodeCount = 0
	for _, sr := range data.Seasons {
		row.EpisodeCount += len(sr.Episodes)
	}
	data.Show = &row
	if cands, err := s.deps.TV.ShowCandidates(r.Context(), id); err == nil {
		for _, c := range cands {
			data.Candidates = append(data.Candidates, tvCandidateRow{
				Score: c.Score, TMDBID: c.Candidate.TMDBID, Name: c.Candidate.Name,
				Year: c.Candidate.Year, IDMatch: c.IDMatch, PosterURL: c.Candidate.PosterURL,
			})
		}
	}
	return data, nil
}

// renderTVShowPanel writes the refreshed TV show page fragment after
// search/match/unmatch actions.
func (s *Server) renderTVShowPanel(w http.ResponseWriter, r *http.Request, status int, id, msg string) {
	sh, err := s.deps.TV.GetShow(r.Context(), id)
	title := id
	if err == nil {
		title = sh.Title
	}
	data, err := s.tvShowPageData(r, id, title)
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	data.Error = msg
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.tpl.pages["tvshow"].ExecuteTemplate(w, "tvshow_panel", data); err != nil {
		s.log.Error("render tvshow panel", "error", err)
	}
}

// renderModalTVCandidates renders the modal candidates fragment for a show
// (list page search flow, HTMX swaps it into #modal-content).
func (s *Server) renderModalTVCandidates(w http.ResponseWriter, r *http.Request, status int, id string) {
	row, cands, err := s.showRowData(r.Context(), id)
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	data := struct {
		Show       tvShowRow
		Candidates []tvCandidateRow
	}{row, cands}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.tpl.pages["tv"].ExecuteTemplate(w, "modal_tv_candidates", data); err != nil {
		s.log.Error("render modal tv candidates", "error", err)
	}
}

// renderTVRow renders the refreshed show row fragment after match/unmatch.
func (s *Server) renderTVRow(w http.ResponseWriter, r *http.Request, status int, id string) {
	row, _, err := s.showRowData(r.Context(), id)
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.tpl.pages["tv"].ExecuteTemplate(w, "tv_row", row); err != nil {
		s.log.Error("render tv row", "error", err)
	}
}

// renderModalTVCandidatesError renders the modal fragment with an inline
// error (search failures keep the dialog open).
func (s *Server) renderModalTVCandidatesError(w http.ResponseWriter, r *http.Request, id, msg string) {
	row, _, err := s.showRowData(r.Context(), id)
	if err != nil {
		writeAPIError(w, http.StatusInternalServerError, "internal server error")
		return
	}
	data := struct {
		Show       tvShowRow
		Candidates []tvCandidateRow
		Error      string
	}{row, nil, msg}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusOK) // fragment mode: swap regardless (see D7)
	if err := s.tpl.pages["tv"].ExecuteTemplate(w, "modal_tv_candidates", data); err != nil {
		s.log.Error("render modal tv candidates error", "error", err)
	}
}
