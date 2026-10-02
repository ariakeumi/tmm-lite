// Package artwork selects and downloads movie artwork (poster, fanart)
// following the Phase-0 ranking behavior: preferred language first, then
// English, then TMDB vote average. Downloads go to a temporary file that is
// atomically renamed into place — a failed download never damages an
// existing file.
package artwork

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/paths"
	"media-manager-lite/internal/task"
	"media-manager-lite/internal/tmdb"
	"media-manager-lite/internal/tv"
)

// Fixed file names inside the media folder (naming variants arrive with the
// renamer milestone).
const (
	PosterFile = "poster.jpg"
	FanartFile = "fanart.jpg"

	maxImageBytes = 32 << 20 // 32 MiB
)

// Service picks and downloads artwork for matched movies and TV shows.
type Service struct {
	tmdb   *tmdb.Service
	media  *media.Store
	movies *movie.Store
	tv     *tv.Store
	libs   *library.Service
	http   *http.Client
}

// NewService returns an artwork service.
func NewService(tm *tmdb.Service, m *media.Store, mv *movie.Store, t *tv.Store, l *library.Service) *Service {
	return &Service{
		tmdb:   tm,
		media:  m,
		movies: mv,
		tv:     t,
		libs:   l,
		http:   &http.Client{Timeout: 60 * time.Second},
	}
}

// ensureWithinLibrary verifies that dest's directory still resolves inside
// the library root at write time — a directory swapped for a symlink after
// the scan must not redirect artwork writes out of the root (P0-4).
func (s *Service) ensureWithinLibrary(ctx context.Context, libraryID, dest string) error {
	if s.libs == nil {
		return nil // hardening is best-effort without a library source
	}
	lib, err := s.libs.Get(ctx, libraryID)
	if err != nil {
		return fmt.Errorf("artwork: resolve library: %w", err)
	}
	resolved, err := filepath.EvalSymlinks(filepath.Dir(dest))
	if err != nil {
		return fmt.Errorf("artwork: destination directory: %w", err)
	}
	if !paths.WithinRoot(lib.Path, resolved) {
		return fmt.Errorf("artwork: destination resolves outside the library root")
	}
	return nil
}

// BestImage applies the Phase-0 ranking: preferred language first, English
// second, TMDB vote average (then vote count) as tiebreakers.
func BestImage(images []tmdb.Image, preferred string) (tmdb.Image, bool) {
	if len(images) == 0 {
		return tmdb.Image{}, false
	}
	preferred = strings.ToLower(preferred)
	sorted := make([]tmdb.Image, len(images))
	copy(sorted, images)
	sort.SliceStable(sorted, func(i, j int) bool {
		a, b := sorted[i], sorted[j]
		ai, bi := rankImage(a, preferred), rankImage(b, preferred)
		if ai != bi {
			return ai > bi
		}
		if a.VoteAverage != b.VoteAverage {
			return a.VoteAverage > b.VoteAverage
		}
		return a.VoteCount > b.VoteCount
	})
	return sorted[0], true
}

func rankImage(img tmdb.Image, preferred string) int {
	lang := strings.ToLower(img.Language)
	switch {
	case preferred != "" && lang == preferred:
		return 3
	case lang == "en":
		return 2
	case lang == "":
		return 1 // undetermined language (most backdrops)
	default:
		return 0
	}
}

