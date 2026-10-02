// Package tmdb implements a focused TMDB API v3 client: configuration,
// movie search, movie details (with translation fallback) and external-id
// lookup. Requests carry the user's own API key; keys never appear in logs
// or error strings.
package tmdb

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Errors are distinguished so the transport layer can map them to precise
// HTTP statuses and messages.
var (
	ErrNotConfigured = errors.New("tmdb: API key not configured")
	ErrUnauthorized  = errors.New("tmdb: invalid API key")
	ErrNotFound      = errors.New("tmdb: not found")
	ErrRateLimited   = errors.New("tmdb: rate limited")
	ErrUnavailable   = errors.New("tmdb: service unavailable")
)

// Default endpoints and sizes (Phase 0 §3.2).
const (
	DefaultBaseURL = "https://api.themoviedb.org/3"
	PosterSize     = "w342"
	searchMaxPages = 5
	requestTimeout = 30 * time.Second
)

// Client issues authenticated TMDB requests with bounded retry behavior
// and a short-TTL response cache (Phase 0 §3.2: 429 Retry-After handling,
// 15-minute cache semantics), without letting one TMDB outage stall a task
// indefinitely (hard caps everywhere).
type Client struct {
	apiKey  string
	baseURL string
	http    *http.Client

	cacheMu sync.Mutex
	cache   map[string]cacheEntry
	now     func() time.Time // injectable for tests
}

const (
	// maxRetries bounds the total attempts per request (initial + retries).
	maxRetries = 3
	// maxRetryAfter caps an honored Retry-After (TMM aborts beyond 30s).
	maxRetryAfter = 30 * time.Second
	// backoffBase is the exponential backoff for 5xx responses.
	backoffBase = 1 * time.Second
	// responseCacheTTL mirrors TMM's forced 15-minute TMDB response cache.
	responseCacheTTL = 15 * time.Minute
	// maxCacheEntries bounds memory; the cache is a nice-to-have.
	maxCacheEntries = 512
)

type cacheEntry struct {
	body      []byte
	expiresAt time.Time
}

// NewClient returns a client for the given user-supplied API key.
func NewClient(apiKey, baseURL string) *Client {
	return &Client{
		apiKey:  apiKey,
		baseURL: strings.TrimRight(baseURL, "/"),
		http:    &http.Client{Timeout: requestTimeout},
		cache:   map[string]cacheEntry{},
		now:     time.Now,
	}
}

// sanitizedError redacts the API key from an error message while keeping
// the original error reachable via errors.Is/As (context cancellation, ...).
type sanitizedError struct {
	msg string
	err error
}

func (e *sanitizedError) Error() string { return e.msg }
func (e *sanitizedError) Unwrap() error { return e.err }

// do performs a GET and decodes the JSON response into v.
// Error strings never contain the API key: URLs are redacted first.
func (c *Client) do(ctx context.Context, path string, query url.Values, v any) error {
	// The cache key excludes the API key (one key per instance anyway) and
	// is bounded so a long-running process cannot grow it unbounded.
	cacheKey := path + "?" + query.Encode()

	if body, ok := c.cached(cacheKey); ok {
		return json.Unmarshal(body, v)
	}

	var lastErr error
	wait := time.Duration(0)
	for attempt := 0; attempt < maxRetries; attempt++ {
		if wait > 0 {
			// Sleep between attempts unless the context is done (a canceled
			// task must never wait on backoff).
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(wait):
			}
		}
		body, nextWait, err := c.attempt(ctx, path, query)
		if err == nil {
			c.store(cacheKey, body)
			return json.Unmarshal(body, v)
		}
		lastErr = err
		if nextWait == noRetry {
			return err // unrecoverable (4xx): never retry
		}
		// Exponential backoff for retryable errors without a server hint.
		if nextWait == 0 {
			nextWait = backoffBase << attempt
		}
		wait = nextWait
	}
	return lastErr
}

// noRetry marks an unrecoverable failure (do not attempt again).
const noRetry time.Duration = -1

