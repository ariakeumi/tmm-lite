package artwork

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"media-manager-lite/internal/tmdb"
)

func TestBestImageRanking(t *testing.T) {
	preferred := "zh"
	images := []tmdb.Image{
		{FilePath: "/en.jpg", Language: "en", VoteAverage: 9.9, VoteCount: 100},
		{FilePath: "/zh-tw.jpg", Language: "zh", VoteAverage: 5.0, VoteCount: 10},
		{FilePath: "/none.jpg", VoteAverage: 8.0, VoteCount: 50},
		{FilePath: "/de.jpg", Language: "de", VoteAverage: 9.0, VoteCount: 40},
		{FilePath: "/zh-cn.jpg", Language: "zh", VoteAverage: 6.0, VoteCount: 20},
	}

	best, ok := BestImage(images, preferred)
	if !ok {
		t.Fatal("no best image")
	}
	// Preferred language wins over the higher-voted English poster; among
	// the two zh candidates the higher vote count wins.
	if best.FilePath != "/zh-cn.jpg" {
		t.Errorf("best = %q, want the zh poster (preferred language first)", best.FilePath)
	}

	// Among same-language candidates the higher vote wins.
	noZh := []tmdb.Image{images[1], images[4]}
	best, _ = BestImage(noZh, preferred)
	if best.FilePath != "/zh-cn.jpg" {
		t.Errorf("best = %q, want the higher-voted zh poster", best.FilePath)
	}

	// Without any zh: English beats higher-voted other languages.
	noZhAtAll := []tmdb.Image{images[0], images[3], images[2]}
	best, _ = BestImage(noZhAtAll, preferred)
	if best.FilePath != "/en.jpg" {
		t.Errorf("best = %q, want the English poster", best.FilePath)
	}

	// Fallback: undetermined language beats unrelated ones by votes.
	only := []tmdb.Image{images[2], images[3]}
	best, _ = BestImage(only, preferred)
	if best.FilePath != "/none.jpg" {
		t.Errorf("best = %q, want the undetermined-language poster by votes", best.FilePath)
	}

	if _, ok := BestImage(nil, preferred); ok {
		t.Error("empty list must return ok=false")
	}
}

// pngBytes is a minimal valid 1x1 PNG.
var pngBytes = []byte{
	0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
	0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
	0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4, 0x89, 0x00, 0x00, 0x00,
	0x0A, 0x49, 0x44, 0x41, 0x54, 0x78, 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
	0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4, 0x00, 0x00, 0x00, 0x00, 0x49,
	0x45, 0x4E, 0x44, 0xAE, 0x42, 0x60, 0x82,
}

func TestDownloadAtomicSuccess(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/png")
		w.Write(pngBytes)
	}))
	defer srv.Close()

	s := &Service{http: srv.Client()}
	dir := t.TempDir()
	dest := filepath.Join(dir, PosterFile)

	if err := s.download(context.Background(), srv.URL+"/img", dest); err != nil {
		t.Fatal(err)
	}
	got, err := os.ReadFile(dest)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(pngBytes) {
		t.Error("downloaded content differs")
	}
	// No temp leftovers.
	entries, _ := os.ReadDir(dir)
	if len(entries) != 1 {
		t.Errorf("directory = %d entries, want only the poster", len(entries))
	}
}

func TestDownloadFailureLeavesExistingFileUntouched(t *testing.T) {
	calls := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		if calls == 1 {
			w.Write(pngBytes)
			return
		}
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer srv.Close()

	s := &Service{http: srv.Client()}
	dir := t.TempDir()
	dest := filepath.Join(dir, PosterFile)

	if err := s.download(context.Background(), srv.URL+"/img", dest); err != nil {
		t.Fatal(err)
	}
	before, _ := os.ReadFile(dest)

	// Now the server fails: the existing file must survive untouched.
	if err := s.download(context.Background(), srv.URL+"/img", dest); err == nil {
		t.Fatal("expected an error on server failure")
	}
	after, err := os.ReadFile(dest)
	if err != nil {
		t.Fatalf("existing file must survive: %v", err)
	}
	if string(before) != string(after) {
		t.Error("existing file was modified by a failed download")
	}
	entries, _ := os.ReadDir(dir)
	if len(entries) != 1 {
		t.Errorf("temp files left behind: %d entries", len(entries))
	}
}

func TestDownloadRejectsNonImagesAndEmpty(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch {
		case strings.HasSuffix(r.URL.Path, "/empty"):
			return // 0 bytes, 200
		case strings.HasSuffix(r.URL.Path, "/html"):
			fmt.Fprint(w, "<html>not an image</html>")
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()

	s := &Service{http: srv.Client()}
	dir := t.TempDir()

	for _, name := range []string{"empty", "html", "missing"} {
		dest := filepath.Join(dir, name+".jpg")
		if err := s.download(context.Background(), srv.URL+"/"+name, dest); err == nil {
			t.Errorf("download %q should fail", name)
		}
		if _, err := os.Stat(dest); !os.IsNotExist(err) {
			t.Errorf("destination %q must not be created on failure", dest)
		}
	}
	entries, _ := os.ReadDir(dir)
	if len(entries) != 0 {
		t.Errorf("temp leftovers: %d entries", len(entries))
	}
}