// Download fetches poster and fanart for the movie linked to the media
// item, writing poster.jpg / fanart.jpg into the item's directory. On
// failure the existing files are untouched (temp files are removed) and the
// movies.art_json row is left as-is.
func (s *Service) Download(ctx context.Context, itemID string) (*movie.Artwork, error) {
	it, err := s.media.GetItem(ctx, itemID)
	if err != nil {
		return nil, fmt.Errorf("artwork: %w", err)
	}
	if it.Kind != media.KindMovie {
		return nil, fmt.Errorf("artwork: only movie items are supported")
	}
	if it.MovieID == "" {
		return nil, fmt.Errorf("artwork: media item is not matched to a movie")
	}
	m, err := s.movies.GetByID(ctx, it.MovieID)
	if err != nil {
		return nil, fmt.Errorf("artwork: %w", err)
	}
	if m.TMDBID <= 0 {
		return nil, fmt.Errorf("artwork: movie has no TMDB id")
	}
	// Fail fast before any network activity: the write destination must
	// resolve inside the library root (P0-4).
	if err := s.ensureWithinLibrary(ctx, it.LibraryID, filepath.Join(filepath.Dir(it.Path), PosterFile)); err != nil {
		return nil, err
	}

	c, err := s.tmdb.Client(ctx)
	if err != nil {
		return nil, err
	}
	posters, backdrops, err := c.MovieImages(ctx, m.TMDBID)
	if err != nil {
		return nil, fmt.Errorf("artwork: images: %w", err)
	}
	lang, err := s.tmdb.Language(ctx)
	if err != nil {
		return nil, err
	}
	lang = strings.ToLower(strings.SplitN(lang, "-", 2)[0]) // zh-CN → zh

	base, err := s.tmdb.ImageBaseURL(ctx)
	if err != nil {
		return nil, err
	}
	dir := filepath.Dir(it.Path)
	aw := &movie.Artwork{DownloadedAt: time.Now().UTC().Format(time.RFC3339)}

	if best, ok := BestImage(posters, lang); ok {
		url := base + "original" + best.FilePath
		if err := s.download(ctx, url, filepath.Join(dir, PosterFile)); err != nil {
			return nil, fmt.Errorf("artwork: poster: %w", err)
		}
		aw.PosterURL = url
		aw.PosterFile = filepath.Join(dir, PosterFile)
	}
	if best, ok := BestImage(backdrops, lang); ok {
		url := base + "original" + best.FilePath
		if err := s.download(ctx, url, filepath.Join(dir, FanartFile)); err != nil {
			return nil, fmt.Errorf("artwork: fanart: %w", err)
		}
		aw.FanartURL = url
		aw.FanartFile = filepath.Join(dir, FanartFile)
	}

	if err := s.movies.UpdateArtwork(ctx, m.ID, aw); err != nil {
		return nil, err
	}
	return aw, nil
}

// download streams url into <dest>.part, validates the payload (non-empty,
// JPEG/PNG magic) and atomically renames it onto dest. Any failure removes
// the temp file and leaves an existing dest untouched.
func (s *Service) download(ctx context.Context, url, dest string) error {
	if url == "" {
		return errors.New("artwork: empty URL")
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return fmt.Errorf("artwork: build request: %w", err)
	}
	resp, err := s.http.Do(req)
	if err != nil {
		return fmt.Errorf("artwork: download: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("artwork: download: unexpected status %d", resp.StatusCode)
	}

	f, err := os.CreateTemp(filepath.Dir(dest), filepath.Base(dest)+".*.part")
	if err != nil {
		return fmt.Errorf("artwork: temp file: %w", err)
	}
	tmpName := f.Name()
	removeTemp := func() { _ = f.Close(); _ = os.Remove(tmpName) }

	n, err := io.Copy(f, io.LimitReader(resp.Body, maxImageBytes+1))
	if err != nil {
		removeTemp()
		return fmt.Errorf("artwork: download: %w", err)
	}
	if n == 0 {
		removeTemp()
		return errors.New("artwork: 0-byte file downloaded")
	}
	if n > maxImageBytes {
		removeTemp()
		return errors.New("artwork: image too large")
	}
	// Magic-byte validation (JPEG or PNG) — lighter than a full decode.
	if _, err := f.Seek(0, io.SeekStart); err != nil {
		removeTemp()
		return err
	}
	magic := make([]byte, 4)
	if _, err := io.ReadFull(f, magic); err != nil {
		removeTemp()
		return fmt.Errorf("artwork: read magic: %w", err)
	}
	isJPEG := magic[0] == 0xFF && magic[1] == 0xD8
	isPNG := magic[0] == 0x89 && magic[1] == 0x50 && magic[2] == 0x4E && magic[3] == 0x47
	if !isJPEG && !isPNG {
		removeTemp()
		return errors.New("artwork: downloaded file is not a JPEG/PNG image")
	}
	if err := f.Close(); err != nil {
		_ = os.Remove(tmpName)
		return err
	}
	if err := os.Rename(tmpName, dest); err != nil {
		_ = os.Remove(tmpName)
		return fmt.Errorf("artwork: rename: %w", err)
	}
	return nil
}

// DownloadHandler adapts Download into a task handler.
func DownloadHandler(media *media.Store, svc *Service) task.Handler {
	return func(ctx context.Context, _ /*targetType*/, itemID string) (string, error) {
		it, err := media.GetItem(ctx, itemID)
		if err != nil {
			return "", err
		}
		aw, err := svc.Download(ctx, itemID)
		if err != nil {
			return "", err
		}
		downloaded := 0
		if aw.PosterFile != "" {
			downloaded++
		}
		if aw.FanartFile != "" {
			downloaded++
		}
		return fmt.Sprintf("downloaded %d artwork file(s) for %s", downloaded, it.Filename), nil
	}
}
