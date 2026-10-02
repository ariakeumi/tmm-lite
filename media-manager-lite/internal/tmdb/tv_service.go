package tmdb

import "context"

// SearchTVByText searches TV shows by title, retrying without the year
// filter when the strict search comes up empty (Phase 0 search cascade).
func (s *Service) SearchTVByText(ctx context.Context, title string, year int) ([]TVSearchResult, error) {
	c, err := s.Client(ctx)
	if err != nil {
		return nil, err
	}
	lang, err := s.Language(ctx)
	if err != nil {
		return nil, err
	}
	results, err := c.SearchTV(ctx, title, lang, year)
	if err != nil {
		return nil, err
	}
	if len(results) == 0 && year > 0 {
		results, err = c.SearchTV(ctx, title, lang, 0)
		if err != nil {
			return nil, err
		}
	}
	return results, nil
}

// TVDetailResult bundles the raw TV detail with the fallback-applied title.
type TVDetailResult struct {
	Detail   *TVDetail
	Best     BestTitle
	Language string
}

// TVDetailWithFallback fetches a show and applies the translation fallback
// chain (requested → loose → fallback language → original → English).
func (s *Service) TVDetailWithFallback(ctx context.Context, tmdbID int) (*TVDetailResult, error) {
	c, err := s.Client(ctx)
	if err != nil {
		return nil, err
	}
	lang, err := s.Language(ctx)
	if err != nil {
		return nil, err
	}
	fb, err := s.FallbackLanguage(ctx)
	if err != nil {
		return nil, err
	}
	d, err := c.TVDetail(ctx, tmdbID, lang)
	if err != nil {
		return nil, err
	}
	return &TVDetailResult{
		Detail:   d,
		Best:     PickBestTVTitle(d, lang, fb),
		Language: lang,
	}, nil
}

// TVSeasonWithFallback fetches a season in the requested language and
// replaces generic machine-translated episode names (EPISODE_TRANS) with
// the fallback-language names, mirroring TMM's multi-language episode list
// merge (simplified to two tiers: requested + fallback).
func (s *Service) TVSeasonWithFallback(ctx context.Context, tmdbID, season int) (*SeasonDetail, error) {
	c, err := s.Client(ctx)
	if err != nil {
		return nil, err
	}
	lang, err := s.Language(ctx)
	if err != nil {
		return nil, err
	}
	fb, err := s.FallbackLanguage(ctx)
	if err != nil {
		return nil, err
	}

	d, err := c.TVSeason(ctx, tmdbID, season, lang)
	if err != nil {
		return nil, err
	}

	generic := false
	for _, e := range d.Episodes {
		if IsGenericEpisodeTitle(e.Name) {
			generic = true
			break
		}
	}
	if generic && lang != fb {
		fbSeason, err := c.TVSeason(ctx, tmdbID, season, fb)
		if err != nil {
			return d, nil // fallback unavailable: keep the requested version
		}
		byID := make(map[int]Episode, len(fbSeason.Episodes))
		for _, fe := range fbSeason.Episodes {
			byID[fe.TMDBID] = fe
		}
		for i := range d.Episodes {
			if !IsGenericEpisodeTitle(d.Episodes[i].Name) {
				continue
			}
			if fbEp, ok := byID[d.Episodes[i].TMDBID]; ok && !IsGenericEpisodeTitle(fbEp.Name) {
				if fbEp.Name != "" {
					d.Episodes[i].Name = fbEp.Name
				}
				if d.Episodes[i].Overview == "" && fbEp.Overview != "" {
					d.Episodes[i].Overview = fbEp.Overview
				}
			}
		}
	}

	// Season names missing in the requested language fall back too.
	if IsGenericEpisodeTitle(d.Name) && lang != fb {
		if fbSeason, err := c.TVSeason(ctx, tmdbID, season, fb); err == nil && !IsGenericEpisodeTitle(fbSeason.Name) {
			d.Name = fbSeason.Name
		}
	}
	return d, nil
}
