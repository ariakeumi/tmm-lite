package scanner

import (
	"context"
	"database/sql"
	"os"
	"path/filepath"
	"testing"
	"time"

	"media-manager-lite/internal/database"
	"media-manager-lite/internal/library"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/tv"
)

func newTestMediaStore(t *testing.T) (*media.Store, *tv.Store, *movie.Store, *sql.DB) {
	t.Helper()
	db, err := database.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	if err := database.Migrate(context.Background(), db.DB); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	return media.NewStore(db.DB), tv.NewStore(db.DB), movie.NewStore(db.DB), db.DB
}

func seedLibrary(t *testing.T, db *sql.DB, id string) {
	t.Helper()
	_, err := db.ExecContext(context.Background(),
		`INSERT INTO libraries (id, name, path, type, created_at, updated_at)
		 VALUES (?, 'Test', '/tmp/test', 'movie', '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')`, id)
	if err != nil {
		t.Fatal(err)
	}
}

func write(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}
}

func names(cands []Candidate) []string {
	out := make([]string, len(cands))
	for i, c := range cands {
		out[i] = c.Name
	}
	return out
}

func contains(list []string, s string) bool {
	for _, v := range list {
		if v == s {
			return true
		}
	}
	return false
}

func TestWalkRecursiveAndFilters(t *testing.T) {
	root := t.TempDir()
	write(t, filepath.Join(root, "Movie A 2019.mkv"), "v")
	write(t, filepath.Join(root, "sub", "Movie.B.2010.mp4"), "v")
	write(t, filepath.Join(root, "deep", "deeper", "Movie.CD2.avi"), "v")
	write(t, filepath.Join(root, "Trailer.Park.Boys.2001.mkv"), "v")

	// Non-media and junk files that must be filtered out.
	write(t, filepath.Join(root, "notes.nfo"), "x")
	write(t, filepath.Join(root, "cover.jpg"), "x")
	write(t, filepath.Join(root, "movie.nfo"), "x")
	write(t, filepath.Join(root, "subs", "movie.eng.srt"), "x")
	write(t, filepath.Join(root, "subs", "movie.ass"), "x")
	write(t, filepath.Join(root, "sample.mkv"), "x")
	write(t, filepath.Join(root, "Movie.trailer.mkv"), "x")
	write(t, filepath.Join(root, ".hidden.mkv"), "x")
	write(t, filepath.Join(root, ".hidden", "Movie.X.mkv"), "x")

	cands, err := Walk(root)
	if err != nil {
		t.Fatal(err)
	}
	got := names(cands)
	want := []string{"Movie A 2019.mkv", "Movie.B.2010.mp4", "Movie.CD2.avi", "Trailer.Park.Boys.2001.mkv"}
	if len(got) != len(want) {
		t.Fatalf("Walk() = %v, want %v", got, want)
	}
	for _, w := range want {
		if !contains(got, w) {
			t.Errorf("Walk() missing %q; got %v", w, got)
		}
	}

	for _, c := range cands {
		if !filepath.IsAbs(c.Path) {
			t.Errorf("candidate path %q is not absolute", c.Path)
		}
		if c.Size <= 0 {
			t.Errorf("candidate %q has size %d, want >0", c.Name, c.Size)
		}
		if c.ModTime.IsZero() || time.Since(c.ModTime) > time.Hour {
			t.Errorf("candidate %q has implausible mod time %v", c.Name, c.ModTime)
		}
	}
}

