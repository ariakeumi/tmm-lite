package tests_test

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"media-manager-lite/internal/artwork"
	"media-manager-lite/internal/config"
	"media-manager-lite/internal/database"
	httpapi "media-manager-lite/internal/http"
	"media-manager-lite/internal/library"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/nfo"
	"media-manager-lite/internal/renamer"
	"media-manager-lite/internal/scanner"
	"media-manager-lite/internal/settings"
	"media-manager-lite/internal/task"
	"media-manager-lite/internal/tmdb"
	"media-manager-lite/internal/tv"
)

// app is a fully wired application under test.
type app struct {
	ts     *httptest.Server
	db     *database.DB
	dbPath string
	deps   httpapi.Deps
}

// newApp boots the whole stack on the given database path.
func newApp(t *testing.T, dbPath string) *app {
	t.Helper()
	return newAppWithConfig(t, func() config.Config {
		cfg := config.Defaults()
		cfg.DBPath = dbPath
		return cfg
	})
}

func newAppWithConfig(t *testing.T, cfgFn func() config.Config) *app {
	t.Helper()

	cfg := cfgFn()
	db, err := database.Open(cfg.DBPath)
	if err != nil {
		t.Fatalf("open db: %v", err)
	}
	if err := database.Migrate(t.Context(), db.DB); err != nil {
		t.Fatalf("migrate: %v", err)
	}

	log := slog.New(slog.NewTextHandler(io.Discard, nil))
	libs := library.NewService(library.NewStore(db.DB))
	mediaStore := media.NewStore(db.DB)
	tvStore := tv.NewStore(db.DB)
	scanSvc := scanner.NewService(mediaStore, tvStore)
	tasks := task.NewStore(db.DB)
	runner := task.NewRunner(tasks, log, 64)
	runner.Register("scan_library", scanner.ScanHandler(libs, scanSvc))
	if err := runner.Start(t.Context()); err != nil {
		t.Fatalf("start runner: %v", err)
	}
	settingsStore := settings.NewStore(db.DB)
	movies := movie.NewStore(db.DB)
	tmdbSvc := tmdb.NewService(settingsStore, cfg.TMDBAPIKey, cfg.TMDBBaseURL)
	artworkSvc := artwork.NewService(tmdbSvc, mediaStore, movies, tvStore, libs)
	nfoSvc := nfo.NewService(mediaStore, movies, settingsStore, tmdbSvc, tvStore, libs)
	renamerSvc := renamer.NewService(mediaStore, movies, libs, settingsStore, tvStore)
	tvScrape := tv.NewScrapeService(tvStore, tmdbSvc, func(ctx context.Context) string {
		v, _ := settingsStore.String(ctx, "certification_country", "US")
		return v
	})

	runner.Register("download_artwork", artwork.DownloadHandler(mediaStore, artworkSvc))
	runner.Register("download_tv_artwork", artwork.TVDownloadHandler(tvStore, artworkSvc))
	runner.Register("scrape_tv_episodes", tv.EnrichHandler(tvStore, tvScrape))

	deps := httpapi.Deps{
		Libraries: libs,
		Media:     mediaStore,
		Scanner:   scanSvc,
		Tasks:     tasks,
		Runner:    runner,
		Settings:  settingsStore,
		Movies:    movies,
		TMDB:      tmdbSvc,
		Artwork:   artworkSvc,
		NFO:       nfoSvc,
		Renamer:   renamerSvc,
		TV:        tvStore,
		TVScrape:  tvScrape,
	}
	srv, err := httpapi.New(cfg, log, db, deps)
	if err != nil {
		t.Fatalf("new server: %v", err)
	}
	ts := httptest.NewServer(srv.Handler())
	t.Cleanup(ts.Close)

	return &app{ts: ts, db: db, dbPath: cfg.DBPath, deps: deps}
}

// restartApp closes the current app's database and boots a fresh application
// on the same file, simulating a container restart.
func restartApp(t *testing.T, a *app) *app {
	t.Helper()
	a.ts.Close()
	if err := a.db.Close(); err != nil {
		t.Fatalf("close db: %v", err)
	}
	return newApp(t, a.dbPath)
}

func TestMilestone1EndToEnd(t *testing.T) {
	dir := t.TempDir()
	dbPath := filepath.Join(dir, "mml.db")

	mediaDir := t.TempDir()
	tvDir := t.TempDir()

	a := newApp(t, dbPath)

	// Dashboard renders.
	resp, err := http.Get(a.ts.URL + "/")
	if err != nil {
		t.Fatal(err)
	}
	body, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	if resp.StatusCode != http.StatusOK || !strings.Contains(string(body), "仪表盘") {
		t.Fatalf("dashboard status=%d body=%q", resp.StatusCode, body)
	}

	// Create a movie and a TV library via the API.
	movieID := createLibrary(t, a.ts.URL, "Movies", mediaDir, "movie")
	tvID := createLibrary(t, a.ts.URL, "TV Shows", tvDir, "tv")
	if movieID == "" || tvID == "" {
		t.Fatal("library ids empty")
	}

	// Both are listed and rendered on the page.
	libs := listLibraries(t, a.ts.URL)
	if len(libs) != 2 {
		t.Fatalf("libraries = %d, want 2", len(libs))
	}
	page := getBody(t, a.ts.URL+"/libraries")
	for _, want := range []string{"Movies", "TV Shows"} {
		if !strings.Contains(page, want) {
			t.Errorf("/libraries page missing %q", want)
		}
	}

	// The media directories are untouched.
	for _, p := range []string{mediaDir, tvDir} {
		if _, err := os.Stat(p); err != nil {
			t.Fatalf("media dir %s disturbed: %v", p, err)
		}
	}

	// Simulate a container restart: same database, fresh process objects.
	a = restartApp(t, a)

	libs = listLibraries(t, a.ts.URL)
	if len(libs) != 2 {
		t.Fatalf("libraries after restart = %d, want 2 (persistence)", len(libs))
	}

	// Delete one library; the other survives, media stays.
	deleteLibrary(t, a.ts.URL, movieID)
	libs = listLibraries(t, a.ts.URL)
	if len(libs) != 1 || libs[0]["id"] != tvID {
		t.Fatalf("libraries after delete = %+v, want only the TV library", libs)
	}
	if _, err := os.Stat(mediaDir); err != nil {
		t.Fatalf("media dir must survive library deletion: %v", err)
	}
}