// attempt performs one HTTP round trip and returns the body, the wait
// before the next attempt (noRetry when the error is unrecoverable) and
// an error for non-2xx responses.
func (c *Client) attempt(ctx context.Context, path string, query url.Values) ([]byte, time.Duration, error) {
	query.Set("api_key", c.apiKey)
	u := c.baseURL + path + "?" + query.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return nil, noRetry, fmt.Errorf("tmdb: build request: %w", err)
	}
	req.Header.Set("Accept", "application/json")

	resp, err := c.http.Do(req)
	if err != nil {
		msg := redact(fmt.Sprintf("tmdb: %s: %v", redact(u, c.apiKey), err), c.apiKey)
		// Network errors are retryable with exponential backoff, but a
		// canceled context is not.
		if ctx.Err() != nil {
			return nil, noRetry, &sanitizedError{msg: msg, err: err}
		}
		return nil, 0, &sanitizedError{msg: msg, err: err} // exponential backoff
	}
	defer resp.Body.Close()

	switch resp.StatusCode {
	case http.StatusOK:
		body, err := io.ReadAll(io.LimitReader(resp.Body, 8<<20))
		if err != nil {
			return nil, 0, fmt.Errorf("tmdb: read response: %w", err)
		}
		return body, 0, nil
	case http.StatusUnauthorized:
		return nil, noRetry, ErrUnauthorized
	case http.StatusNotFound:
		return nil, noRetry, ErrNotFound
	case http.StatusTooManyRequests:
		wait := retryAfterDuration(resp.Header.Get("Retry-After"))
		if wait > maxRetryAfter {
			// Server asks for too long: give up like TMM does.
			return nil, noRetry, ErrRateLimited
		}
		return nil, wait, ErrRateLimited
	default:
		if resp.StatusCode >= 500 {
			return nil, 0, fmt.Errorf("tmdb: %s: unexpected status %d",
				redact(u, c.apiKey), resp.StatusCode)
		}
		// Other 4xx are client errors: surface immediately.
		return nil, noRetry, fmt.Errorf("tmdb: %s: unexpected status %d",
			redact(u, c.apiKey), resp.StatusCode)
	}
}

// retryAfterDuration parses a Retry-After header (seconds); unknown
// formats fall back to a small default.
func retryAfterDuration(v string) time.Duration {
	v = strings.TrimSpace(v)
	if v == "" {
		return backoffBase
	}
	if n, err := strconv.Atoi(v); err == nil && n >= 0 {
		return time.Duration(n) * time.Second
	}
	return backoffBase
}

func (c *Client) cached(key string) ([]byte, bool) {
	c.cacheMu.Lock()
	defer c.cacheMu.Unlock()
	e, ok := c.cache[key]
	if !ok {
		return nil, false
	}
	if c.now().After(e.expiresAt) {
		delete(c.cache, key)
		return nil, false
	}
	return e.body, true
}

func (c *Client) store(key string, body []byte) {
	c.cacheMu.Lock()
	defer c.cacheMu.Unlock()
	if len(c.cache) >= maxCacheEntries {
		// Cheap eviction: drop everything past half its budget.
		for k := range c.cache {
			delete(c.cache, k)
			if len(c.cache) < maxCacheEntries/2 {
				break
			}
		}
	}
	c.cache[key] = cacheEntry{body: body, expiresAt: c.now().Add(responseCacheTTL)}
}

// redact removes the API key from a URL for safe inclusion in errors.
func redact(raw, key string) string {
	if key == "" {
		return raw
	}
	return strings.ReplaceAll(raw, "api_key="+url.QueryEscape(key), "api_key=REDACTED")
}

// --- API models (subset) ---

// Configuration mirrors GET /configuration.
type Configuration struct {
	Images struct {
		SecureBaseURL string `json:"secure_base_url"`
	} `json:"images"`
}

