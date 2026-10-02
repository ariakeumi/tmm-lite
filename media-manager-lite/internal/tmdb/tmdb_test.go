package tmdb

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestExpandLanguage(t *testing.T) {
	cases := map[string]string{
		"zh":    "zh-CN",
		"ja":    "ja-JP",
		"ko":    "ko-KR",
		"en":    "en-US",
		"de":    "de-DE",
		"zh-CN": "zh-CN",
		"zh_CN": "zh-CN",
		"en-us": "en-US",
		"xx":    "xx-XX",
	}
	for in, want := range cases {
		if got := ExpandLanguage(in); got != want {
			t.Errorf("ExpandLanguage(%q) = %q, want %q", in, got, want)
		}
	}
}

func newFixtureClient(t *testing.T, handler http.HandlerFunc) *Client {
	t.Helper()
	ts := httptest.NewServer(handler)
	t.Cleanup(ts.Close)
	return NewClient("fixture-key-0001", ts.URL)
}

func TestConfigurationSuccessAndAuth(t *testing.T) {
	c := newFixtureClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("api_key") != "fixture-key-0001" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		if r.URL.Path != "/configuration" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		io.WriteString(w, `{"images":{"secure_base_url":"http://image.example/"}}`)
	})

	cfg, err := c.Configuration(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if cfg.Images.SecureBaseURL != "http://image.example/" {
		t.Errorf("secure base url = %q", cfg.Images.SecureBaseURL)
	}

	bad := NewClient("wrong-key", c.baseURL)
	if _, err := bad.Configuration(context.Background()); !errors.Is(err, ErrUnauthorized) {
		t.Errorf("wrong key error = %v, want ErrUnauthorized", err)
	}
}

func TestSearchMovieSendsLanguageAndPages(t *testing.T) {
	var queries []string
	c := newFixtureClient(t, func(w http.ResponseWriter, r *http.Request) {
		queries = append(queries, r.URL.RawQuery)
		q := r.URL.Query()
		if q.Get("language") != "zh-CN" || q.Get("query") != "Movie" {
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		page := q.Get("page")
		w.Header().Set("Content-Type", "application/json")
		if page == "1" {
			io.WriteString(w, `{"page":1,"total_pages":2,"results":[{"id":1,"title":"A","release_date":"2019-01-01"}]}`)
			return
		}
		io.WriteString(w, `{"page":2,"total_pages":2,"results":[{"id":2,"title":"B","release_date":"2020-01-01"}]}`)
	})

	results, err := c.SearchMovie(context.Background(), "Movie", "zh-CN", 0)
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 2 || results[0].TMDBID != 1 || results[1].TMDBID != 2 {
		t.Errorf("results = %+v", results)
	}
	if len(queries) != 2 {
		t.Fatalf("requests = %d, want 2 pages", len(queries))
	}
	if !strings.Contains(queries[1], "page=2") {
		t.Errorf("second query = %q, want page=2", queries[1])
	}
	if !strings.Contains(queries[0], "api_key=fixture-key-0001") {
		t.Error("api_key not sent")
	}
}

func TestSearchMovieYearFilterAndErrors(t *testing.T) {
	c := newFixtureClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("year") != "2019" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		io.WriteString(w, `{"page":1,"total_pages":1,"results":[]}`)
	})
	if _, err := c.SearchMovie(context.Background(), "Movie", "zh-CN", 2019); err != nil {
		t.Errorf("year search error = %v", err)
	}
	if _, err := c.SearchMovie(context.Background(), "Movie", "zh-CN", 0); !errors.Is(err, ErrUnauthorized) {
		t.Errorf("unauthed search error = %v, want ErrUnauthorized", err)
	}
}

func TestMovieDetailAndFind(t *testing.T) {
	c := newFixtureClient(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if strings.HasPrefix(r.URL.Path, "/movie/") {
			if r.URL.Query().Get("append_to_response") != "translations,credits,release_dates,external_ids" {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			io.WriteString(w, `{"id":111,"title":"Movie","translations":{"translations":[]}}`)
			return
		}
		if strings.HasPrefix(r.URL.Path, "/find/tt0000111") {
			if r.URL.Query().Get("external_source") != "imdb_id" {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			io.WriteString(w, `{"movie_results":[{"id":111,"title":"Found Movie","release_date":"2009-01-01"}]}`)
			return
		}
		w.WriteHeader(http.StatusNotFound)
	})

	d, err := c.MovieDetail(context.Background(), 111, "zh-CN")
	if err != nil {
		t.Fatal(err)
	}
	if d.TMDBID != 111 {
		t.Errorf("detail id = %d", d.TMDBID)
	}

	found, err := c.Find(context.Background(), "tt0000111", "imdb_id", "zh-CN")
	if err != nil {
		t.Fatal(err)
	}
	if len(found) != 1 || found[0].TMDBID != 111 {
		t.Errorf("find results = %+v", found)
	}
	if y := found[0].Year(); y != 2009 {
		t.Errorf("Year() = %d, want 2009", y)
	}
}

func TestAPIKeyNeverLeaksIntoErrorsOrLogs(t *testing.T) {
	var logBuf strings.Builder
	slog.SetDefault(slog.New(slog.NewTextHandler(&logBuf, nil)))

	c := newFixtureClient(t, func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		io.WriteString(w, `{"status_message":"boom"}`)
	})

	_, err := c.SearchMovie(context.Background(), "Movie", "zh-CN", 0)
	if err == nil {
		t.Fatal("expected an error")
	}
	if strings.Contains(err.Error(), "fixture-key-0001") {
		t.Errorf("API key leaked into error: %v", err)
	}
	if strings.Contains(logBuf.String(), "fixture-key-0001") {
		t.Errorf("API key leaked into logs: %s", logBuf.String())
	}

	// Even network-level failures must not carry the key.
	bad := NewClient("fixture-key-0001", "http://127.0.0.1:1") // nothing listens
	if _, err := bad.SearchMovie(context.Background(), "Movie", "", 0); err != nil {
		if strings.Contains(err.Error(), "fixture-key-0001") {
			t.Errorf("API key leaked into network error: %v", err)
		}
	}
}

