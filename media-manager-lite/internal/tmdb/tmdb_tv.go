package tmdb

import (
	"context"
	"fmt"
	"net/url"
	"strings"
)

// --- TV models (Phase 0 §3.2 endpoint map) ---

// TVSearchResult is one entry of a /search/tv response.
type TVSearchResult struct {
	TMDBID           int     `json:"id"`
	Name             string  `json:"name"`
	OriginalName     string  `json:"original_name"`
	FirstAirDate     string  `json:"first_air_date,omitempty"`
	Overview         string  `json:"overview,omitempty"`
	PosterPath       string  `json:"poster_path,omitempty"`
	VoteAverage      float64 `json:"vote_average"`
	VoteCount        int     `json:"vote_count"`
	OriginalLanguage string  `json:"original_language,omitempty"`
}

// Year extracts the first-air year (0 when unknown).
func (r TVSearchResult) Year() int {
	if len(r.FirstAirDate) >= 4 {
		var y int
		if _, err := fmt.Sscanf(r.FirstAirDate[:4], "%d", &y); err == nil {
			return y
		}
	}
	return 0
}

type tvSearchResponse struct {
	Page       int              `json:"page"`
	Results    []TVSearchResult `json:"results"`
	TotalPages int              `json:"total_pages"`
}

// ContentRating is one country's age rating.
type ContentRating struct {
	ISO3166_1 string `json:"iso_3166_1"`
	Rating    string `json:"rating"`
}

// SeasonSummary is a season entry of the TV detail response.
type SeasonSummary struct {
	TMDBID       int    `json:"id"`
	Name         string `json:"name"`
	SeasonNumber int    `json:"season_number"`
	EpisodeCount int    `json:"episode_count"`
	AirDate      string `json:"air_date,omitempty"`
	PosterPath   string `json:"poster_path,omitempty"`
}

// EpisodeGroupSummary is one episode group listed on the show.
type EpisodeGroupSummary struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// TVDetail mirrors GET /tv/{id} with the M7 append_to_response set.
type TVDetail struct {
	TMDBID           int     `json:"id"`
	Name             string  `json:"name"`
	OriginalName     string  `json:"original_name"`
	Overview         string  `json:"overview"`
	FirstAirDate     string  `json:"first_air_date"`
	LastAirDate      string  `json:"last_air_date"`
	Status           string  `json:"status"`
	VoteAverage      float64 `json:"vote_average"`
	VoteCount        int     `json:"vote_count"`
	PosterPath       string  `json:"poster_path,omitempty"`
	OriginalLanguage string  `json:"original_language"`
	NumberOfSeasons  int     `json:"number_of_seasons"`
	NumberOfEpisodes int     `json:"number_of_episodes"`
	Genres           []Genre `json:"genres"`

	ProductionCompanies []Company `json:"production_companies"`

	Credits struct {
		Cast []CastMember `json:"cast"`
		Crew []CrewMember `json:"crew"`
	} `json:"credits"`

	ContentRatings struct {
		Results []ContentRating `json:"results"`
	} `json:"content_ratings"`

	Translations struct {
		Translations []Translation `json:"translations"`
	} `json:"translations"`

	EpisodeGroups struct {
		Results []EpisodeGroupSummary `json:"results"`
	} `json:"episode_groups"`

	ExternalIDs struct {
		IMDBID string `json:"imdb_id"`
	} `json:"external_ids"`
}

// Certification returns the content rating for a country.
func (d *TVDetail) Certification(country string) string {
	for _, r := range d.ContentRatings.Results {
		if strings.EqualFold(r.ISO3166_1, country) && r.Rating != "" {
			return r.Rating
		}
	}
	return ""
}

// Episode is one episode of a season detail response.
type Episode struct {
	TMDBID        int     `json:"id"`
	Name          string  `json:"name"`
	Overview      string  `json:"overview"`
	SeasonNumber  int     `json:"season_number"`
	EpisodeNumber int     `json:"episode_number"`
	AirDate       string  `json:"air_date,omitempty"`
	Runtime       int     `json:"runtime"`
	VoteAverage   float64 `json:"vote_average"`
	VoteCount     int     `json:"vote_count"`
}

// SeasonDetail mirrors GET /tv/{id}/season/{n}.
type SeasonDetail struct {
	TMDBID       int       `json:"id"`
	Name         string    `json:"name"`
	Overview     string    `json:"overview"`
	SeasonNumber int       `json:"season_number"`
	AirDate      string    `json:"air_date,omitempty"`
	Episodes     []Episode `json:"episodes"`
}

// EpisodeGroupPlacement maps one TMDB episode id to its group ordering.
type EpisodeGroupPlacement struct {
	SeasonNumber  int `json:"seasonNumber"`
	EpisodeNumber int `json:"episodeNumber"`
	Order         int `json:"order"`
}

// EpisodeGroupEntry is one episode inside an episode group: the TMDB
// episode id with its REAL season/episode placement and the group order
// (0-based; consumers normalize to 1-based for absolute numbering).
type EpisodeGroupEntry struct {
	ID            int `json:"id"`
	SeasonNumber  int `json:"season_number"`
	EpisodeNumber int `json:"episode_number"`
	Order         int `json:"order"`
}

// EpisodeGroupDetail mirrors GET /tv/episode_group/{id}.
type EpisodeGroupDetail struct {
	ID     string `json:"id"`
	Name   string `json:"name"`
	Groups []struct {
		Type     int                 `json:"type"`
		Order    int                 `json:"order"`
		Episodes []EpisodeGroupEntry `json:"episodes"`
	} `json:"groups"`
}