// SearchResult is one entry of a movie search (also used by /find).
type SearchResult struct {
	TMDBID           int     `json:"id"`
	Title            string  `json:"title"`
	OriginalTitle    string  `json:"original_title"`
	ReleaseDate      string  `json:"release_date,omitempty"`
	Overview         string  `json:"overview,omitempty"`
	PosterPath       string  `json:"poster_path,omitempty"`
	VoteAverage      float64 `json:"vote_average"`
	VoteCount        int     `json:"vote_count"`
	OriginalLanguage string  `json:"original_language,omitempty"`
}

// Year extracts the release year (0 when unknown).
func (r SearchResult) Year() int {
	if len(r.ReleaseDate) >= 4 {
		var y int
		if _, err := fmt.Sscanf(r.ReleaseDate[:4], "%d", &y); err == nil {
			return y
		}
	}
	return 0
}

type searchResponse struct {
	Page         int            `json:"page"`
	Results      []SearchResult `json:"results"`
	TotalPages   int            `json:"total_pages"`
	TotalResults int            `json:"total_results"`
}

// Genre is a TMDB genre entry.
type Genre struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

// Company is a production company entry.
type Company struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

// Collection is the movie set a film belongs to.
type Collection struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
}

// Image is one artwork entry of the /movie/{id}/images endpoint.
type Image struct {
	FilePath    string  `json:"file_path"`
	VoteAverage float64 `json:"vote_average"`
	VoteCount   int     `json:"vote_count"`
	Width       int     `json:"width"`
	Height      int     `json:"height"`
	Language    string  `json:"iso_639_1,omitempty"`
}

// CastMember is one credited actor.
type CastMember struct {
	TMDBID      int    `json:"id"`
	Name        string `json:"name"`
	Character   string `json:"character,omitempty"`
	ProfilePath string `json:"profile_path,omitempty"`
}

// CrewMember is one crew entry.
type CrewMember struct {
	TMDBID     int    `json:"id"`
	Name       string `json:"name"`
	Job        string `json:"job,omitempty"`
	Department string `json:"department,omitempty"`
}

// Translation carries per-locale title/overview data from the
// append_to_response=translations block.
type Translation struct {
	ISO639_1  string `json:"iso_639_1"`
	ISO3166_1 string `json:"iso_3166_1"`
	Name      string `json:"name,omitempty"`
	Data      struct {
		Title    string `json:"title,omitempty"`
		Overview string `json:"overview,omitempty"`
		Tagline  string `json:"tagline,omitempty"`
	} `json:"data"`
}

type releaseDatesResponse struct {
	Results []struct {
		ISO3166_1    string `json:"iso_3166_1"`
		ReleaseDates []struct {
			Certification string `json:"certification"`
			Type          int    `json:"type"`
			Date          string `json:"release_date"`
		} `json:"release_dates"`
	} `json:"results"`
}

// MovieDetail mirrors GET /movie/{id} with the M3 append_to_response set.
type MovieDetail struct {
	TMDBID           int     `json:"id"`
	Title            string  `json:"title"`
	OriginalTitle    string  `json:"original_title"`
	Overview         string  `json:"overview"`
	Tagline          string  `json:"tagline"`
	ReleaseDate      string  `json:"release_date"`
	Runtime          int     `json:"runtime"`
	VoteAverage      float64 `json:"vote_average"`
	VoteCount        int     `json:"vote_count"`
	PosterPath       string  `json:"poster_path,omitempty"`
	OriginalLanguage string  `json:"original_language"`
	Status           string  `json:"status"`
	Genres           []Genre `json:"genres"`

	BelongsToCollection *Collection `json:"belongs_to_collection"`
	ProductionCompanies []Company   `json:"production_companies"`

	Credits struct {
		Cast []CastMember `json:"cast"`
		Crew []CrewMember `json:"crew"`
	} `json:"credits"`

	ReleaseDates releaseDatesResponse `json:"release_dates"`

	Translations struct {
		Translations []Translation `json:"translations"`
	} `json:"translations"`

	ExternalIDs struct {
		IMDBID string `json:"imdb_id"`
	} `json:"external_ids"`
}

// BestTitle is the outcome of the Phase-0 translation fallback chain.
type BestTitle struct {
	Title        string
	Overview     string
	Tagline      string
	EnglishTitle string
	Source       string // "exact" | "loose" | "fallback" | "original" | "english"
}

