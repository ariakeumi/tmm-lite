package artwork

import (
	"context"
	"fmt"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/task"
	"media-manager-lite/internal/tv"
)

// Season poster file name (TMM naming: seasonXX-poster.jpg).
func seasonPosterFile(season int) string {
	if season == 0 {
		return "season-specials-poster.jpg"
	}
	return fmt.Sprintf("season%02d-poster.jpg", season)
}

var seasonFolderName = regexp.MustCompile(`(?i)^(s|season|saison|staffel|temporada|serie)\s*\d{1,2}$|^specials?$`)

// showDirOf resolves the show artwork folder: the common directory of the
// show's episode items, lifted one level when it is a season folder (same
// rule as the TV NFO writer).
func showDirOf(items []tv.EpisodeItemRef) string {
	if len(items) == 0 {
		return ""
	}
	paths := make([]string, 0, len(items))
	for _, it := range items {
		paths = append(paths, it.Path)
	}
	common := filepath.Dir(paths[0])
	for _, p := range paths[1:] {
		d := filepath.Dir(p)
		for common != d {
			if strings.HasPrefix(d, common+string(filepath.Separator)) || d == common {
				break
			}
			parent := filepath.Dir(common)
			if parent == common {
				break
			}
			common = parent
		}
	}
	if seasonFolderName.MatchString(strings.ToLower(filepath.Base(common))) {
		parent := filepath.Dir(common)
		if parent != common {
			common = parent
		}
	}
	return common
}

// DownloadShow fetches poster + fanart for a matched TV show into the show
// folder and a season poster (seasonXX-poster.jpg) into every scanned
// season folder. Existing files are untouched on failure (atomic
// temp-file + rename), mirroring the movie flow.
func (s *Service) DownloadShow(ctx context.Context, showID string) (*movie.Artwork, error) {
	full, err := s.tv.GetShowFull(ctx, showID)
	if err != nil {
		return nil, fmt.Errorf("tv artwork: %w", err)
	}
	if full.TMDBID <= 0 {
		return nil, fmt.Errorf("tv artwork: tv show is not matched to a TMDB show")
	}
	items, err := s.tv.ListEpisodeItems(ctx, showID)
	if err != nil {
		return nil, err
	}
	if len(items) == 0 {
		return nil, fmt.Errorf("tv artwork: show has no scanned episode files")
	}
	showDir := showDirOf(items)
	if showDir == "" {
		return nil, fmt.Errorf("tv artwork: cannot resolve show directory")
	}
	if err := s.ensureWithinLibrary(ctx, full.LibraryID, filepath.Join(showDir, PosterFile)); err != nil {
		return nil, err
	}

	c, err := s.tmdb.Client(ctx)
	if err != nil {
		return nil, err
	}
	posters, backdrops, err := c.TVImages(ctx, full.TMDBID)
	if err != nil {
		return nil, fmt.Errorf("tv artwork: images: %w", err)
	}
	lang, err := s.tmdb.Language(ctx)
	if err != nil {
		return nil, err
	}
	lang = strings.ToLower(strings.SplitN(lang, "-", 2)[0])
	base, err := s.tmdb.ImageBaseURL(ctx)
	if err != nil {
		return nil, err
	}

	aw := &movie.Artwork{DownloadedAt: time.Now().UTC().Format(time.RFC3339)}
	if best, ok := BestImage(posters, lang); ok {
		url := base + "original" + best.FilePath
		if err := s.download(ctx, url, filepath.Join(showDir, PosterFile)); err != nil {
			return nil, fmt.Errorf("tv artwork: poster: %w", err)
		}
		aw.PosterURL, aw.PosterFile = url, filepath.Join(showDir, PosterFile)
	}
	if best, ok := BestImage(backdrops, lang); ok {
		url := base + "original" + best.FilePath
		if err := s.download(ctx, url, filepath.Join(showDir, FanartFile)); err != nil {
			return nil, fmt.Errorf("tv artwork: fanart: %w", err)
		}
		aw.FanartURL, aw.FanartFile = url, filepath.Join(showDir, FanartFile)
	}

	// Season posters into every scanned season folder.
	seasonDirs := map[int]string{}
	for _, it := range items {
		row, err := s.tv.EpisodeNumbersForItem(ctx, it.ItemID)
		if err != nil {
			continue
		}
		seasonDirs[row.Season] = filepath.Dir(it.Path)
	}
	for season, dir := range seasonDirs {
		if err := s.ensureWithinLibrary(ctx, full.LibraryID, filepath.Join(dir, seasonPosterFile(season))); err != nil {
			return nil, err
		}
		images, err := c.TVSeasonImages(ctx, full.TMDBID, season)
		if err != nil {
			continue // season images unavailable: not fatal
		}
		best, ok := BestImage(images, lang)
		if !ok {
			continue
		}
		url := base + "original" + best.FilePath
		if err := s.download(ctx, url, filepath.Join(dir, seasonPosterFile(season))); err != nil {
			return nil, fmt.Errorf("tv artwork: season %d poster: %w", season, err)
		}
	}

	if err := s.tv.UpdateShowArtwork(ctx, showID, aw); err != nil {
		return nil, err
	}
	return aw, nil
}

// TVDownloadHandler adapts DownloadShow into a task handler.
func TVDownloadHandler(store *tv.Store, svc *Service) task.Handler {
	return func(ctx context.Context, _ /*targetType*/, showID string) (string, error) {
		sh, err := store.GetShow(ctx, showID)
		if err != nil {
			return "", err
		}
		aw, err := svc.DownloadShow(ctx, showID)
		if err != nil {
			return "", err
		}
		n := 0
		if aw.PosterFile != "" {
			n++
		}
		if aw.FanartFile != "" {
			n++
		}
		return fmt.Sprintf("downloaded %d tv artwork file(s) for %q", n, sh.Title), nil
	}
}

var _ = tv.ErrShowNotFound
