package artwork

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"media-manager-lite/internal/database"
	"media-manager-lite/internal/library"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/scanner"
	"media-manager-lite/internal/settings"
	"media-manager-lite/internal/tmdb"
	"media-manager-lite/internal/tv"
)

// newHardenFixture builds the full service stack for symlink regression
// tests: a movie library whose scanned item lives in a directory the user
// later swaps for a symlink pointing outside the library.
func newHardenFixture(t *testing.T) (*Service, *media.Store, *movie.Store, *tv.Store, string, string, string) {
	t.Helper()
	dir := t.TempDir()
	db, err := database.Open(filepath.Join(dir, "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	if err := database.Migrate(context.Background(), db.DB); err != nil {
		t.Fatal(err)
	}

	root := filepath.Join(dir, "library")
	if err := os.MkdirAll(root, 0o755); err != nil {
		t.Fatal(err)
	}
	videoDir := filepath.Join(root, "Movie.2019.1080p.GROUP")
	if err := os.MkdirAll(videoDir, 0o755); err != nil {
		t.Fatal(err)
	}
	video := filepath.Join(videoDir, "Movie.2019.1080p.GROUP.mkv")
	if err := os.WriteFile(video, []byte("v"), 0o600); err != nil {
		t.Fatal(err)
	}
	if resolved, err := filepath.EvalSymlinks(root); err == nil {
		root = resolved
		videoDir = filepath.Join(root, "Movie.2019.1080p.GROUP")
	}

	libs := library.NewService(library.NewStore(db.DB))
	lib, err := libs.Create(context.Background(), "Movies", root, library.TypeMovie)
	if err != nil {
		t.Fatal(err)
	}

	mediaStore := media.NewStore(db.DB)
	movieStore := movie.NewStore(db.DB)
	movies := movieStore
	tvStore := tv.NewStore(db.DB)
	settingsStore := settings.NewStore(db.DB)
	tm := tmdb.NewService(settingsStore, "fixture-key-0001", "http://127.0.0.1:1")
	svc := NewService(tm, mediaStore, movieStore, tvStore, libs)

	// Seed the media item + matched movie through the normal scan flow.
	sc := scanner.NewService(mediaStore, tvStore, movies)
	stats, err := sc.ScanLibrary(context.Background(), lib)
	if err != nil {
		t.Fatal(err)
	}
	if stats.New != 1 {
		t.Fatalf("scan = %+v", stats)
	}
	items, err := mediaStore.ListByLibrary(context.Background(), lib.ID)
	if err != nil {
		t.Fatal(err)
	}
	m := movie.Movie{LibraryID: lib.ID, TMDBID: 42, Title: "Movie", Year: 2019}
	confirmed, err := movieStore.ConfirmMatch(context.Background(), items[0].ID, m)
	if err != nil {
		t.Fatal(err)
	}
	_, _ = confirmed, video
	return svc, mediaStore, movieStore, tvStore, root, videoDir, items[0].ID
}

func TestArtworkRefusesSymlinkEscapedDirectory(t *testing.T) {
	svc, _, _, _, root, videoDir, itemID := newHardenFixture(t)

	// The user swaps the movie directory for a symlink to outside the root.
	outside := t.TempDir()
	// The scanned directory contains the video; move it out to free the
	// name for the symlink swap.
	if err := os.RemoveAll(videoDir); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(outside, videoDir); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	if !withinRootForTest(root, videoDir) {
		t.Log("informational: raw path still inside root string-wise; resolution must catch it")
	}

	_, err := svc.Download(context.Background(), itemID)
	if err == nil {
		t.Fatal("artwork download must refuse a directory symlinked outside the root")
	}
	// Nothing may have been written outside.
	entries, _ := os.ReadDir(outside)
	if len(entries) != 0 {
		t.Errorf("files written outside the library root: %v", entries)
	}
}

func TestArtworkSucceedsInsideRoot(t *testing.T) {
	svc, _, _, _, _, videoDir, _ := newHardenFixture(t)
	// A local http server serving a PNG.
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Write(pngBytes)
	}))
	defer srv.Close()
	// Point the TMDB service at the fixture and the artwork base at it via
	// a fake image URL is complex; instead exercise the download primitive
	// directly within the root.
	dest := filepath.Join(videoDir, PosterFile)
	if err := svc.download(context.Background(), srv.URL+"/p.jpg", dest); err != nil {
		t.Fatalf("in-root download: %v", err)
	}
	if _, err := os.Stat(dest); err != nil {
		t.Fatal(err)
	}
}

// withinRootForTest mirrors paths.WithinRoot for assertions.
func withinRootForTest(root, target string) bool {
	if target == root {
		return true
	}
	return len(target) > len(root) && target[:len(root)] == root && target[len(root)] == filepath.Separator
}