// Configuration fetches and validates the API configuration; a 401 maps to
// ErrUnauthorized so callers can prompt for a correct key.
func (c *Client) Configuration(ctx context.Context) (*Configuration, error) {
	var cfg Configuration
	if err := c.do(ctx, "/configuration", url.Values{}, &cfg); err != nil {
		return nil, err
	}
	return &cfg, nil
}

// SearchMovie queries /search/movie, following up to 5 result pages like
// tinyMediaManager. year>0 passes the year filter; language is a full tag
// such as zh-CN (see ExpandLanguage).
func (c *Client) SearchMovie(ctx context.Context, query, language string, year int) ([]SearchResult, error) {
	var out []SearchResult
	for page := 1; page <= searchMaxPages; page++ {
		q := url.Values{}
		q.Set("query", query)
		if language != "" {
			q.Set("language", language)
		}
		if year > 0 {
			q.Set("year", fmt.Sprintf("%d", year))
		}
		q.Set("page", fmt.Sprintf("%d", page))

		var resp searchResponse
		if err := c.do(ctx, "/search/movie", q, &resp); err != nil {
			return nil, err
		}
		out = append(out, resp.Results...)
		if page >= resp.TotalPages || len(resp.Results) == 0 {
			break
		}
	}
	return out, nil
}

// MovieDetail fetches GET /movie/{id} with translations, credits,
// release_dates and external_ids appended.
func (c *Client) MovieDetail(ctx context.Context, tmdbID int, language string) (*MovieDetail, error) {
	q := url.Values{}
	if language != "" {
		q.Set("language", language)
	}
	q.Set("append_to_response", "translations,credits,release_dates,external_ids")
	var d MovieDetail
	if err := c.do(ctx, fmt.Sprintf("/movie/%d", tmdbID), q, &d); err != nil {
		return nil, err
	}
	return &d, nil
}

// MovieImages fetches GET /movie/{id}/images. Per the Phase-0 behavior the
// language parameter is deliberately NOT sent: all languages are returned
// and ranked client-side (internal/artwork).
func (c *Client) MovieImages(ctx context.Context, tmdbID int) (posters, backdrops []Image, err error) {
	var resp struct {
		Posters   []Image `json:"posters"`
		Backdrops []Image `json:"backdrops"`
	}
	if err := c.do(ctx, fmt.Sprintf("/movie/%d/images", tmdbID), url.Values{}, &resp); err != nil {
		return nil, nil, err
	}
	return resp.Posters, resp.Backdrops, nil
}

// Find resolves an external id (e.g. imdb_id) to TMDB search results.
func (c *Client) Find(ctx context.Context, externalID, source, language string) ([]SearchResult, error) {
	q := url.Values{}
	q.Set("external_source", source)
	if language != "" {
		q.Set("language", language)
	}
	var resp struct {
		MovieResults []SearchResult `json:"movie_results"`
	}
	if err := c.do(ctx, "/find/"+url.PathEscape(externalID), q, &resp); err != nil {
		return nil, err
	}
	return resp.MovieResults, nil
}

// --- Language + translation fallback (Phase 0 §3.2) ---

// defaultCountry expands a bare ISO-639-1 code into a full language_COUNTRY
// tag, mirroring tinyMediaManager's getRequestLanguage behavior.
var defaultCountry = map[string]string{
	"de": "DE", "en": "US", "es": "ES", "fr": "FR", "it": "IT",
	"ja": "JP", "ko": "KR", "pt": "BR", "ru": "RU", "zh": "CN",
}

// ExpandLanguage turns "zh" into "zh-CN"; tags that already carry a country
// ("zh_CN", "zh-CN") pass through with dashes.
func ExpandLanguage(lang string) string {
	lang = strings.TrimSpace(lang)
	switch len(lang) {
	case 2:
		lang = strings.ToLower(lang)
		if c, ok := defaultCountry[lang]; ok {
			return lang + "-" + c
		}
		return lang + "-" + strings.ToUpper(lang)
	case 5:
		if lang[2] == '_' || lang[2] == '-' {
			return strings.ToLower(lang[:2]) + "-" + strings.ToUpper(lang[3:])
		}
	}
	return lang
}