// Mapping returns the episode-id → placement map of the group, with the
// order normalized to 1-based (TMDB counts group order from zero) and the
// group type recorded per Phase 0 (2 = absolute, 3 = DVD, others alternate).
func (d *EpisodeGroupDetail) Mapping() map[int]EpisodeGroupPlacement {
	out := make(map[int]EpisodeGroupPlacement)
	for _, g := range d.Groups {
		for _, e := range g.Episodes {
			out[e.ID] = EpisodeGroupPlacement{
				SeasonNumber:  e.SeasonNumber,
				EpisodeNumber: e.EpisodeNumber,
				Order:         e.Order + 1, // TMDB counts group episodes from zero
			}
		}
	}
	return out
}

// --- client methods ---

// SearchTV queries /search/tv, following up to 5 result pages. year>0
// passes first_air_date_year.
func (c *Client) SearchTV(ctx context.Context, query, language string, year int) ([]TVSearchResult, error) {
	var out []TVSearchResult
	for page := 1; page <= searchMaxPages; page++ {
		q := url.Values{}
		q.Set("query", query)
		if language != "" {
			q.Set("language", language)
		}
		if year > 0 {
			q.Set("first_air_date_year", fmt.Sprintf("%d", year))
		}
		q.Set("page", fmt.Sprintf("%d", page))

		var resp tvSearchResponse
		if err := c.do(ctx, "/search/tv", q, &resp); err != nil {
			return nil, err
		}
		out = append(out, resp.Results...)
		if page >= resp.TotalPages || len(resp.Results) == 0 {
			break
		}
	}
	return out, nil
}

// TVDetail fetches GET /tv/{id} with translations, credits, external_ids,
// content_ratings and episode_groups appended.
func (c *Client) TVDetail(ctx context.Context, tmdbID int, language string) (*TVDetail, error) {
	q := url.Values{}
	if language != "" {
		q.Set("language", language)
	}
	q.Set("append_to_response", "translations,credits,external_ids,content_ratings,episode_groups")
	var d TVDetail
	if err := c.do(ctx, fmt.Sprintf("/tv/%d", tmdbID), q, &d); err != nil {
		return nil, err
	}
	return &d, nil
}

// TVSeason fetches GET /tv/{id}/season/{n}.
func (c *Client) TVSeason(ctx context.Context, tmdbID, season int, language string) (*SeasonDetail, error) {
	q := url.Values{}
	if language != "" {
		q.Set("language", language)
	}
	var d SeasonDetail
	if err := c.do(ctx, fmt.Sprintf("/tv/%d/season/%d", tmdbID, season), q, &d); err != nil {
		return nil, err
	}
	return &d, nil
}

// TVEpisode fetches GET /tv/{id}/season/{n}/episode/{e} with external ids
// and credits appended; stills are fetched in every language
// (include_image_language=null, Phase 0).
func (c *Client) TVEpisode(ctx context.Context, tmdbID, season, episode int, language string) (*Episode, error) {
	q := url.Values{}
	if language != "" {
		q.Set("language", language)
	}
	q.Set("append_to_response", "external_ids,credits")
	var d Episode
	if err := c.do(ctx, fmt.Sprintf("/tv/%d/season/%d/episode/%d", tmdbID, season, episode), q, &d); err != nil {
		return nil, err
	}
	return &d, nil
}

// EpisodeGroup fetches GET /tv/episode_group/{id}.
func (c *Client) EpisodeGroup(ctx context.Context, groupID string) (*EpisodeGroupDetail, error) {
	var d EpisodeGroupDetail
	if err := c.do(ctx, "/tv/episode_group/"+url.PathEscape(groupID), url.Values{}, &d); err != nil {
		return nil, err
	}
	return &d, nil
}

// PickBestTVTitle applies the Phase-0 translation fallback chain to a TV
// detail (same tiers as the movie variant).
func PickBestTVTitle(d *TVDetail, requested, fallback string) BestTitle {
	// The chain operates on the translations block plus fallback texts.
	carrier := struct {
		Title    string
		Overview string
		Tagline  string
		T        []Translation
	}{d.Name, d.Overview, "", d.Translations.Translations}
	return pickBest(carrier.T, carrier.Title, carrier.Overview, carrier.Tagline, requested, fallback)
}

// TVImages fetches GET /tv/{id}/images (all languages, ranked client-side
// by internal/artwork like the movie variant).
func (c *Client) TVImages(ctx context.Context, tmdbID int) (posters, backdrops []Image, err error) {
	var resp struct {
		Posters   []Image `json:"posters"`
		Backdrops []Image `json:"backdrops"`
	}
	if err := c.do(ctx, fmt.Sprintf("/tv/%d/images", tmdbID), url.Values{}, &resp); err != nil {
		return nil, nil, err
	}
	return resp.Posters, resp.Backdrops, nil
}

// TVSeasonImages fetches GET /tv/{id}/season/{n}/images (season posters).
func (c *Client) TVSeasonImages(ctx context.Context, tmdbID, season int) ([]Image, error) {
	var resp struct {
		Posters []Image `json:"posters"`
	}
	if err := c.do(ctx, fmt.Sprintf("/tv/%d/season/%d/images", tmdbID, season), url.Values{}, &resp); err != nil {
		return nil, err
	}
	return resp.Posters, nil
}
