package http

import (
	"io"
	"net/http"
	"strings"
	"testing"

	"media-manager-lite/internal/movie"
)

func TestMovieListShowsScrapedPoster(t *testing.T) {
	ts, deps := newTestServerWithDeps(t, nil, "")
	libID := mediaDirWithFiles(t, ts)
	task := scanLibraryViaAPI(t, ts, libID)
	waitForTask(t, ts, task["id"].(string), "")
	items, err := deps.Media.ListByLibrary(t.Context(), libID)
	if err != nil || len(items) != 2 {
		t.Fatalf("items = %v, err = %v", items, err)
	}
	matched, err := deps.Movies.ConfirmMatch(t.Context(), items[0].ID, movie.Movie{LibraryID: libID, TMDBID: 42, Title: "Movie"})
	if err != nil {
		t.Fatal(err)
	}
	const poster = "https://image.example/poster.jpg"
	if err := deps.Movies.UpdateArtwork(t.Context(), matched.ID, &movie.Artwork{PosterURL: poster}); err != nil {
		t.Fatal(err)
	}
	for _, path := range []string{"/movies", "/libraries/" + libID + "/match"} {
		resp, err := http.Get(ts.URL + path)
		if err != nil {
			t.Fatal(err)
		}
		body, err := io.ReadAll(resp.Body)
		resp.Body.Close()
		if err != nil {
			t.Fatal(err)
		}
		if resp.StatusCode != http.StatusOK || !strings.Contains(string(body), `src="`+poster+`"`) || strings.Count(string(body), `class="movie-poster"`) != 1 {
			t.Errorf("GET %s: status %d, body %s", path, resp.StatusCode, body)
		}
	}
}
