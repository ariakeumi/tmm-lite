package tmdb

import (
	"context"
	"fmt"
	"net/http"
	"sync"

	"media-manager-lite/internal/settings"
)

// Service resolves the API key (settings table first, env fallback), the
// metadata/fallback languages and the artwork base URL, and hands out
// ready-to-use clients. The key itself never leaves this package.
type Service struct {
	settings *settings.Store
	envKey   string
	baseURL  string

	httpClient *http.Client

	mu    sync.Mutex
	cfg   *Configuration
	cfgOK bool
}

// NewService returns a TMDB service. envKey is the bootstrap fallback from
// configuration; the settings-table value takes precedence.
func NewService(st *settings.Store, envKey, baseURL string) *Service {
	if baseURL == "" {
		baseURL = DefaultBaseURL
	}
	return &Service{
		settings:   st,
		envKey:     envKey,
		baseURL:    baseURL,
		httpClient: &http.Client{Timeout: requestTimeout},
	}
}

// KeyConfigured reports whether a key is available without exposing it.
func (s *Service) KeyConfigured(ctx context.Context) (bool, error) {
	_, err := s.apiKey(ctx)
	return err == nil, nil
}

func (s *Service) apiKey(ctx context.Context) (string, error) {
	if s.settings != nil {
		if v, ok, err := s.settings.Get(ctx, settings.KeyTMDBAPIKey); err == nil && ok && v != "" {
			return v, nil
		}
	}
	if s.envKey != "" {
		return s.envKey, nil
	}
	return "", ErrNotConfigured
}

// langOr returns the settings value or def when no store is configured.
func (s *Service) langOr(ctx context.Context, key, def string) string {
	if s.settings == nil {
		return def
	}
	v, err := s.settings.String(ctx, key, def)
	if err != nil {
		return def
	}
	return v
}

// Client returns a client bound to the currently configured key.
func (s *Service) Client(ctx context.Context) (*Client, error) {
	key, err := s.apiKey(ctx)
	if err != nil {
		return nil, err
	}
	client := NewClient(key, s.baseURL)
	client.http = s.httpClient
	return client, nil
}

// Language returns the configured metadata language expanded to a full tag
// (default: zh -> zh-CN).
func (s *Service) Language(ctx context.Context) (string, error) {
	return ExpandLanguage(s.langOr(ctx, settings.KeyMetaLang, "zh")), nil
}

// FallbackLanguage returns the configured fallback language (default en-US).
func (s *Service) FallbackLanguage(ctx context.Context) (string, error) {
	return ExpandLanguage(s.langOr(ctx, settings.KeyFallback, "en-US")), nil
}

// SearchMovieByText searches by title/year, retrying without the year filter
// when the strict search comes up empty (Phase 0 search cascade, text part).
func (s *Service) SearchMovieByText(ctx context.Context, title string, year int) ([]SearchResult, error) {
	c, err := s.Client(ctx)
	if err != nil {
		return nil, err
	}
	lang, err := s.Language(ctx)
	if err != nil {
		return nil, err
	}
	results, err := c.SearchMovie(ctx, title, lang, year)
	if err != nil {
		return nil, err
	}
	if len(results) == 0 && year > 0 {
		results, err = c.SearchMovie(ctx, title, lang, 0)
		if err != nil {
			return nil, err
		}
	}
	return results, nil
}

// FindByIMDB resolves an IMDb id through /find.
func (s *Service) FindByIMDB(ctx context.Context, imdbID string) ([]SearchResult, error) {
	c, err := s.Client(ctx)
	if err != nil {
		return nil, err
	}
	lang, err := s.Language(ctx)
	if err != nil {
		return nil, err
	}
	return c.Find(ctx, imdbID, "imdb_id", lang)
}

// MovieDetailResult bundles the raw detail with the fallback-applied title.
type MovieDetailResult struct {
	Detail    *MovieDetail
	Best      BestTitle
	Language  string
	PosterURL string
}

// MovieDetail fetches full details and applies the translation fallback
// chain. best.Source records which tier of the chain produced the title.
func (s *Service) MovieDetail(ctx context.Context, tmdbID int) (*MovieDetailResult, error) {
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
	d, err := c.MovieDetail(ctx, tmdbID, lang)
	if err != nil {
		return nil, err
	}
	poster, _ := s.PosterURL(ctx, d.PosterPath)
	return &MovieDetailResult{
		Detail:    d,
		Best:      PickBestTitle(d, lang, fb),
		Language:  lang,
		PosterURL: poster,
	}, nil
}

// PosterURL resolves a poster path to a preview URL using the
// configuration's secure base URL (cached after the first call).
func (s *Service) PosterURL(ctx context.Context, posterPath string) (string, error) {
	if posterPath == "" {
		return "", nil
	}
	base, err := s.ImageBaseURL(ctx)
	if err != nil {
		return "", err
	}
	return base + PosterSize + posterPath, nil
}

// PosterThumbURL resolves a poster path to a small thumbnail URL for
// candidate lists (w185).
func (s *Service) PosterThumbURL(ctx context.Context, posterPath string) (string, error) {
	if posterPath == "" {
		return "", nil
	}
	base, err := s.ImageBaseURL(ctx)
	if err != nil {
		return "", err
	}
	return base + PosterThumbSize + posterPath, nil
}

// ImageBaseURL returns the cached TMDB image base URL (fetching the
// configuration on first use). Artwork downloads append the size and path.
func (s *Service) ImageBaseURL(ctx context.Context) (string, error) {
	return s.imageBaseURL(ctx)
}

func (s *Service) imageBaseURL(ctx context.Context) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.cfgOK {
		return s.cfg.Images.SecureBaseURL, nil
	}
	c, err := s.Client(ctx)
	if err != nil {
		return "", err
	}
	cfg, err := c.Configuration(ctx)
	if err != nil {
		return "", fmt.Errorf("tmdb: configuration: %w", err)
	}
	s.cfg, s.cfgOK = cfg, true
	return cfg.Images.SecureBaseURL, nil
}

// SetHTTPClient overrides the HTTP client (used by tests to share
// transports with fixture servers).
func (s *Service) SetHTTPClient(h *http.Client) { s.httpClient = h }

// ResetConfigurationCache drops the cached configuration (used when the API
// key changes so the next request revalidates it).
func (s *Service) ResetConfigurationCache() {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.cfg, s.cfgOK = nil, false
}
