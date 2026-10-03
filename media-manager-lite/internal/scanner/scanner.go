// Package scanner walks a library directory and collects video file
// candidates, parses their filenames and persists them as media_items.
//
// Filesystem policy:
//   - the walk is rooted at the library's canonical path (re-validated on
//     every scan, so an unmounted volume fails loudly);
//   - entries starting with "." are skipped;
//   - symlinked directories are never descended into (filepath.WalkDir does
//     not follow them), and symlinked files are only accepted when their
//     resolved target stays inside the library root — a symlink cannot
//     escape the library;
//   - only video files are collected; samples and trailers are excluded.
//
// The scanner does not assign movie matches or write NFO files — parsing is
// filename-only (see internal/parser).
package scanner

import (
	"context"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"time"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/parser"
	"media-manager-lite/internal/paths"
	"media-manager-lite/internal/task"
	"media-manager-lite/internal/tv"
)

// Candidate is one video file discovered on disk.
type Candidate struct {
	Path    string
	Name    string
	Size    int64
	ModTime time.Time
}

// Walk collects video file candidates under root. The root is canonicalized
// first (existence, directory, symlink resolution), so containment checks
// compare resolved paths on both sides. Unreadable entries abort the walk.
func Walk(root string) ([]Candidate, error) {
	root, err := paths.CanonicalizeDir(root)
	if err != nil {
		return nil, fmt.Errorf("walk: %w", err)
	}

	var out []Candidate
	err = filepath.WalkDir(root, func(p string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		name := d.Name()

		// Hidden files and directories (except the root itself).
		if p != root && strings.HasPrefix(name, ".") {
			if d.IsDir() {
				return fs.SkipDir
			}
			return nil
		}
		if d.IsDir() {
			return nil
		}

		// Symlinks: resolve and enforce library-root containment.
		if !d.Type().IsRegular() {
			resolved, rerr := filepath.EvalSymlinks(p)
			if rerr != nil {
				return nil // broken symlink: ignore
			}
			info, serr := os.Stat(resolved)
			if serr != nil || !info.Mode().IsRegular() {
				return nil // symlinked directories are never traversed
			}
			if !paths.WithinRoot(root, resolved) {
				return nil // escape attempt: ignore
			}
		}

		if !parser.IsVideoFile(name) || parser.IsTrailerOrSample(name) {
			return nil
		}

		info, err := d.Info()
		if err != nil {
			return err
		}
		out = append(out, Candidate{Path: p, Name: name, Size: info.Size(), ModTime: info.ModTime()})
		return nil
	})
	if err != nil {
		return nil, fmt.Errorf("walk %s: %w", root, err)
	}
	return out, nil
}

// Stats summarizes one library scan.
type Stats struct {
	Found    int `json:"found"`
	New      int `json:"new"`
	Updated  int `json:"updated"`
	Skipped  int `json:"skipped,omitempty"`
	Episodes int `json:"episodes,omitempty"`
}

// Service scans libraries and persists media items.
type Service struct {
	media  *media.Store
	tv     *tv.Store
	movies *movie.Store
}

// NewService returns a scanner service persisting into the given stores.
func NewService(m *media.Store, t *tv.Store, mv *movie.Store) *Service {
	return &Service{media: m, tv: t, movies: mv}
}

// ScanLibrary walks the library directory and dispatches by library type:
// movie libraries go through the movie filename parser, TV libraries
// through the episode parser (show/season/episode upserts).
func (s *Service) ScanLibrary(ctx context.Context, lib library.Library) (Stats, error) {
	if lib.Type == library.TypeTV {
		return s.ScanTVLibrary(ctx, lib)
	}
	return s.scanMovieLibrary(ctx, lib)
}

// scanMovieLibrary walks the library directory, parses every discovered
// filename and upserts the results as media_items. The filesystem is only
// read.
func (s *Service) scanMovieLibrary(ctx context.Context, lib library.Library) (Stats, error) {
	root, err := paths.CanonicalizeDir(lib.Path)
	if err != nil {
		return Stats{}, fmt.Errorf("scan library %q: %w", lib.Name, err)
	}
	cands, err := Walk(root)
	if err != nil {
		return Stats{}, fmt.Errorf("scan library %q: %w", lib.Name, err)
	}

	items := make([]media.Item, 0, len(cands))
	for _, c := range cands {
		parsed := parser.Parse(c.Name)
		items = append(items, media.Item{
			LibraryID:   lib.ID,
			Kind:        media.KindMovie,
			Path:        c.Path,
			Filename:    c.Name,
			Ext:         strings.ToLower(filepath.Ext(c.Name)),
			Size:        c.Size,
			ModTime:     c.ModTime.UTC().Format(time.RFC3339),
			ParsedTitle: parsed.Title,
			ParsedYear:  parsed.Year,
			Parsed:      &parsed,
			Status:      media.StatusNew,
		})
	}

	inserted, updated, err := s.media.UpsertBatch(ctx, items)
	if err != nil {
		return Stats{}, fmt.Errorf("scan library %q: %w", lib.Name, err)
	}

	// Files that no longer exist on disk leave the list entirely, matched
	// or not: the scan reflects what is actually in the library folder.
	// Matched items additionally cascade to their movie row when no other
	// media item references it (multi-version movies survive).
	walked := make(map[string]bool, len(cands))
	for _, c := range cands {
		walked[c.Path] = true
	}
	items, err2 := s.media.ListByLibrary(ctx, lib.ID)
	if err2 != nil {
		return Stats{}, fmt.Errorf("scan library %q: %w", lib.Name, err2)
	}
	affected := map[string]bool{}
	removed := 0
	for _, it := range items {
		if _, ok := walked[it.Path]; ok {
			continue
		}
		if _, err := os.Stat(it.Path); !os.IsNotExist(err) {
			continue // still on disk somehow: keep
		}
		movieID, err := s.media.DeleteItem(ctx, it.ID)
		if err != nil {
			return Stats{}, fmt.Errorf("scan library %q: %w", lib.Name, err)
		}
		removed++
		if movieID != "" {
			affected[movieID] = true
		}
	}
	_ = removed
	for movieID := range affected {
		if _, err := s.movies.DeleteIfUnreferenced(ctx, movieID); err != nil {
			return Stats{}, fmt.Errorf("scan library %q: %w", lib.Name, err)
		}
	}

	return Stats{Found: len(cands), New: inserted, Updated: updated}, nil
}

// ScanHandler adapts ScanLibrary into a task handler for the Runner. It
// re-loads the library on every execution, so tasks recovered after a
// restart pick up the current configuration.
func ScanHandler(libs *library.Service, svc *Service) task.Handler {
	return func(ctx context.Context, _ /*targetType*/, targetID string) (string, error) {
		lib, err := libs.Get(ctx, targetID)
		if err != nil {
			return "", err
		}
		stats, err := svc.ScanLibrary(ctx, lib)
		if err != nil {
			return "", err
		}
		return fmt.Sprintf("found %d files: %d new, %d updated", stats.Found, stats.New, stats.Updated), nil
	}
}