func TestWalkEmptyDirectory(t *testing.T) {
	cands, err := Walk(t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	if len(cands) != 0 {
		t.Errorf("Walk(empty) = %v, want none", cands)
	}
}

func TestWalkRootDoesNotExist(t *testing.T) {
	if _, err := Walk(filepath.Join(t.TempDir(), "missing")); err == nil {
		t.Error("Walk() on missing root should fail")
	}
}

func TestWalkRootIsFile(t *testing.T) {
	root := filepath.Join(t.TempDir(), "file.mkv")
	write(t, root, "v")
	if _, err := Walk(root); err == nil {
		t.Error("Walk() on a file root should fail")
	}
}

func TestWalkSymlinkEscapeBlocked(t *testing.T) {
	root := t.TempDir()
	outside := t.TempDir()

	write(t, filepath.Join(outside, "secret.mp4"), "v")
	write(t, filepath.Join(outside, "vault", "Hidden.2020.mkv"), "v")
	write(t, filepath.Join(root, "Inside.2019.mkv"), "v")

	// Symlinked file pointing outside the library root.
	if err := os.Symlink(filepath.Join(outside, "secret.mp4"), filepath.Join(root, "evil.mp4")); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	// Symlinked directory pointing outside (must not be traversed).
	if err := os.Symlink(filepath.Join(outside, "vault"), filepath.Join(root, "vault")); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	// Symlink to a file inside the root: allowed.
	if err := os.Symlink(filepath.Join(root, "Inside.2019.mkv"), filepath.Join(root, "alias.mkv")); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}

	cands, err := Walk(root)
	if err != nil {
		t.Fatal(err)
	}
	got := names(cands)
	for _, c := range cands {
		if c.Name == "evil.mp4" || c.Name == "Hidden.2020.mkv" {
			t.Errorf("symlink escape not blocked: %q in results %v", c.Name, got)
		}
	}
	if !contains(got, "Inside.2019.mkv") || !contains(got, "alias.mkv") {
		t.Errorf("in-root files missing from results: %v", got)
	}
}

func TestScanLibraryPersistsAndIsIdempotent(t *testing.T) {
	store, tvStore, movieStore, db := newTestMediaStore(t)
	seedLibrary(t, db, "lib-1")
	svc := NewService(store, tvStore, movieStore)
	ctx := context.Background()

	dir := t.TempDir()
	write(t, filepath.Join(dir, "Movie.One.2019.1080p.BluRay.x264.mkv"), "v")
	write(t, filepath.Join(dir, "Movie.Two.2020.2160p.WEB-DL.x265.mkv"), "v")
	write(t, filepath.Join(dir, "notes.nfo"), "x")

	lib := library.Library{ID: "lib-1", Name: "Movies", Path: dir, Type: library.TypeMovie}

	stats, err := svc.ScanLibrary(ctx, lib)
	if err != nil {
		t.Fatal(err)
	}
	if stats.Found != 2 || stats.New != 2 || stats.Updated != 0 {
		t.Errorf("first scan stats = %+v, want found 2 new 2 updated 0", stats)
	}

	items, err := store.ListByLibrary(ctx, lib.ID)
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 2 {
		t.Fatalf("media items = %d, want 2", len(items))
	}
	byName := map[string]media.Item{}
	for _, it := range items {
		byName[it.Filename] = it
		if it.Kind != media.KindMovie || it.Status != media.StatusNew {
			t.Errorf("item %s kind/status = %s/%s", it.Filename, it.Kind, it.Status)
		}
		if it.Parsed == nil || it.ParsedTitle == "" {
			t.Errorf("item %s missing parsed metadata: %+v", it.Filename, it)
		}
		if it.ModTime == "" || it.Size <= 0 {
			t.Errorf("item %s missing file statistics: %+v", it.Filename, it)
		}
	}
	if byName["Movie.One.2019.1080p.BluRay.x264.mkv"].ParsedTitle != "Movie One" {
		t.Errorf("parsed title = %q, want Movie One", byName["Movie.One.2019.1080p.BluRay.x264.mkv"].ParsedTitle)
	}
	if byName["Movie.Two.2020.2160p.WEB-DL.x265.mkv"].ParsedYear != 2020 {
		t.Errorf("parsed year = %d, want 2020", byName["Movie.Two.2020.2160p.WEB-DL.x265.mkv"].ParsedYear)
	}

	// Rescanning must update, not duplicate.
	stats, err = svc.ScanLibrary(ctx, lib)
	if err != nil {
		t.Fatal(err)
	}
	if stats.New != 0 || stats.Updated != 2 {
		t.Errorf("rescan stats = %+v, want new 0 updated 2", stats)
	}
	n, err := store.CountByLibrary(ctx, lib.ID)
	if err != nil {
		t.Fatal(err)
	}
	if n != 2 {
		t.Errorf("media items after rescan = %d, want 2 (no duplicates)", n)
	}
}

