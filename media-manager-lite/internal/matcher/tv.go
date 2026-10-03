package matcher

import "sort"

// TVShowLocal is the local side of a show match.
type TVShowLocal struct {
	Title  string
	Year   int
	TMDBID int
}

// TVShowCandidate is one /search/tv result to score.
type TVShowCandidate struct {
	TMDBID       int    `json:"tmdbId"`
	Name         string `json:"name"`
	OriginalName string `json:"originalName,omitempty"`
	Year         int    `json:"year,omitempty"`
	Overview     string `json:"overview,omitempty"`
	PosterPath   string `json:"posterPath,omitempty"`
	PosterURL    string `json:"posterUrl,omitempty"`
	FirstAirDate string `json:"firstAirDate,omitempty"`
}

// TVShowMatchResult pairs a show candidate with its score.
type TVShowMatchResult struct {
	Score     float64         `json:"score"`
	IDMatch   bool            `json:"idMatch"`
	Candidate TVShowCandidate `json:"candidate"`
}

// ScoreTVShow follows the Phase-0 movie formula: id hit = 1.0, otherwise
// max similarity over name and original name minus year/poster penalties.
func ScoreTVShow(local TVShowLocal, c TVShowCandidate) float64 {
	if local.TMDBID != 0 && c.TMDBID == local.TMDBID {
		return 1.0
	}
	movieLocal := Local{Title: local.Title, Year: local.Year, TMDBID: local.TMDBID}
	movieCand := Candidate{TMDBID: c.TMDBID, Title: c.Name, OriginalTitle: c.OriginalName,
		Year: c.Year, PosterPath: c.PosterPath}
	return Score(movieLocal, movieCand)
}

// RankTVShows sorts show candidates by score desc (ties: year, id).
func RankTVShows(local TVShowLocal, candidates []TVShowCandidate) []TVShowMatchResult {
	out := make([]TVShowMatchResult, 0, len(candidates))
	for _, c := range candidates {
		out = append(out, TVShowMatchResult{
			Score:     ScoreTVShow(local, c),
			IDMatch:   local.TMDBID != 0 && c.TMDBID == local.TMDBID,
			Candidate: c,
		})
	}
	sort.SliceStable(out, func(i, j int) bool {
		if out[i].Score != out[j].Score {
			return out[i].Score > out[j].Score
		}
		if out[i].Candidate.Year != out[j].Candidate.Year {
			return out[i].Candidate.Year < out[j].Candidate.Year
		}
		return out[i].Candidate.TMDBID < out[j].Candidate.TMDBID
	})
	return out
}

// TVEpisodeLocal is the local side of an episode match.
type TVEpisodeLocal struct {
	Season  int
	Episode int
	Title   string
	TMDBID  int
}

// TVEpisodeCandidate is one TMDB episode to match against.
type TVEpisodeCandidate struct {
	TMDBID   int    `json:"tmdbId"`
	Season   int    `json:"season"`
	Episode  int    `json:"episode"`
	Name     string `json:"name"`
	Overview string `json:"overview,omitempty"`
}

// TVEpisodeMatchResult pairs an episode candidate with its score.
type TVEpisodeMatchResult struct {
	Score     float64            `json:"score"`
	NumberHit bool               `json:"numberHit"`
	Candidate TVEpisodeCandidate `json:"candidate"`
}

// ScoreTVEpisode follows Phase 0: season/episode numbers dominate — an
// exact number hit scores 1.0 (a TMDB id hit likewise); without a number
// hit only title similarity (half weight) applies, so number matches always
// outrank title-only matches.
func ScoreTVEpisode(local TVEpisodeLocal, c TVEpisodeCandidate) float64 {
	if local.TMDBID != 0 && c.TMDBID == local.TMDBID {
		return 1.0
	}
	if local.Season == c.Season && local.Episode == c.Episode {
		return 1.0
	}
	if local.Title == "" || c.Name == "" {
		return 0
	}
	return 0.5 * calculateScore(local.Title, c.Name)
}

// RankTVEpisodes sorts episode candidates by score desc (ties: season,
// episode, id).
func RankTVEpisodes(local TVEpisodeLocal, candidates []TVEpisodeCandidate) []TVEpisodeMatchResult {
	out := make([]TVEpisodeMatchResult, 0, len(candidates))
	for _, c := range candidates {
		out = append(out, TVEpisodeMatchResult{
			Score:     ScoreTVEpisode(local, c),
			NumberHit: local.Season == c.Season && local.Episode == c.Episode,
			Candidate: c,
		})
	}
	sort.SliceStable(out, func(i, j int) bool {
		if out[i].Score != out[j].Score {
			return out[i].Score > out[j].Score
		}
		if out[i].Candidate.Season != out[j].Candidate.Season {
			return out[i].Candidate.Season < out[j].Candidate.Season
		}
		if out[i].Candidate.Episode != out[j].Candidate.Episode {
			return out[i].Candidate.Episode < out[j].Candidate.Episode
		}
		return out[i].Candidate.TMDBID < out[j].Candidate.TMDBID
	})
	return out
}
