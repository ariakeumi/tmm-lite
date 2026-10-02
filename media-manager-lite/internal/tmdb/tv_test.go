package tmdb

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestTVClientEndpoints(t *testing.T) {
	c := newFixtureClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Query().Get("api_key") != "fixture-key-0001" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		q := r.URL.Query()
		switch {
		case r.URL.Path == "/search/tv":
			if q.Get("language") != "zh-CN" {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			w.Write([]byte(`{"page":1,"total_pages":1,"results":[
				{"id":111,"name":"北斗剧集","original_name":"北斗","first_air_date":"2021-05-01","poster_path":"/t.jpg","vote_average":8.1,"vote_count":99},
				{"id":222,"name":"Unrelated","original_name":"Unrelated","first_air_date":"1999-01-01","vote_average":5,"vote_count":1}]}`))
		case r.URL.Path == "/tv/111":
			if q.Get("append_to_response") != "translations,credits,external_ids,content_ratings,episode_groups" {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			w.Write([]byte(`{
				"id":111,"name":"北斗剧集","original_name":"北斗","overview":"requested overview",
				"first_air_date":"2021-05-01","status":"Returning Series","vote_average":8.1,
				"vote_count":99,"original_language":"cn","number_of_seasons":2,"number_of_episodes":10,
				"genres":[{"id":18,"name":"Drama"}],
				"content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"}]},
				"episode_groups":{"results":[{"id":"g1","name":"Absolute"}]},
				"credits":{"cast":[{"id":5,"name":"Actor","character":"Lead"}]},
				"translations":{"translations":[
					{"iso_639_1":"en","iso_3166_1":"US","data":{"title":"Polaris Show","overview":"english overview"}},
					{"iso_639_1":"zh","iso_3166_1":"CN","data":{"title":"北斗剧集","overview":"中国大陆简介"}}]},
				"external_ids":{"imdb_id":"tt123"}}`))
		case r.URL.Path == "/tv/111/season/1":
			if q.Get("language") != "zh-CN" {
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			w.Write([]byte(`{"id":900,"name":"第 1 季","season_number":1,"episodes":[
				{"id":9001,"name":"第 1 集","overview":"","season_number":1,"episode_number":1,"air_date":"2021-05-01","runtime":45,"vote_average":7.5,"vote_count":10},
				{"id":9002,"name":"真名场面","overview":"好戏","season_number":1,"episode_number":2,"air_date":"2021-05-08","runtime":47,"vote_average":8.0,"vote_count":12}]}`))
		case r.URL.Path == "/tv/111/season/1/episode/1":
			w.Write([]byte(`{"id":9001,"name":"第 1 集","season_number":1,"episode_number":1,"runtime":45}`))
		case r.URL.Path == "/tv/episode_group/g1":
			w.Write([]byte(`{"id":"g1","name":"Absolute","groups":[{"type":2,"order":0,"episodes":[
				{"id":9001,"season_number":1,"episode_number":1,"order":0},
				{"id":9002,"season_number":1,"episode_number":2,"order":1}]}]}`))
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	})
	ctx := context.Background()

	// Search sends zh-CN and maps results.
	results, err := c.SearchTV(ctx, "北斗", "zh-CN", 0)
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 2 || results[0].TMDBID != 111 {
		t.Fatalf("search results = %+v", results)
	}
	if y := results[0].Year(); y != 2021 {
		t.Errorf("Year() = %d, want 2021", y)
	}

	// Detail + translation fallback chain.
	d, err := c.TVDetail(ctx, 111, "zh-CN")
	if err != nil {
		t.Fatal(err)
	}
	best := PickBestTVTitle(d, "zh-CN", "en-US")
	if best.Title != "北斗剧集" || best.Source != "exact" {
		t.Errorf("best = %+v", best)
	}
	if best.EnglishTitle != "Polaris Show" {
		t.Errorf("english title = %q", best.EnglishTitle)
	}
	if got := d.Certification("US"); got != "TV-MA" {
		t.Errorf("certification = %q", got)
	}

	// Season with EPISODE_TRANS fallback: generic zh names replaced.
	svc := NewService(nil, "fixture-key-0001", c.baseURL)
	svc.SetHTTPClient(c.http)
	season, err := svc.TVSeasonWithFallback(ctx, 111, 1)
	if err != nil {
		t.Fatal(err)
	}
	// The zh season only has generic names ("第 1 集") — but the fallback
	// language (en-US) is not served by this fixture, so names stay as-is;
	// the merge path is exercised by the double-language fixture below.
	if len(season.Episodes) != 2 {
		t.Fatalf("season episodes = %d", len(season.Episodes))
	}

	// Episode detail endpoint.
	ep, err := c.TVEpisode(ctx, 111, 1, 1, "zh-CN")
	if err != nil {
		t.Fatal(err)
	}
	if ep.TMDBID != 9001 {
		t.Errorf("episode id = %d", ep.TMDBID)
	}

	// Episode groups + mapping (order normalized to 1-based).
	eg, err := c.EpisodeGroup(ctx, "g1")
	if err != nil {
		t.Fatal(err)
	}
	m := eg.Mapping()
	if p := m[9002]; p.Order != 2 {
		t.Errorf("group order = %+v, want 2", p)
	}
}

func TestTVSeasonWithFallbackReplacesGenericNames(t *testing.T) {
	c := newFixtureClient(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if r.URL.Query().Get("language") == "zh-CN" {
			w.Write([]byte(`{"id":900,"name":"第 1 季","season_number":1,"episodes":[
				{"id":9001,"name":"第 1 集","season_number":1,"episode_number":1},
				{"id":9002,"name":"真名场面","season_number":1,"episode_number":2}]}`))
			return
		}
		if r.URL.Query().Get("language") == "en-US" {
			w.Write([]byte(`{"id":900,"name":"Season 1","season_number":1,"episodes":[
				{"id":9001,"name":"Episode 1","season_number":1,"episode_number":1},
				{"id":9002,"name":"Real Scene","season_number":1,"episode_number":2}]}`))
			return
		}
		w.WriteHeader(http.StatusNotFound)
	})
	svc := NewService(nil, "fixture-key-0001", c.baseURL)
	svc.SetHTTPClient(c.http)

	season, err := svc.TVSeasonWithFallback(context.Background(), 111, 1)
	if err != nil {
		t.Fatal(err)
	}
	// Generic zh name + generic en name → nothing better available, keep.
	if season.Episodes[0].Name != "第 1 集" {
		t.Errorf("ep1 = %q (fallback also generic)", season.Episodes[0].Name)
	}
	// Specific zh name survives untouched.
	if season.Episodes[1].Name != "真名场面" {
		t.Errorf("ep2 = %q, want the specific zh name", season.Episodes[1].Name)
	}

	// Now a fixture where the fallback name is specific: it must replace.
	c2 := newFixtureClient(t, func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if r.URL.Query().Get("language") == "zh-CN" {
			w.Write([]byte(`{"id":900,"name":"第 1 季","season_number":1,"episodes":[
				{"id":9001,"name":"第 1 集","season_number":1,"episode_number":1}]}`))
			return
		}
		w.Write([]byte(`{"id":900,"name":"Season 1","season_number":1,"episodes":[
			{"id":9001,"name":"Pilot","overview":"the pilot","season_number":1,"episode_number":1}]}`))
	})
	svc2 := NewService(nil, "fixture-key-0001", c2.baseURL)
	svc2.SetHTTPClient(c2.http)
	season2, err := svc2.TVSeasonWithFallback(context.Background(), 111, 1)
	if err != nil {
		t.Fatal(err)
	}
	if season2.Episodes[0].Name != "Pilot" {
		t.Errorf("ep1 = %q, want the specific fallback name", season2.Episodes[0].Name)
	}
	if season2.Name != "Season 1" {
		t.Errorf("season name = %q, want the fallback season name", season2.Name)
	}
}

func TestTVAPIKeyNeverLeaks(t *testing.T) {
	c := newFixtureClient(t, func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	})
	svc := NewService(nil, "fixture-key-0001", c.baseURL)
	svc.SetHTTPClient(c.http)
	_, err := svc.SearchTVByText(context.Background(), "Show", 0)
	if err == nil {
		t.Fatal("expected an error")
	}
	if strings.Contains(err.Error(), "fixture-key-0001") {
		t.Errorf("API key leaked: %v", err)
	}
	_, err = svc.TVDetailWithFallback(context.Background(), 1)
	if err != nil {
		if strings.Contains(err.Error(), "fixture-key-0001") {
			t.Errorf("API key leaked: %v", err)
		}
	}
	// Unauthenticated search maps to a precise error.
	unauth := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer unauth.Close()
	bad := NewService(nil, "wrong-key", unauth.URL)
	bad.SetHTTPClient(unauth.Client())
	_, err = bad.SearchTVByText(context.Background(), "Show", 0)
	if !errors.Is(err, ErrUnauthorized) {
		t.Errorf("error = %v, want ErrUnauthorized", err)
	}
}