func TestScanLibraryErrors(t *testing.T) {
	store, tvStore, movieStore, _ := newTestMediaStore(t)
	svc := NewService(store, tvStore, movieStore)
	ctx := context.Background()

	// Missing directory.
	if _, err := svc.ScanLibrary(ctx, library.Library{ID: "x", Path: filepath.Join(t.TempDir(), "gone")}); err == nil {
		t.Error("scan of missing path should fail")
	}
	// A file instead of a directory.
	f := filepath.Join(t.TempDir(), "file")
	write(t, f, "x")
	if _, err := svc.ScanLibrary(ctx, library.Library{ID: "x", Path: f}); err == nil {
		t.Error("scan of a file path should fail")
	}
}

func TestScanLibraryUnreadableDirectoryFails(t *testing.T) {
	store, tvStore, movieStore, _ := newTestMediaStore(t)
	svc := NewService(store, tvStore, movieStore)

	dir := t.TempDir()
	restricted := filepath.Join(dir, "secret")
	if err := os.Mkdir(restricted, 0o000); err != nil {
		t.Skipf("cannot create mode-000 directory: %v", err)
	}
	t.Cleanup(func() { _ = os.Chmod(restricted, 0o755) })

	write(t, filepath.Join(dir, "Movie.2019.mkv"), "v")
	if _, err := svc.ScanLibrary(context.Background(), library.Library{ID: "x", Path: dir}); err == nil {
		t.Error("scan with unreadable subdirectory should fail")
	}
}

func TestScanKeepsRemovedFiles(t *testing.T) {
	store, tvStore, movieStore, db := newTestMediaStore(t)
	seedLibrary(t, db, "lib-1")
	svc := NewService(store, tvStore, movieStore)
	ctx := context.Background()

	dir := t.TempDir()
	f := filepath.Join(dir, "Gone.Movie.2019.1080p.BluRay.x264.mkv")
	write(t, f, "v")
	lib := library.Library{ID: "lib-1", Name: "Movies", Path: dir, Type: library.TypeMovie}

	if _, err := svc.ScanLibrary(ctx, lib); err != nil {
		t.Fatal(err)
	}
	// Match the item (so movie_id is set), then the user removes the file
	// from disk; a rescan removes the item from the list and drops the
	// now-unreferenced movie row.
	items, err := store.ListByLibrary(ctx, lib.ID)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := movieStore.ConfirmMatch(ctx, items[0].ID, movie.Movie{
		LibraryID: "lib-1", TMDBID: 1, Title: "Gone Movie",
	}); err != nil {
		t.Fatal(err)
	}

	if err := os.Remove(f); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.ScanLibrary(ctx, lib); err != nil {
		t.Fatal(err)
	}
	items, err = store.ListByLibrary(ctx, lib.ID)
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 0 {
		t.Fatalf("removed matched file kept in list: %d items", len(items))
	}
	movies, err := movieStore.ListByLibrary(ctx, lib.ID)
	if err != nil {
		t.Fatal(err)
	}
	if len(movies) != 0 {
		t.Errorf("unreferenced movie kept: %d rows", len(movies))
	}

	// Same rule for unmatched files: removed from disk → removed from list.
	f2 := filepath.Join(dir, "Plain.Movie.2020.1080p.WEB-DL.x264.mkv")
	write(t, f2, "v")
	if _, err := svc.ScanLibrary(ctx, lib); err != nil {
		t.Fatal(err)
	}
	items, _ = store.ListByLibrary(ctx, lib.ID)
	if len(items) != 1 {
		t.Fatalf("items after adding second file = %d, want 1", len(items))
	}
	if err := os.Remove(f2); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.ScanLibrary(ctx, lib); err != nil {
		t.Fatal(err)
	}
	items, _ = store.ListByLibrary(ctx, lib.ID)
	if len(items) != 0 {
		t.Fatalf("removed unmatched file kept in list: %d items", len(items))
	}
}