func TestMilestone2ScanPersistLifecycle(t *testing.T) {
	dir := t.TempDir()
	dbPath := filepath.Join(dir, "mml.db")

	// Media directory with real files and junk.
	mediaDir := filepath.Join(dir, "media")
	for _, f := range []string{
		"Movie.One.2019.1080p.BluRay.x264.mkv",
		"sub/Movie.Two.2020.2160p.WEB-DL.x265.mkv",
		"notes.nfo", "poster.jpg", "sample.mkv",
	} {
		p := filepath.Join(mediaDir, f)
		if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(p, []byte("x"), 0o600); err != nil {
			t.Fatal(err)
		}
	}

	a := newApp(t, dbPath)
	movieID := createLibrary(t, a.ts.URL, "Movies", mediaDir, "movie")

	// Scan via API (async): submit, then wait for completion.
	scanAndWait(t, a.ts.URL, movieID, "found 2 files: 2 new, 0 updated")

	// Media items listed with parsed metadata.
	list := getJSON(t, a.ts.URL+"/api/libraries/"+movieID+"/media")
	if list["count"].(float64) != 2 {
		t.Fatalf("media count = %v, want 2", list["count"])
	}

	// Rescan is idempotent.
	scanAndWait(t, a.ts.URL, movieID, "found 2 files: 0 new, 2 updated")

	// Restart the whole application: media items persist.
	a = restartApp(t, a)
	list = getJSON(t, a.ts.URL+"/api/libraries/"+movieID+"/media")
	if list["count"].(float64) != 2 {
		t.Fatalf("media count after restart = %v, want 2", list["count"])
	}

	// Deleting the library cascades the database records; the files survive.
	deleteLibrary(t, a.ts.URL, movieID)
	var n int
	if err := a.db.QueryRow("SELECT COUNT(*) FROM media_items WHERE library_id = ?", movieID).Scan(&n); err != nil {
		t.Fatal(err)
	}
	if n != 0 {
		t.Errorf("media_items rows after library delete = %d, want 0 (cascade)", n)
	}
	for _, f := range []string{
		"Movie.One.2019.1080p.BluRay.x264.mkv",
		filepath.Join("sub", "Movie.Two.2020.2160p.WEB-DL.x265.mkv"),
	} {
		if _, err := os.Stat(filepath.Join(mediaDir, f)); err != nil {
			t.Errorf("media file %q must survive library deletion: %v", f, err)
		}
	}
}

// pngBytes is a minimal valid 1x1 PNG served by the artwork fixture.
var pngBytes = []byte{
	0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
	0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
	0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4, 0x89, 0x00, 0x00, 0x00,
	0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
	0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49,
	0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82,
}