func TestPickBestTitleFallbackChain(t *testing.T) {
	mk := func(translations string) *MovieDetail {
		d := &MovieDetail{Title: "Requested Overview Title", Overview: "requested overview", OriginalLanguage: "en"}
		if err := json.Unmarshal([]byte(translations), &d.Translations); err != nil {
			t.Fatal(err)
		}
		return d
	}

	cases := []struct {
		name        string
		d           *MovieDetail
		requested   string
		fallback    string
		wantTitle   string
		wantSource  string
		wantEnglish string
	}{
		{
			name: "exact zh-CN",
			d: mk(`{"translations":[
				{"iso_639_1":"en","iso_3166_1":"US","data":{"title":"Movie One","overview":"english overview"}},
				{"iso_639_1":"zh","iso_3166_1":"TW","data":{"title":"電影一","overview":"tw overview"}},
				{"iso_639_1":"zh","iso_3166_1":"CN","data":{"title":"大电影","overview":"cn overview"}}]}`),
			requested: "zh-CN", fallback: "en-US",
			wantTitle: "大电影", wantSource: "exact", wantEnglish: "Movie One",
		},
		{
			name: "loose zh-* when exact missing",
			d: mk(`{"translations":[
				{"iso_639_1":"en","iso_3166_1":"US","data":{"title":"Movie One","overview":"english overview"}},
				{"iso_639_1":"zh","iso_3166_1":"TW","data":{"title":"電影一","overview":"tw overview"}}]}`),
			requested: "zh-CN", fallback: "en-US",
			wantTitle: "電影一", wantSource: "loose", wantEnglish: "Movie One",
		},
		{
			name: "fallback language when no zh",
			d: mk(`{"translations":[
				{"iso_639_1":"en","iso_3166_1":"US","data":{"title":"Movie One","overview":"english overview"}},
				{"iso_639_1":"ja","iso_3166_1":"JP","data":{"title":"映画","overview":"jp overview"}}]}`),
			requested: "zh-CN", fallback: "ja-JP",
			wantTitle: "映画", wantSource: "fallback", wantEnglish: "Movie One",
		},
		{
			name:      "original when nothing matches",
			d:         mk(`{"translations":[]}`),
			requested: "zh-CN", fallback: "en-US",
			wantTitle: "Requested Overview Title", wantSource: "original", wantEnglish: "",
		},
		{
			name: "excluded es-MX never wins the loose match for bare es",
			d: mk(`{"translations":[
				{"iso_639_1":"es","iso_3166_1":"MX","data":{"title":"Película MX","overview":"mx"}},
				{"iso_639_1":"es","iso_3166_1":"ES","data":{"title":"Película ES","overview":"es"}}]}`),
			requested: "es", fallback: "en-US",
			wantTitle: "Película ES", wantSource: "loose",
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := PickBestTitle(tc.d, tc.requested, tc.fallback)
			if got.Title != tc.wantTitle {
				t.Errorf("title = %q, want %q", got.Title, tc.wantTitle)
			}
			if got.Source != tc.wantSource {
				t.Errorf("source = %q, want %q", got.Source, tc.wantSource)
			}
			if got.EnglishTitle != tc.wantEnglish {
				t.Errorf("english title = %q, want %q", got.EnglishTitle, tc.wantEnglish)
			}
		})
	}
}

func TestCertificationPrefersTheatrical(t *testing.T) {
	d := &MovieDetail{}
	payload := `{"results":[{"iso_3166_1":"US","release_dates":[{"certification":"","type":1,"release_date":"2019-01-01"},{"certification":"PG-13","type":3,"release_date":"2019-05-17"}]}]}`
	if err := json.Unmarshal([]byte(payload), &d.ReleaseDates); err != nil {
		t.Fatal(err)
	}
	if got := d.Certification("US"); got != "PG-13" {
		t.Errorf("certification = %q, want PG-13", got)
	}
	if got := d.Certification("DE"); got != "" {
		t.Errorf("missing country certification = %q, want empty", got)
	}
}

func TestServiceKeyResolution(t *testing.T) {
	// Without any key the service reports ErrNotConfigured and never a key.
	s := NewService(nil, "", DefaultBaseURL)
	if _, err := s.Client(context.Background()); !errors.Is(err, ErrNotConfigured) {
		t.Errorf("error = %v, want ErrNotConfigured", err)
	}
	configured, _ := s.KeyConfigured(context.Background())
	if configured {
		t.Error("KeyConfigured = true without any key")
	}
}