// parts splits a language tag into (language, country).
func parts(tag string) (string, string) {
	tag = strings.ReplaceAll(tag, "_", "-")
	if i := strings.IndexByte(tag, '-'); i > 0 {
		return strings.ToLower(tag[:i]), strings.ToUpper(tag[i+1:])
	}
	return strings.ToLower(tag), ""
}

// localesExcludedFromLooseMatch are the es-MX / pt-BR / fr-CA style pairs
// tinyMediaManager excludes from the language-or-country loose match.
var looseExcluded = map[string]bool{
	"ES-MX": true, "PT-BR": true, "FR-CA": true,
}

// pickBest applies the Phase-0 translation fallback chain to a
// translations block:
//  1. exact language_COUNTRY match
//  2. language-only OR country-only match (excluding es-MX/pt-BR/fr-CA)
//  3. configured fallback language
//  4. the detail's own (requested-language) fields
//  5. English
//
// EnglishTitle is always the en translation title when available. Shared by
// the movie and TV variants.
func pickBest(translations []Translation, title, overview, tagline, requested, fallback string) BestTitle {
	reqLang, reqCountry := parts(requested)
	fbLang, fbCountry := parts(fallback)

	var best BestTitle
	best.Source = "original"
	best.Title, best.Overview, best.Tagline = title, overview, tagline

	var exact, loose, fb, en *Translation
	for i := range translations {
		t := &translations[i]
		l, c := parts(t.ISO639_1 + "-" + t.ISO3166_1)
		tag := strings.ToUpper(t.ISO639_1 + "-" + t.ISO3166_1)
		if l == "en" && en == nil {
			en = t
		}
		switch {
		case reqCountry != "" && l == reqLang && c == reqCountry:
			exact = t
		case looseExcluded[tag]:
			// excluded from the loose match (es-MX / pt-BR / fr-CA)
		case l == reqLang || (reqCountry != "" && c == reqCountry):
			if loose == nil {
				loose = t
			}
		case l == fbLang && (fbCountry == "" || c == fbCountry):
			if fb == nil {
				fb = t
			}
		}
	}

	apply := func(t *Translation, source string) {
		if t == nil {
			return
		}
		if t.Data.Title != "" {
			best.Title = t.Data.Title
		}
		if t.Data.Overview != "" {
			best.Overview = t.Data.Overview
		}
		if t.Data.Tagline != "" {
			best.Tagline = t.Data.Tagline
		}
		best.Source = source
	}
	switch {
	case exact != nil:
		apply(exact, "exact")
	case loose != nil:
		apply(loose, "loose")
	case fb != nil:
		apply(fb, "fallback")
	case en != nil && best.Title == "":
		apply(en, "english")
	}

	if en != nil && en.Data.Title != "" {
		best.EnglishTitle = en.Data.Title
	}
	return best
}

// PickBestTitle applies the Phase-0 translation fallback chain to a movie
// detail.
func PickBestTitle(d *MovieDetail, requested, fallback string) BestTitle {
	return pickBest(d.Translations.Translations, d.Title, d.Overview, d.Tagline, requested, fallback)
}

// Certification returns the certification for a country, preferring
// theatrical and later release types over premieres (type 1), mirroring the
// Phase-0 release_dates semantics.
func (d *MovieDetail) Certification(country string) string {
	for _, r := range d.ReleaseDates.Results {
		if !strings.EqualFold(r.ISO3166_1, country) {
			continue
		}
		nonPremiere := ""
		any := ""
		for _, rd := range r.ReleaseDates {
			if rd.Certification == "" {
				continue
			}
			if any == "" {
				any = rd.Certification
			}
			if rd.Type > 1 && nonPremiere == "" {
				nonPremiere = rd.Certification
			}
		}
		if nonPremiere != "" {
			return nonPremiere
		}
		return any
	}
	return ""
}