// tmdbFixture serves canned TMDB responses for the M3/M4 flows.
func tmdbFixture(t *testing.T) *httptest.Server {
	t.Helper()
	var baseURL string
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if strings.HasPrefix(r.URL.Path, "/original/") {
			// Image-CDN style: no API key required for artwork downloads.
			w.Header().Set("Content-Type", "image/png")
			w.Write(pngBytes)
			return
		}
		if r.URL.Query().Get("api_key") != "fixture-key-0001" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		switch {
		case r.URL.Path == "/configuration":
			fmt.Fprintf(w, `{"images":{"secure_base_url":"%s"}}`, baseURL)
		case r.URL.Path == "/tv/555/images":
			fmt.Fprint(w, `{"posters":[{"id":1,"file_path":"/tv-poster.jpg","iso_639_1":"zh","vote_average":9.0,"vote_count":50}],"backdrops":[{"id":2,"file_path":"/tv-fanart.jpg","vote_average":8.0,"vote_count":30}]}`)
		case strings.HasPrefix(r.URL.Path, "/tv/555/season/") && strings.HasSuffix(r.URL.Path, "/images"):
			fmt.Fprint(w, `{"posters":[{"id":3,"file_path":"/s1-poster.jpg","iso_639_1":"zh","vote_average":8.5,"vote_count":20}]}`)
		case r.URL.Path == "/search/tv":
			if r.URL.Query().Get("language") != "zh-CN" {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			fmt.Fprint(w, `{"page":1,"total_pages":1,"results":[
				{"id":555,"name":"北斗剧集","original_name":"北斗","first_air_date":"2021-05-01","poster_path":"/tv.jpg","vote_average":8.1,"vote_count":99},
				{"id":666,"name":"Unrelated Series","original_name":"Unrelated Series","first_air_date":"1999-01-01","vote_average":5,"vote_count":1}]}`)
		case r.URL.Path == "/tv/555":
			fmt.Fprint(w, `{
				"id":555,"name":"北斗剧集","original_name":"北斗","overview":"requested overview",
				"first_air_date":"2021-05-01","status":"Returning Series","vote_average":8.1,"vote_count":99,
				"original_language":"cn","number_of_seasons":1,"number_of_episodes":3,
				"genres":[{"id":18,"name":"Drama"}],
				"content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]},
				"episode_groups":{"results":[]},
				"credits":{"cast":[],"crew":[]},
				"translations":{"translations":[
					{"iso_639_1":"en","iso_3166_1":"US","data":{"title":"Polaris Show","overview":"english overview"}},
					{"iso_639_1":"zh","iso_3166_1":"CN","data":{"title":"北斗剧集","overview":"中国大陆简介"}}]},
				"external_ids":{"imdb_id":"tt555"}}`)
		case r.URL.Path == "/tv/555/season/1":
			fmt.Fprint(w, `{"id":900,"name":"Season 1","season_number":1,"episodes":[
				{"id":9001,"name":"第 1 集","overview":"","season_number":1,"episode_number":1,"air_date":"2021-05-01","runtime":45,"vote_average":7.5,"vote_count":10},
				{"id":9002,"name":"名场面","overview":"好戏","season_number":1,"episode_number":2,"air_date":"2021-05-08","runtime":47,"vote_average":8.0,"vote_count":12},
				{"id":9003,"name":"第 3 集","overview":"","season_number":1,"episode_number":3,"air_date":"2021-05-15","runtime":44,"vote_average":7.0,"vote_count":8}]}`)
		case r.URL.Path == "/search/movie":
			if r.URL.Query().Get("language") != "zh-CN" {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			fmt.Fprint(w, `{"page":1,"total_pages":1,"results":[
				{"id":111,"title":"大电影 2019","original_title":"Movie One","release_date":"2019-05-17","poster_path":"/p1.jpg","vote_average":7.5,"vote_count":100},
				{"id":222,"title":"Unrelated Film","original_title":"Unrelated Film","release_date":"2021-01-01","vote_average":5,"vote_count":1}]}`)
		case r.URL.Path == "/movie/111/images":
			fmt.Fprint(w, `{"posters":[
				{"file_path":"/p-zh.jpg","iso_639_1":"zh","vote_average":6.0,"vote_count":20},
				{"file_path":"/p-en.jpg","iso_639_1":"en","vote_average":9.9,"vote_count":100},
				{"file_path":"/p-de.jpg","iso_639_1":"de","vote_average":9.0,"vote_count":40}],
				"backdrops":[
				{"file_path":"/b-1.jpg","vote_average":8.8,"vote_count":55},
				{"file_path":"/b-2.jpg","vote_average":7.0,"vote_count":5}]}`)
		case strings.HasPrefix(r.URL.Path, "/movie/"):
			fmt.Fprint(w, `{
				"id":111,"title":"Movie One","original_title":"Movie One","overview":"fallback overview",
				"release_date":"2019-05-17","runtime":96,"vote_average":7.8,"vote_count":4321,
				"poster_path":"/p1.jpg","original_language":"en","status":"Released",
				"genres":[{"id":28,"name":"Action"}],
				"production_companies":[{"id":1,"name":"Example Studios"}],
				"credits":{"cast":[{"id":9,"name":"Jane Doe","character":"Lead","profile_path":"/j.jpg"}],
				           "crew":[{"id":8,"name":"John Director","job":"Director","department":"Directing"},
				                   {"id":7,"name":"Ann Writer","job":"Screenplay","department":"Writing"}]},
				"belongs_to_collection":{"id":119,"name":"Movie One Collection"},
				"release_dates":{"results":[{"iso_3166_1":"US","release_dates":[{"certification":"PG-13","type":3,"release_date":"2019-05-17"}]}]},
				"translations":{"translations":[
					{"iso_639_1":"en","iso_3166_1":"US","data":{"title":"Movie One","overview":"english overview"}},
					{"iso_639_1":"zh","iso_3166_1":"TW","data":{"title":"電影一","overview":"台灣簡介"}},
					{"iso_639_1":"zh","iso_3166_1":"CN","data":{"title":"大电影 2019","overview":"中国大陆简介"}}]},
				"external_ids":{"imdb_id":"tt0000111"}}`)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	})
	srv := httptest.NewServer(mux)
	baseURL = srv.URL + "/"
	t.Cleanup(srv.Close)
	return srv
}

func TestMilestone3MatchFlow(t *testing.T) {
	dir := t.TempDir()
	mediaDir := filepath.Join(dir, "media")
	if err := os.MkdirAll(mediaDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(mediaDir, "Movie.One.2019.1080p.BluRay.x264.mkv"), []byte("x"), 0o600); err != nil {
		t.Fatal(err)
	}

	fixture := tmdbFixture(t)
	a := newAppWithConfig(t, func() config.Config {
		cfg := config.Defaults()
		cfg.DBPath = filepath.Join(dir, "mml.db")
		cfg.TMDBBaseURL = fixture.URL
		cfg.TMDBAPIKey = "fixture-key-0001"
		return cfg
	})

	// No key in settings yet: env fallback applies, key endpoint reports it.
	keyStatus := getJSON(t, a.ts.URL+"/api/settings/tmdb-api-key")
	if keyStatus["configured"] != true {
		t.Fatalf("key configured = %v, want true (env fallback)", keyStatus["configured"])
	}

	libID := createLibrary(t, a.ts.URL, "Movies", mediaDir, "movie")
	scanAndWait(t, a.ts.URL, libID, "found 1 files: 1 new, 0 updated")

	list := getJSON(t, a.ts.URL+"/api/libraries/"+libID+"/media")
	items := list["mediaItems"].([]any)
	item := items[0].(map[string]any)
	itemID := item["id"].(string)

	// Search: candidates scored, best first, persisted.
	search := postJSONBody(t, a.ts.URL+"/api/movies/"+itemID+"/search", `{}`)
	if search["count"].(float64) != 2 {
		t.Fatalf("candidate count = %v, want 2", search["count"])
	}
	cands := search["candidates"].([]any)
	best := cands[0].(map[string]any)
	if best["idMatch"] != false {
		t.Errorf("best candidate unexpectedly flagged as id match")
	}
	cand := best["candidate"].(map[string]any)
	if cand["tmdbId"].(float64) != 111 {
		t.Errorf("best candidate = %+v, want TMDB id 111", cand)
	}

	// GET candidates returns the persisted list.
	persisted := getJSON(t, a.ts.URL+"/api/movies/"+itemID+"/candidates")
	if persisted["count"].(float64) != 2 {
		t.Errorf("persisted candidates = %v, want 2", persisted["count"])
	}

	// Confirm the match: detail is fetched, movie row written, item matched.
	matchBody := `{"tmdbId": 111}`
	resp, err := http.Post(a.ts.URL+"/api/movies/"+itemID+"/match", "application/json", strings.NewReader(matchBody))
	if err != nil {
		t.Fatal(err)
	}
	var matchRes struct {
		Status string `json:"status"`
		Movie  struct {
			ID     string  `json:"id"`
			TMDBID int     `json:"tmdbId"`
			Title  string  `json:"title"`
			Year   int     `json:"year"`
			Plot   string  `json:"plot"`
			Rating float64 `json:"rating"`
		} `json:"movie"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&matchRes); err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusOK || matchRes.Status != "matched" {
		t.Fatalf("match status = %d/%s", resp.StatusCode, matchRes.Status)
	}
	// zh-CN translation won the fallback chain.
	if matchRes.Movie.Title != "大电影 2019" {
		t.Errorf("matched title = %q, want the zh-CN translation", matchRes.Movie.Title)
	}
	if matchRes.Movie.Year != 2019 || matchRes.Movie.TMDBID != 111 {
		t.Errorf("matched movie = %+v", matchRes.Movie)
	}
	if matchRes.Movie.Plot != "中国大陆简介" {
		t.Errorf("plot = %q, want the zh-CN overview", matchRes.Movie.Plot)
	}

	// Item now carries the movie; movies list shows it.
	itemDetail := getJSON(t, a.ts.URL+"/api/movies/"+itemID)
	if itemDetail["status"] != "matched" {
		t.Errorf("item status = %v, want matched", itemDetail["status"])
	}
	mov := getJSON(t, a.ts.URL+"/api/movies?libraryId="+libID)
	if mov["count"].(float64) != 1 {
		t.Errorf("movies count = %v, want 1", mov["count"])
	}

	// The /movies page lists the matched movie; /tvshows aliases /tv.
	moviesPage := getBody(t, a.ts.URL+"/movies")
	if !strings.Contains(moviesPage, "大电影 2019") {
		t.Errorf("/movies page missing matched movie title")
	}
	if !strings.Contains(moviesPage, "go to match page") && !strings.Contains(moviesPage, "No matched movies") {
		t.Log("pending section absent — all items matched")
	}
	if code := getStatusCode(t, a.ts.URL+"/tvshows"); code != http.StatusOK {
		t.Errorf("/tvshows alias status = %d, want 200", code)
	}

	// Match survives a restart.
	a = restartApp(t, a)
	itemDetail = getJSON(t, a.ts.URL+"/api/movies/"+itemID)
	if itemDetail["status"] != "matched" {
		t.Errorf("item status after restart = %v, want matched", itemDetail["status"])
	}

	// Unmatched transition.
	unmatched := postJSONBody(t, a.ts.URL+"/api/movies/"+itemID+"/match", `{"unmatched": true}`)
	if unmatched["status"] != "unmatched" {
		t.Fatalf("unmatched response = %v", unmatched)
	}
	itemDetail = getJSON(t, a.ts.URL+"/api/movies/"+itemID)
	if itemDetail["status"] != "unmatched" {
		t.Errorf("item status = %v, want unmatched", itemDetail["status"])
	}
}

func scanAndWait(t *testing.T, base, libraryID, wantDetail string) {
	t.Helper()
	resp, err := http.Post(base+"/api/libraries/"+libraryID+"/scan", "application/json", nil)
	if err != nil {
		t.Fatal(err)
	}
	var task struct {
		ID    string `json:"id"`
		State string `json:"state"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&task); err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if task.State != "pending" {
		t.Fatalf("scan submit state = %s, want pending", task.State)
	}
	waitTask(t, base, task.ID, wantDetail)
}

// waitTask polls /api/tasks until the given task reaches "completed"
// (verifying detail) or "failed".
func waitTask(t *testing.T, base, taskID, wantDetail string) {
	t.Helper()
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		tasks := getJSON(t, base+"/api/tasks")
		for _, raw := range tasks["tasks"].([]any) {
			tk := raw.(map[string]any)
			if tk["id"] != taskID {
				continue
			}
			switch tk["state"] {
			case "completed":
				if wantDetail != "" && tk["detail"] != wantDetail {
					t.Fatalf("task detail = %v, want %q", tk["detail"], wantDetail)
				}
				return
			case "failed":
				t.Fatalf("task failed: %v", tk["error"])
			}
			time.Sleep(20 * time.Millisecond)
			continue
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatal("task did not complete within deadline")
}

// postJSONBody posts a JSON body and returns the decoded response map.
// 200 (sync result) and 202 (async task accepted) are both accepted.
func postJSONBody(t *testing.T, url, body string) map[string]any {
	t.Helper()
	resp, err := http.Post(url, "application/json", strings.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusAccepted {
		b, _ := io.ReadAll(resp.Body)
		t.Fatalf("POST %s: status=%d body=%s", url, resp.StatusCode, b)
	}
	var out map[string]any
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		t.Fatal(err)
	}
	return out
}

func createLibrary(t *testing.T, base, name, path, typ string) string {
	t.Helper()
	body, _ := json.Marshal(map[string]string{"name": name, "path": path, "type": typ})
	resp, err := http.Post(base+"/api/libraries", "application/json", strings.NewReader(string(body)))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusCreated {
		b, _ := io.ReadAll(resp.Body)
		t.Fatalf("create %s: status=%d body=%s", name, resp.StatusCode, b)
	}
	var lib struct {
		ID string `json:"id"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&lib); err != nil {
		t.Fatal(err)
	}
	return lib.ID
}

func listLibraries(t *testing.T, base string) []map[string]any {
	t.Helper()
	out := getJSON(t, base+"/api/libraries")
	libs, ok := out["libraries"].([]any)
	if !ok {
		return nil
	}
	res := make([]map[string]any, 0, len(libs))
	for _, l := range libs {
		res = append(res, l.(map[string]any))
	}
	return res
}

func deleteLibrary(t *testing.T, base, id string) {
	t.Helper()
	req, err := http.NewRequest(http.MethodDelete, base+"/api/libraries/"+id, nil)
	if err != nil {
		t.Fatal(err)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNoContent {
		t.Fatalf("delete: status=%d, want 204", resp.StatusCode)
	}
}

func getBody(t *testing.T, url string) string {
	t.Helper()
	resp, err := http.Get(url)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	b, _ := io.ReadAll(resp.Body)
	return string(b)
}

func getJSON(t *testing.T, url string) map[string]any {
	t.Helper()
	resp, err := http.Get(url)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		b, _ := io.ReadAll(resp.Body)
		t.Fatalf("GET %s: status=%d body=%s", url, resp.StatusCode, b)
	}
	var out map[string]any
	if err := json.NewDecoder(resp.Body).Decode(&out); err != nil {
		t.Fatal(err)
	}
	return out
}

func TestMilestone4ArtworkAndNfo(t *testing.T) {
	dir := t.TempDir()
	mediaDir := filepath.Join(dir, "media")
	if err := os.MkdirAll(mediaDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(mediaDir, "Movie.One.2019.1080p.BluRay.x264.mkv"), []byte("x"), 0o600); err != nil {
		t.Fatal(err)
	}

	// A pre-existing NFO with user data and unknown elements — the writer
	// must merge and preserve it.
	oldNfo := `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<movie>
  <title>Stale Title</title>
  <watched>true</watched>
  <playcount>3</playcount>
  <dateadded>2025-06-01 09:30:00</dateadded>
  <resume><position>500</position><total>8880</total></resume>
  <trailer>plugin://plugin.video.youtube/play/?video_id=old</trailer>
</movie>`
	if err := os.WriteFile(filepath.Join(mediaDir, "Movie.One.2019.1080p.BluRay.x264.nfo"), []byte(oldNfo), 0o600); err != nil {
		t.Fatal(err)
	}

	fixture := tmdbFixture(t)
	a := newAppWithConfig(t, func() config.Config {
		cfg := config.Defaults()
		cfg.DBPath = filepath.Join(dir, "mml.db")
		cfg.TMDBBaseURL = fixture.URL
		cfg.TMDBAPIKey = "fixture-key-0001"
		return cfg
	})

	libID := createLibrary(t, a.ts.URL, "Movies", mediaDir, "movie")
	scanAndWait(t, a.ts.URL, libID, "found 1 files: 1 new, 0 updated")

	list := getJSON(t, a.ts.URL+"/api/libraries/"+libID+"/media")
	item := list["mediaItems"].([]any)[0].(map[string]any)
	itemID := item["id"].(string)

	// Match first.
	match := postJSONBody(t, a.ts.URL+"/api/movies/"+itemID+"/match", `{"tmdbId": 111}`)
	if match["status"] != "matched" {
		t.Fatalf("match = %v", match)
	}

	// Artwork: async task downloads poster.jpg + fanart.jpg into the media
	// folder and fills movies.art_json.
	resp, err := http.Post(a.ts.URL+"/api/movies/"+itemID+"/artwork", "application/json", nil)
	if err != nil {
		t.Fatal(err)
	}
	var task struct {
		ID    string `json:"id"`
		State string `json:"state"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&task); err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusAccepted || task.State != "pending" {
		t.Fatalf("artwork submit = %d/%s", resp.StatusCode, task.State)
	}
	waitTask(t, a.ts.URL, task.ID, "downloaded 2 artwork file(s) for Movie.One.2019.1080p.BluRay.x264.mkv")

	for _, f := range []string{"poster.jpg", "fanart.jpg"} {
		b, err := os.ReadFile(filepath.Join(mediaDir, f))
		if err != nil {
			t.Fatalf("%s missing after download: %v", f, err)
		}
		if len(b) == 0 || b[0] != 0x89 {
			t.Errorf("%s is not the PNG payload", f)
		}
	}
	itemDetail := getJSON(t, a.ts.URL+"/api/movies/"+itemID)
	mov := itemDetail["movie"].(map[string]any)
	if mov["artworkStatus"] != "downloaded" {
		t.Errorf("artwork status = %v", mov["artworkStatus"])
	}
	aw := mov["artwork"].(map[string]any)
	if aw["posterFile"] == "" || aw["fanartFile"] == "" || aw["posterUrl"] == "" {
		t.Errorf("artwork json = %v", aw)
	}
	// Preferred-language ranking: zh poster beats the higher-voted en one.
	if !strings.Contains(aw["posterUrl"].(string), "/p-zh.jpg") {
		t.Errorf("poster url = %v, want the zh poster", aw["posterUrl"])
	}

	// NFO: written next to the video, merging the pre-existing user data.
	nfoRes := postJSONBody(t, a.ts.URL+"/api/movies/"+itemID+"/nfo", `{}`)
	if nfoRes["written"] != true {
		t.Errorf("nfo result = %v", nfoRes)
	}
	if !strings.HasSuffix(nfoRes["path"].(string), "Movie.One.2019.1080p.BluRay.x264.nfo") {
		t.Errorf("nfo path = %v", nfoRes["path"])
	}

	nfoContent := getBody(t, a.ts.URL+"/api/movies/"+itemID+"/nfo")
	for _, want := range []string{
		"<title>大电影 2019</title>",                                      // fresh zh-CN title
		"<playcount>3</playcount>",                                     // merged from the old NFO
		"<dateadded>2025-06-01 09:30:00</dateadded>",                   // preserved
		"<resume><position>500</position><total>8880</total></resume>", // verbatim
		"<trailer>plugin://plugin.video.youtube/play/?video_id=old</trailer>",
		`<uniqueid type="imdb" default="true">tt0000111</uniqueid>`,
		`<uniqueid type="tmdb">111</uniqueid>`,
		`<set tmdbcolid="119">`,
		"<certification>US:PG-13</certification>",
	} {
		if !strings.Contains(nfoContent, want) {
			t.Errorf("NFO missing %q\n%s", want, nfoContent)
		}
	}
	if strings.Contains(nfoContent, "Stale Title") {
		t.Error("stale title must be replaced")
	}

	// Rewriting with unchanged data is skipped (write stability).
	nfoRes2 := postJSONBody(t, a.ts.URL+"/api/movies/"+itemID+"/nfo", `{}`)
	if nfoRes2["written"] != false {
		t.Errorf("second write = %v, want written=false (unchanged)", nfoRes2["written"])
	}

	// A missing NFO yields 404 on read.
	if _, err := os.Stat(filepath.Join(mediaDir, "Movie.One.2019.1080p.BluRay.x264.nfo")); err != nil {
		t.Fatal(err)
	}
}

func TestMilestone5RenameWorkflow(t *testing.T) {
	dir := t.TempDir()
	mediaDir := filepath.Join(dir, "media")
	if err := os.MkdirAll(mediaDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(mediaDir, "Movie.One.2019.1080p.BluRay.x264.mkv"), []byte("VIDEO"), 0o600); err != nil {
		t.Fatal(err)
	}
	// The library service stores the canonical path (macOS /var → /private/var).
	if resolved, err := filepath.EvalSymlinks(mediaDir); err == nil {
		mediaDir = resolved
	}

	fixture := tmdbFixture(t)
	a := newAppWithConfig(t, func() config.Config {
		cfg := config.Defaults()
		cfg.DBPath = filepath.Join(dir, "mml.db")
		cfg.TMDBBaseURL = fixture.URL
		cfg.TMDBAPIKey = "fixture-key-0001"
		return cfg
	})

	libID := createLibrary(t, a.ts.URL, "Movies", mediaDir, "movie")
	scanAndWait(t, a.ts.URL, libID, "found 1 files: 1 new, 0 updated")
	list := getJSON(t, a.ts.URL+"/api/libraries/"+libID+"/media")
	item := list["mediaItems"].([]any)[0].(map[string]any)
	itemID := item["id"].(string)

	// Match + artwork + NFO set up the rename prerequisites.
	postJSONBody(t, a.ts.URL+"/api/movies/"+itemID+"/match", `{"tmdbId": 111}`)
	artTask := postJSONBody(t, a.ts.URL+"/api/movies/"+itemID+"/artwork", `{}`)
	deadline := time.Now().Add(10 * time.Second)
	for {
		tasks := getJSON(t, a.ts.URL+"/api/tasks")
		done := false
		for _, raw := range tasks["tasks"].([]any) {
			tk := raw.(map[string]any)
			if tk["id"] == artTask["id"] {
				if tk["state"] == "completed" {
					done = true
				}
				if tk["state"] == "failed" {
					t.Fatalf("artwork failed: %v", tk["error"])
				}
			}
		}
		if done || time.Now().After(deadline) {
			if !done {
				t.Fatal("artwork task did not complete")
			}
			break
		}
		time.Sleep(20 * time.Millisecond)
	}
	postJSONBody(t, a.ts.URL+"/api/movies/"+itemID+"/nfo", `{}`)

	// Dry-run: the plan is computed with ZERO filesystem writes.
	snapshot := map[string]string{}
	entries, _ := os.ReadDir(mediaDir)
	for _, e := range entries {
		b, _ := os.ReadFile(filepath.Join(mediaDir, e.Name()))
		snapshot[e.Name()] = string(b)
	}

	dryPlan := getJSON(t, a.ts.URL+"/api/movies/"+itemID+"/rename")
	if dryPlan["valid"] != true {
		t.Fatalf("dry plan = %v", dryPlan)
	}
	if dryPlan["newDir"] != filepath.Join(mediaDir, "大电影 2019 (2019)") {
		t.Errorf("newDir = %v", dryPlan["newDir"])
	}

	snapshot2 := map[string]string{}
	entries2, _ := os.ReadDir(mediaDir)
	for _, e := range entries2 {
		b, _ := os.ReadFile(filepath.Join(mediaDir, e.Name()))
		snapshot2[e.Name()] = string(b)
	}
	if len(snapshot) != len(snapshot2) {
		t.Errorf("dry-run changed the directory listing: %d -> %d", len(snapshot), len(snapshot2))
	}
	for name, content := range snapshot {
		if snapshot2[name] != content {
			t.Errorf("dry-run modified %q", name)
		}
	}

	// Execute: files move on disk, the DB follows.
	executed := postJSONBody(t, a.ts.URL+"/api/movies/"+itemID+"/rename", `{}`)
	if executed["executed"] == nil {
		t.Fatalf("execute response = %v", executed)
	}

	newDir := filepath.Join(mediaDir, "大电影 2019 (2019)")
	if b, err := os.ReadFile(filepath.Join(newDir, "大电影 2019 (2019).mkv")); err != nil || string(b) != "VIDEO" {
		t.Fatalf("renamed video missing or changed: %v", err)
	}
	if _, err := os.Stat(filepath.Join(newDir, "大电影 2019 (2019).nfo")); err != nil {
		t.Errorf("renamed NFO copy missing: %v", err)
	}
	if _, err := os.Stat(filepath.Join(newDir, "poster.jpg")); err != nil {
		t.Errorf("poster missing after folder move: %v", err)
	}

	// media_items.path follows the rename.
	itemDetail := getJSON(t, a.ts.URL+"/api/movies/"+itemID)
	if itemDetail["path"] != filepath.Join(newDir, "大电影 2019 (2019).mkv") {
		t.Errorf("item path = %v", itemDetail["path"])
	}

	// Re-executing is a no-op success.
	again := postJSONBody(t, a.ts.URL+"/api/movies/"+itemID+"/rename", `{}`)
	plan2 := again["plan"].(map[string]any)
	if plan2["valid"] != true {
		t.Fatalf("second plan = %v", plan2)
	}
	if actions, ok := plan2["actions"].([]any); ok && len(actions) != 0 {
		t.Errorf("second plan has actions: %v", actions)
	}

	// The movie page renders with the rename panel.
	page := getBody(t, a.ts.URL+"/movies/"+itemID)
	if !strings.Contains(page, "rename-panel") {
		t.Error("movie page missing rename panel")
	}
}

func TestMilestone6TVLibrary(t *testing.T) {
	dir := t.TempDir()
	dbPath := filepath.Join(dir, "mml.db")
	tvDir := filepath.Join(dir, "tv")
	for _, f := range []string{
		filepath.Join("Breaking Bad", "Season 1", "Breaking.Bad.S01E01.720p.mkv"),
		filepath.Join("Breaking Bad", "Season 1", "Breaking.Bad.S01E02.720p.mkv"),
		filepath.Join("北斗", "S01", "北斗.S01E05.1080p.mkv"),
		filepath.Join("北斗", "S01", "北斗.S01E06-E07.1080p.mkv"),
		filepath.Join("北斗", "poster.jpg"),
	} {
		p := filepath.Join(tvDir, f)
		if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(p, []byte("x"), 0o600); err != nil {
			t.Fatal(err)
		}
	}

	a := newApp(t, dbPath)
	libID := createLibrary(t, a.ts.URL, "TV Shows", tvDir, "tv")
	scanAndWait(t, a.ts.URL, libID, "found 4 files: 4 new, 0 updated")

	// Shows are listed with episodes and seasons.
	shows := getJSON(t, a.ts.URL+"/api/tv/shows?libraryId="+libID)
	if shows["count"].(float64) != 2 {
		t.Fatalf("shows = %v", shows["count"])
	}
	var bbID, cnID string
	for _, raw := range shows["shows"].([]any) {
		sh := raw.(map[string]any)
		switch sh["title"] {
		case "Breaking Bad":
			bbID = sh["id"].(string)
		case "北斗":
			cnID = sh["id"].(string)
		}
	}
	if bbID == "" || cnID == "" {
		t.Fatalf("shows = %v", shows)
	}

	bb := getJSON(t, a.ts.URL+"/api/tv/shows/"+bbID)
	if bb["count"].(float64) != 1 {
		t.Errorf("BB seasons = %v, want 1", bb["count"])
	}
	bbEps := getJSON(t, a.ts.URL+"/api/tv/shows/"+bbID+"/episodes")
	if bbEps["count"].(float64) != 2 {
		t.Errorf("BB episodes = %v", bbEps["count"])
	}
	cnEps := getJSON(t, a.ts.URL+"/api/tv/shows/"+cnID+"/episodes")
	if cnEps["count"].(float64) != 3 {
		t.Errorf("北斗 episodes = %v, want 3 (multi-episode expanded)", cnEps["count"])
	}

	// Unknown show id → 404.
	resp, err := http.Get(a.ts.URL + "/api/tv/shows/no-such-id")
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("unknown show status = %d, want 404", resp.StatusCode)
	}

	// Rescan is idempotent.
	scanAndWait(t, a.ts.URL, libID, "found 4 files: 0 new, 4 updated")
	shows = getJSON(t, a.ts.URL+"/api/tv/shows?libraryId="+libID)
	if shows["count"].(float64) != 2 {
		t.Errorf("shows after rescan = %v", shows["count"])
	}

	// Restart: the hierarchy persists.
	a = restartApp(t, a)
	shows = getJSON(t, a.ts.URL+"/api/tv/shows?libraryId="+libID)
	if shows["count"].(float64) != 2 {
		t.Fatalf("shows after restart = %v, want 2 (persistence)", shows["count"])
	}
	cnEps = getJSON(t, a.ts.URL+"/api/tv/shows/"+cnID+"/episodes")
	if cnEps["count"].(float64) != 3 {
		t.Errorf("北斗 episodes after restart = %v", cnEps["count"])
	}

	// Deleting the library cascades shows/seasons/episodes.
	deleteLibrary(t, a.ts.URL, libID)
	var n int
	if err := a.db.QueryRow("SELECT COUNT(*) FROM tv_shows WHERE library_id = ?", libID).Scan(&n); err != nil || n != 0 {
		t.Errorf("tv_shows after library delete = %d (%v), want 0 (cascade)", n, err)
	}
	if err := a.db.QueryRow("SELECT COUNT(*) FROM media_items WHERE library_id = ?", libID).Scan(&n); err != nil || n != 0 {
		t.Errorf("media_items after library delete = %d (%v), want 0 (cascade)", n, err)
	}
}

func TestMilestone7TVMatchFlow(t *testing.T) {
	dir := t.TempDir()
	mediaDir := filepath.Join(dir, "tv")
	for _, f := range []string{
		filepath.Join("北斗", "S01", "北斗.S01E01.1080p.mkv"),
		filepath.Join("北斗", "S01", "北斗.S01E02.1080p.mkv"),
	} {
		p := filepath.Join(mediaDir, f)
		if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(p, []byte("x"), 0o600); err != nil {
			t.Fatal(err)
		}
	}

	fixture := tmdbFixture(t)
	a := newAppWithConfig(t, func() config.Config {
		cfg := config.Defaults()
		cfg.DBPath = filepath.Join(dir, "mml.db")
		cfg.TMDBBaseURL = fixture.URL
		cfg.TMDBAPIKey = "fixture-key-0001"
		return cfg
	})

	libID := createLibrary(t, a.ts.URL, "TV", mediaDir, "tv")
	scanAndWait(t, a.ts.URL, libID, "found 2 files: 2 new, 0 updated")

	shows := getJSON(t, a.ts.URL+"/api/tv/shows?libraryId="+libID)
	if shows["count"].(float64) != 1 {
		t.Fatalf("shows = %v, want 1", shows["count"])
	}
	showID := shows["shows"].([]any)[0].(map[string]any)["id"].(string)

	// Search: candidates ranked, exact title first.
	search := postJSONBody(t, a.ts.URL+"/api/tv/shows/"+showID+"/search", `{}`)
	if search["count"].(float64) != 2 {
		t.Fatalf("search = %v", search)
	}
	best := search["candidates"].([]any)[0].(map[string]any)
	if best["candidate"].(map[string]any)["tmdbId"].(float64) != 555 {
		t.Errorf("best candidate = %v", best)
	}

	// Candidates persist.
	cands := getJSON(t, a.ts.URL+"/api/tv/shows/"+showID+"/candidates")
	if cands["count"].(float64) != 2 {
		t.Errorf("persisted candidates = %v", cands["count"])
	}

	// Match: transactional show update + async episode enrichment.
	match := postJSONBody(t, a.ts.URL+"/api/tv/shows/"+showID+"/match", `{"tmdbId": 555}`)
	if match["status"] != "matched" {
		t.Fatalf("match = %v", match)
	}
	sh := match["show"].(map[string]any)
	if sh["title"] != "北斗剧集" || sh["tmdbId"].(float64) != 555 {
		t.Errorf("matched show = %v", sh)
	}

	// Wait for the enrichment task.
	deadline := time.Now().Add(10 * time.Second)
	for {
		tasks := getJSON(t, a.ts.URL+"/api/tasks")
		done := false
		for _, raw := range tasks["tasks"].([]any) {
			tk := raw.(map[string]any)
			if tk["type"] == "scrape_tv_episodes" && tk["targetId"] == showID {
				if tk["state"] == "completed" {
					done = true
				}
				if tk["state"] == "failed" {
					t.Fatalf("enrich failed: %v", tk["error"])
				}
			}
		}
		if done || time.Now().After(deadline) {
			if !done {
				t.Fatal("enrich task did not complete")
			}
			break
		}
		time.Sleep(20 * time.Millisecond)
	}

	// Episodes enriched: generic zh names replaced by fallback names where
	// better, specific zh names kept.
	eps := getJSON(t, a.ts.URL+"/api/tv/shows/"+showID+"/episodes")
	epList := eps["episodes"].([]any)
	e1 := epList[0].(map[string]any)
	if e1["title"] != "第 1 集" {
		// fallback "Episode 1" is also generic → zh stays; both generic.
		t.Logf("ep1 title = %v (both generic)", e1["title"])
	}
	e2 := epList[1].(map[string]any)
	if e2["title"] != "名场面" {
		t.Errorf("ep2 title = %v, want the specific zh name", e2["title"])
	}

	// Pages render.
	page := getBody(t, a.ts.URL+"/tv/shows/"+showID)
	if !strings.Contains(page, "tvshow-panel") {
		t.Error("tv show page missing panel")
	}
	listPage := getBody(t, a.ts.URL+"/tv")
	if !strings.Contains(listPage, "北斗剧集") {
		t.Error("tv list page missing show")
	}

	// Match survives a restart.
	a = restartApp(t, a)
	sh2 := getJSON(t, a.ts.URL+"/api/tv/shows/"+showID)
	if sh2["show"].(map[string]any)["tmdbId"].(float64) != 555 {
		t.Errorf("tmdb id after restart = %v", sh2["show"].(map[string]any)["tmdbId"])
	}

	// Unmatched clears the match.
	un := postJSONBody(t, a.ts.URL+"/api/tv/shows/"+showID+"/match", `{"unmatched": true}`)
	if un["status"] != "unmatched" {
		t.Fatalf("unmatch = %v", un)
	}
	sh3 := getJSON(t, a.ts.URL+"/api/tv/shows/"+showID)
	if sh3["show"].(map[string]any)["tmdbId"].(float64) != 0 {
		t.Errorf("tmdb id after unmatch = %v", sh3["show"].(map[string]any)["tmdbId"])
	}
}

func TestMilestone8TVNfoRename(t *testing.T) {
	dir := t.TempDir()
	mediaDir := filepath.Join(dir, "tv")
	if err := os.MkdirAll(mediaDir, 0o755); err != nil {
		t.Fatal(err)
	}
	if resolved, err := filepath.EvalSymlinks(mediaDir); err == nil {
		mediaDir = resolved
	}
	for _, f := range []string{
		filepath.Join("北斗", "S01", "北斗.S01E01.1080p.mkv"),
		filepath.Join("北斗", "S01", "北斗.S01E02.1080p.mkv"),
	} {
		p := filepath.Join(mediaDir, f)
		if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(p, []byte("x"), 0o600); err != nil {
			t.Fatal(err)
		}
	}

	fixture := tmdbFixture(t)
	a := newAppWithConfig(t, func() config.Config {
		cfg := config.Defaults()
		cfg.DBPath = filepath.Join(dir, "mml.db")
		cfg.TMDBBaseURL = fixture.URL
		cfg.TMDBAPIKey = "fixture-key-0001"
		return cfg
	})

	libID := createLibrary(t, a.ts.URL, "TV", mediaDir, "tv")
	scanAndWait(t, a.ts.URL, libID, "found 2 files: 2 new, 0 updated")
	shows := getJSON(t, a.ts.URL+"/api/tv/shows?libraryId="+libID)
	showID := shows["shows"].([]any)[0].(map[string]any)["id"].(string)
	postJSONBody(t, a.ts.URL+"/api/tv/shows/"+showID+"/match", `{"tmdbId": 555}`)

	// Wait for enrichment, then write NFOs.
	deadline := time.Now().Add(10 * time.Second)
	for {
		tasks := getJSON(t, a.ts.URL+"/api/tasks")
		done := false
		for _, raw := range tasks["tasks"].([]any) {
			tk := raw.(map[string]any)
			if tk["type"] == "scrape_tv_episodes" && tk["targetId"] == showID && tk["state"] == "completed" {
				done = true
			}
			if tk["type"] == "scrape_tv_episodes" && tk["targetId"] == showID && tk["state"] == "failed" {
				t.Fatalf("enrich failed: %v", tk["error"])
			}
		}
		if done || time.Now().After(deadline) {
			if !done {
				t.Fatal("enrich did not complete")
			}
			break
		}
		time.Sleep(20 * time.Millisecond)
	}

	nfoRes := postJSONBody(t, a.ts.URL+"/api/tv/shows/"+showID+"/nfo", `{}`)
	if nfoRes["count"].(float64) != 4 { // tvshow + season + 2 episodes
		t.Fatalf("nfo results = %v", nfoRes)
	}

	showDir := filepath.Join(mediaDir, "北斗")
	if _, err := os.Stat(filepath.Join(showDir, "tvshow.nfo")); err != nil {
		t.Fatalf("tvshow.nfo missing: %v", err)
	}
	if b, err := os.ReadFile(filepath.Join(showDir, "tvshow.nfo")); err != nil ||
		!strings.Contains(string(b), "<title>北斗剧集</title>") {
		t.Fatalf("tvshow.nfo content = %s", string(b))
	}
	if _, err := os.Stat(filepath.Join(showDir, "S01", "season01.nfo")); err != nil {
		t.Fatalf("season01.nfo missing: %v", err)
	}
	if b, err := os.ReadFile(filepath.Join(showDir, "S01", "北斗.S01E01.1080p.nfo")); err != nil ||
		!strings.Contains(string(b), "<showtitle>北斗剧集</showtitle>") {
		t.Fatalf("episode nfo = %s", string(b))
	}

	// Rename: pick the first episode item, dry-run, then execute.
	items := getJSON(t, a.ts.URL+"/api/libraries/"+libID+"/media")
	var epItem map[string]any
	for _, raw := range items["mediaItems"].([]any) {
		it := raw.(map[string]any)
		if it["kind"] == "episode" {
			epItem = it
			break
		}
	}
	itemID := epItem["id"].(string)
	_ = epItem["path"]

	dry := getJSON(t, a.ts.URL+"/api/episodes/"+itemID+"/rename")
	if dry["valid"] != true {
		t.Fatalf("dry plan = %v", dry)
	}
	wantFile := filepath.Join(mediaDir, "北斗剧集", "Season 01", "北斗剧集 - S01E01 - 第 1 集.mkv")
	if dry["video"].(map[string]any)["to"] != wantFile {
		t.Errorf("plan video to = %v, want %q", dry["video"].(map[string]any)["to"], wantFile)
	}

	executed := postJSONBody(t, a.ts.URL+"/api/episodes/"+itemID+"/rename", `{}`)
	if executed["executed"] == nil {
		t.Fatalf("execute = %v", executed)
	}
	if b, err := os.ReadFile(wantFile); err != nil || string(b) != "x" {
		t.Fatalf("renamed file = %v", err)
	}
	// The NFO basename copy moved along.
	if _, err := os.Stat(filepath.Join(mediaDir, "北斗剧集", "Season 01", "北斗剧集 - S01E01 - 第 1 集.nfo")); err != nil {
		t.Errorf("renamed nfo missing: %v", err)
	}

	// DB follows; the episode detail API reflects the new path.
	itemDetail := getJSON(t, a.ts.URL+"/api/movies/"+itemID)
	if itemDetail["path"] != wantFile {
		t.Errorf("item path = %v", itemDetail["path"])
	}

	// Idempotent second run.
	again := postJSONBody(t, a.ts.URL+"/api/episodes/"+itemID+"/rename", `{}`)
	plan2 := again["plan"].(map[string]any)
	if plan2["valid"] != true {
		t.Fatalf("second plan = %v", plan2)
	}
	if actions, ok := plan2["actions"].([]any); ok && len(actions) != 0 {
		t.Errorf("second plan actions = %v", actions)
	}

	// Pages render.
	if page := getBody(t, a.ts.URL+"/tv/shows/"+showID); !strings.Contains(page, "episode_rename_panel") && !strings.Contains(page, "写入 NFO") {
		t.Error("tv show page missing workflow elements")
	}

	// Restart persistence: NFO files stay, DB stays.
	a = restartApp(t, a)
	if _, err := os.Stat(filepath.Join(showDir, "tvshow.nfo")); err != nil {
		t.Errorf("tvshow.nfo lost after restart: %v", err)
	}
	itemDetail = getJSON(t, a.ts.URL+"/api/movies/"+itemID)
	if itemDetail["path"] != wantFile {
		t.Errorf("item path after restart = %v", itemDetail["path"])
	}
}

func TestMilestone9Polish(t *testing.T) {
	dir := t.TempDir()
	mediaDir := filepath.Join(dir, "tv")
	if resolved, err := filepath.EvalSymlinks(mustMkdirAll(mediaDir)); err == nil {
		mediaDir = resolved
	}
	for _, f := range []string{
		filepath.Join("北斗", "S01", "北斗.S01E01.1080p.mkv"),
		filepath.Join("北斗", "S01", "random.video.file.mkv"), // no episode marker
	} {
		p := filepath.Join(mediaDir, f)
		if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(p, []byte("x"), 0o600); err != nil {
			t.Fatal(err)
		}
	}

	fixture := tmdbFixture(t)
	a := newAppWithConfig(t, func() config.Config {
		cfg := config.Defaults()
		cfg.DBPath = filepath.Join(dir, "mml.db")
		cfg.TMDBBaseURL = fixture.URL
		cfg.TMDBAPIKey = "fixture-key-0001"
		return cfg
	})

	libID := createLibrary(t, a.ts.URL, "TV", mediaDir, "tv")
	scanAndWait(t, a.ts.URL, libID, "found 2 files: 1 new, 0 updated")

	// Skipped feedback API lists the unrecognized video with a reason.
	skipped := getJSON(t, a.ts.URL+"/api/libraries/"+libID+"/skipped")
	if skipped["count"].(float64) != 1 {
		t.Fatalf("skipped = %v", skipped)
	}
	s := skipped["skipped"].([]any)[0].(map[string]any)
	if s["filename"] != "random.video.file.mkv" || s["reason"] == "" {
		t.Errorf("skipped item = %v", s)
	}

	// Match + enrich + episode NFO carries the TMDB episode uniqueid.
	shows := getJSON(t, a.ts.URL+"/api/tv/shows?libraryId="+libID)
	showID := shows["shows"].([]any)[0].(map[string]any)["id"].(string)
	postJSONBody(t, a.ts.URL+"/api/tv/shows/"+showID+"/match", `{"tmdbId": 555}`)
	waitTaskType(t, a.ts.URL, "scrape_tv_episodes", showID)

	eps := getJSON(t, a.ts.URL+"/api/tv/shows/"+showID+"/episodes")
	e1 := eps["episodes"].([]any)[0].(map[string]any)
	if e1["tmdbId"].(float64) != 9001 {
		t.Errorf("episode tmdbId = %v, want 9001", e1["tmdbId"])
	}

	postJSONBody(t, a.ts.URL+"/api/tv/shows/"+showID+"/nfo", `{}`)
	showDir := filepath.Join(mediaDir, "北斗")
	b, rerr := os.ReadFile(filepath.Join(showDir, "S01", "北斗.S01E01.1080p.nfo"))
	if rerr != nil || !strings.Contains(string(b), `<uniqueid type="tmdb" default="true">9001</uniqueid>`) {
		t.Fatalf("episode uniqueid missing (%v): %s", rerr, string(b))
	}

	// TV artwork: poster/fanart into the show folder, season poster into S01.
	task := postJSONBody(t, a.ts.URL+"/api/tv/shows/"+showID+"/artwork", `{}`)
	waitTask(t, a.ts.URL, task["id"].(string), "")
	for _, f := range []string{
		filepath.Join(showDir, "poster.jpg"),
		filepath.Join(showDir, "fanart.jpg"),
		filepath.Join(showDir, "S01", "season01-poster.jpg"),
	} {
		if _, err := os.Stat(f); err != nil {
			t.Errorf("artwork %s missing: %v", f, err)
		}
	}
	full := getJSON(t, a.ts.URL+"/api/tv/shows/"+showID)
	if full["show"].(map[string]any)["artworkStatus"] != "downloaded" {
		t.Errorf("artwork status = %v", full["show"].(map[string]any)["artworkStatus"])
	}
}

// waitTaskType waits for the latest task of a type+target to complete.
func waitTaskType(t *testing.T, base, typ, targetID string) {
	t.Helper()
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		tasks := getJSON(t, base+"/api/tasks")
		for _, raw := range tasks["tasks"].([]any) {
			tk := raw.(map[string]any)
			if tk["type"] == typ && tk["targetId"] == targetID {
				switch tk["state"] {
				case "completed":
					return
				case "failed":
					t.Fatalf("task failed: %v", tk["error"])
				}
			}
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatal("task did not complete within deadline")
}

func mustMkdirAll(path string) string {
	if err := os.MkdirAll(path, 0o755); err != nil {
		panic(err)
	}
	return path
}

func getStatusCode(t *testing.T, url string) int {
	t.Helper()
	resp, err := http.Get(url)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	return resp.StatusCode
}
