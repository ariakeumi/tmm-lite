package tv

import (
	"context"
	"fmt"
	"strings"

	"media-manager-lite/internal/matcher"
	"media-manager-lite/internal/task"
	"media-manager-lite/internal/tmdb"
)

// ScrapeService orchestrates the TV matching workflow on top of the store
// and the TMDB service: search, confirm (synchronous, transactional) and
// the async season/episode enrichment task.
type ScrapeService struct {
	store         *Store
	tmdb          *tmdb.Service
	certCountryFn func(ctx context.Context) string
}

// NewScrapeService returns a TV scrape service. certCountryFn resolves the
// certification country setting.
func NewScrapeService(store *Store, tm *tmdb.Service, certCountryFn func(ctx context.Context) string) *ScrapeService {
	return &ScrapeService{store: store, tmdb: tm, certCountryFn: certCountryFn}
}

// SearchShow runs the TMDB show search for a locally scanned show and
// persists the ranked candidates. Results are never auto-confirmed.
func (s *ScrapeService) SearchShow(ctx context.Context, showID string) ([]matcher.TVShowMatchResult, error) {
	sh, err := s.store.GetShow(ctx, showID)
	if err != nil {
		return nil, err
	}
	if strings.TrimSpace(sh.Title) == "" {
		return nil, fmt.Errorf("tv: show has no searchable title")
	}
	results, err := s.tmdb.SearchTVByText(ctx, sh.Title, 0)
	if err != nil {
		return nil, err
	}
	candidates := make([]matcher.TVShowCandidate, 0, len(results))
	for _, sr := range results {
		candidates = append(candidates, matcher.TVShowCandidate{
			TMDBID:       sr.TMDBID,
			Name:         sr.Name,
			OriginalName: sr.OriginalName,
			Year:         sr.Year(),
			Overview:     sr.Overview,
			PosterPath:   sr.PosterPath,
			FirstAirDate: sr.FirstAirDate,
		})
	}
	// Best-effort poster thumbnails for the candidates modal.
	if base, err := s.tmdb.ImageBaseURL(ctx); err == nil {
		for i := range candidates {
			if candidates[i].PosterPath != "" {
				candidates[i].PosterURL = base + tmdb.PosterThumbSize + candidates[i].PosterPath
			}
		}
	}
	ranked := matcher.RankTVShows(matcher.TVShowLocal{Title: sh.Title}, candidates)
	if err := s.store.SaveShowCandidates(ctx, showID, ranked); err != nil {
		return nil, err
	}
	return ranked, nil
}

// MatchShow confirms a show match synchronously: one TMDB detail call,
// then the transactional show-row update. Season/episode enrichment is the
// caller's follow-up (EnrichEpisodes, typically via the task queue).
func (s *ScrapeService) MatchShow(ctx context.Context, showID string, tmdbID int) error {
	res, err := s.tmdb.TVDetailWithFallback(ctx, tmdbID)
	if err != nil {
		return err
	}
	cert := "US"
	if s.certCountryFn != nil {
		cert = s.certCountryFn(ctx)
	}
	return s.store.ConfirmShowMatch(ctx, showID, res, cert)
}

// UnmatchShow clears a confirmed match.
func (s *ScrapeService) UnmatchShow(ctx context.Context, showID string) error {
	return s.store.MarkShowUnmatched(ctx, showID)
}

// EnrichEpisodes fetches every season of a matched show (requested
// language with EPISODE_TRANS fallback merge) and updates the episode rows.
// When the show carries an absolute-order episode group, scanned
// season-1 episodes are mapped through the group (Phase 0 §3.2 item 4)
// instead of direct S/E matching; normal S/E shows behave unchanged.
// Returns how many episodes were updated.
func (s *ScrapeService) EnrichEpisodes(ctx context.Context, showID string) (int, error) {
	sh, err := s.store.GetShowFull(ctx, showID)
	if err != nil {
		return 0, err
	}
	if sh.TMDBID <= 0 {
		return 0, ErrNotMatched
	}
	detail, err := s.tmdb.TVDetailWithFallback(ctx, sh.TMDBID)
	if err != nil {
		return 0, err
	}

	// Absolute-order group mapping (Phase 0 §3.2 item 4): order+1 → entry.
	absOrder := map[int]tmdb.EpisodeGroupEntry{}
	absolute := false
	if len(detail.Detail.EpisodeGroups.Results) > 0 {
		client, cerr := s.tmdb.Client(ctx)
		if cerr != nil {
			return 0, cerr
		}
		if group, err := client.EpisodeGroup(ctx, detail.Detail.EpisodeGroups.Results[0].ID); err == nil {
			for _, g := range group.Groups {
				if g.Type == 2 {
					absolute = true
					for _, e := range g.Episodes {
						absOrder[e.Order+1] = e
					}
					break
				}
			}
		}
	}

	// Fetch all TMDB seasons (1..numberOfSeasons), indexed by id and S/E.
	bySE := map[string]tmdb.Episode{}
	byID := map[int]tmdb.Episode{}
	seasonNames := map[int]string{}
	for n := 1; n <= detail.Detail.NumberOfSeasons; n++ {
		sd, err := s.tmdb.TVSeasonWithFallback(ctx, sh.TMDBID, n)
		if err != nil {
			continue // announced but unaired seasons are not fatal
		}
		seasonNames[n] = sd.Name
		for _, e := range sd.Episodes {
			bySE[fmt.Sprintf("%d:%d", e.SeasonNumber, e.EpisodeNumber)] = e
			byID[e.TMDBID] = e
		}
	}

	rows, err := s.store.EpisodeRows(ctx, showID)
	if err != nil {
		return 0, err
	}
	total := 0
	for _, row := range rows {
		tmdbEp, found := bySE[fmt.Sprintf("%d:%d", row.Season, row.Episode)]
		if !found && absolute && row.Season == 1 {
			// Absolute-number mapping: scanned S1E45 → group order 45.
			if entry, ok := absOrder[row.Episode]; ok {
				if e, ok := byID[entry.ID]; ok {
					tmdbEp, found = e, true
				}
			}
		}
		if !found {
			continue
		}
		if err := s.store.EnrichEpisodeRow(ctx, row, tmdbEp); err != nil {
			return total, err
		}
		total++
	}
	for n, name := range seasonNames {
		if err := s.store.UpdateSeasonTitle(ctx, showID, n, name); err != nil {
			return total, err
		}
	}
	return total, nil
}

// EnrichHandler adapts EnrichEpisodes into a task handler.

// EnrichHandler adapts EnrichEpisodes into a task handler.
func EnrichHandler(store *Store, svc *ScrapeService) task.Handler {
	return func(ctx context.Context, _ /*targetType*/, showID string) (string, error) {
		sh, err := store.GetShow(ctx, showID)
		if err != nil {
			return "", err
		}
		n, err := svc.EnrichEpisodes(ctx, showID)
		if err != nil {
			return "", err
		}
		return fmt.Sprintf("enriched %d episode(s) of %q", n, sh.Title), nil
	}
}
