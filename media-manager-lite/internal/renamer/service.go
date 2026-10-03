package renamer

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/settings"
	"media-manager-lite/internal/task"
	"media-manager-lite/internal/tv"
)

// Service orchestrates the rename workflow for matched movies: it gathers
// the on-disk state (read-only), plans, executes and updates the database
// rows afterwards (media_items.path, movies.art_json).
type Service struct {
	media    *media.Store
	movies   *movie.Store
	libs     *library.Service
	settings *settings.Store
	tv       *tv.Store
	view     FSView
}

// NewService returns a renamer service.
func NewService(m *media.Store, mv *movie.Store, l *library.Service, st *settings.Store, t *tv.Store) *Service {
	return &Service{media: m, movies: mv, libs: l, settings: st, tv: t, view: OSView{}}
}

// OSView is the real-filesystem FSView.
type OSView struct{}

func (OSView) Exists(p string) bool { _, err := os.Stat(p); return err == nil }
func (OSView) IsDir(p string) bool {
	fi, err := os.Stat(p)
	return err == nil && fi.IsDir()
}

// DataForItem assembles the token values from the movie metadata and the
// media item's parsed technical tags.
func (s *Service) DataForItem(ctx context.Context, it media.Item, m movie.Movie) Data {
	d := Data{
		Title:         m.Title,
		OriginalTitle: m.OriginalTitle,
		Year:          m.Year,
		IMDBID:        m.IMDBID,
		TMDBID:        m.TMDBID,
	}
	if md := m.Metadata; md != nil {
		d.EnglishTitle = md.EnglishTitle
	}
	if it.Parsed != nil {
		d.VideoCodec = it.Parsed.VideoCodec
		d.VideoResolution = it.Parsed.Resolution
		d.HDR = it.Parsed.HDR
		d.MediaSource = it.Parsed.Source
		if len(it.Parsed.Audio) > 0 {
			d.AudioCodec = it.Parsed.Audio[0]
		}
	}
	if d.Title == "" {
		d.Title = it.ParsedTitle
	}
	return d
}

// Profile resolves the configured profile (settings JSON) or the default.
func (s *Service) Profile(ctx context.Context) (Profile, error) {
	raw, ok, err := s.settings.Get(ctx, settings.KeyRenameProfile)
	if err != nil {
		return DefaultProfile(), err
	}
	if !ok || strings.TrimSpace(raw) == "" {
		return DefaultProfile(), nil
	}
	var p Profile
	if err := json.Unmarshal([]byte(raw), &p); err != nil {
		return DefaultProfile(), fmt.Errorf("renamer: invalid profile: %w", err)
	}
	if p.FilePattern == "" {
		p.FilePattern = DefaultProfile().FilePattern
	}
	if len(p.NFONames) == 0 {
		p.NFONames = DefaultProfile().NFONames
	}
	if len(p.PosterNames) == 0 {
		p.PosterNames = DefaultProfile().PosterNames
	}
	if len(p.FanartNames) == 0 {
		p.FanartNames = DefaultProfile().FanartNames
	}
	return p, nil
}

// itemContext loads everything Plan needs for one media item.
func (s *Service) itemContext(ctx context.Context, itemID string) (media.Item, movie.Movie, library.Library, FileSet, Data, Profile, error) {
	it, err := s.media.GetItem(ctx, itemID)
	if err != nil {
		return it, movie.Movie{}, library.Library{}, FileSet{}, Data{}, Profile{}, fmt.Errorf("renamer: %w", err)
	}
	if it.Kind != "movie" {
		return it, movie.Movie{}, library.Library{}, FileSet{}, Data{}, Profile{}, fmt.Errorf("renamer: only movie items are supported")
	}
	if it.MovieID == "" {
		return it, movie.Movie{}, library.Library{}, FileSet{}, Data{}, Profile{}, fmt.Errorf("renamer: media item is not matched to a movie")
	}
	m, err := s.movies.GetByID(ctx, it.MovieID)
	if err != nil {
		return it, movie.Movie{}, library.Library{}, FileSet{}, Data{}, Profile{}, fmt.Errorf("renamer: %w", err)
	}
	lib, err := s.libs.Get(ctx, it.LibraryID)
	if err != nil {
		return it, movie.Movie{}, library.Library{}, FileSet{}, Data{}, Profile{}, fmt.Errorf("renamer: %w", err)
	}

	dir := filepath.Dir(it.Path)
	entries, err := os.ReadDir(dir)
	if err != nil {
		return it, movie.Movie{}, library.Library{}, FileSet{}, Data{}, Profile{}, fmt.Errorf("renamer: read directory: %w", err)
	}
	fs := FileSet{Dir: dir, Video: filepath.Base(it.Path)}
	for _, e := range entries {
		if !e.IsDir() && e.Name() != fs.Video {
			fs.Others = append(fs.Others, e.Name())
		}
	}
	sort.Strings(fs.Others)

	data := s.DataForItem(ctx, it, m)
	profile, err := s.Profile(ctx)
	if err != nil {
		return it, movie.Movie{}, library.Library{}, FileSet{}, Data{}, Profile{}, err
	}
	return it, m, lib, fs, data, profile, nil
}

// PlanForItem computes the rename plan for a matched movie item. It only
// reads: the directory listing, the FSView and the database.
func (s *Service) PlanForItem(ctx context.Context, itemID string) (*Plan, error) {
	_, _, lib, fs, data, profile, err := s.itemContext(ctx, itemID)
	if err != nil {
		return nil, err
	}
	return PlanRename(lib.Path, fs, s.view, data, profile), nil
}

// ExecuteForItem plans and executes the rename, then updates the database:
// media_items.path and the art_json file paths follow the new location.
func (s *Service) ExecuteForItem(ctx context.Context, itemID string) (*Plan, []ActionResult, error) {
	it, m, lib, fs, data, profile, err := s.itemContext(ctx, itemID)
	if err != nil {
		return nil, nil, err
	}
	plan := PlanRename(lib.Path, fs, s.view, data, profile)
	if !plan.Valid {
		return plan, nil, fmt.Errorf("renamer: plan is invalid: %s", strings.Join(plan.Invalid, "; "))
	}

	results, err := Execute(plan)
	if err != nil {
		return plan, results, err
	}

	// Database follow-up: the video path always moves to the final name.
	newVideoPath := plan.Video.To
	if newVideoPath == "" {
		newVideoPath = it.Path
	}
	if err := s.media.UpdatePath(ctx, it.ID, newVideoPath); err != nil {
		return plan, results, fmt.Errorf("renamer: update media item: %w", err)
	}

	// Artwork file paths follow the folder rename.
	if m.Artwork != nil && plan.NewDir != plan.OldDir {
		aw := *m.Artwork
		aw.PosterFile = swapDir(aw.PosterFile, plan.OldDir, plan.NewDir)
		aw.FanartFile = swapDir(aw.FanartFile, plan.OldDir, plan.NewDir)
		if err := s.movies.UpdateArtwork(ctx, m.ID, &aw); err != nil {
			return plan, results, fmt.Errorf("renamer: update artwork paths: %w", err)
		}
	}
	return plan, results, nil
}

func swapDir(path, oldDir, newDir string) string {
	if path == "" || oldDir == newDir {
		return path
	}
	if strings.HasPrefix(path, oldDir+string(filepath.Separator)) {
		return filepath.Join(newDir, strings.TrimPrefix(path, oldDir+string(filepath.Separator)))
	}
	return path
}

// EpisodeFileContext gathers the inputs for one episode item plan.
func (s *Service) EpisodeContext(ctx context.Context, itemID string) (library.Library, EpisodeFileSet, EpisodeData, EpisodeProfile, error) {
	it, err := s.media.GetItem(ctx, itemID)
	if err != nil {
		return library.Library{}, EpisodeFileSet{}, EpisodeData{}, EpisodeProfile{}, fmt.Errorf("renamer: %w", err)
	}
	if it.Kind != media.KindEpisode {
		return library.Library{}, EpisodeFileSet{}, EpisodeData{}, EpisodeProfile{}, fmt.Errorf("renamer: item is not a TV episode")
	}
	lib, err := s.libs.Get(ctx, it.LibraryID)
	if err != nil {
		return library.Library{}, EpisodeFileSet{}, EpisodeData{}, EpisodeProfile{}, fmt.Errorf("renamer: %w", err)
	}
	var show library.Library
	_ = show

	// Show + episode data through the tv store.
	row, err := s.tv.EpisodeNumbersForItem(ctx, it.ID)
	if err != nil {
		return library.Library{}, EpisodeFileSet{}, EpisodeData{}, EpisodeProfile{}, fmt.Errorf("renamer: %w", err)
	}
	showID, err := s.tv.ShowIDForItem(ctx, it.ID)
	if err != nil {
		return library.Library{}, EpisodeFileSet{}, EpisodeData{}, EpisodeProfile{}, fmt.Errorf("renamer: %w", err)
	}
	full, err := s.tv.GetShowFull(ctx, showID)
	if err != nil {
		return library.Library{}, EpisodeFileSet{}, EpisodeData{}, EpisodeProfile{}, fmt.Errorf("renamer: %w", err)
	}

	dir := filepath.Dir(it.Path)
	entries, err := os.ReadDir(dir)
	if err != nil {
		return library.Library{}, EpisodeFileSet{}, EpisodeData{}, EpisodeProfile{}, fmt.Errorf("renamer: read directory: %w", err)
	}
	fs := EpisodeFileSet{Dir: dir, Video: filepath.Base(it.Path)}
	for _, e := range entries {
		if !e.IsDir() && e.Name() != fs.Video {
			fs.Others = append(fs.Others, e.Name())
		}
	}

	profile, err := s.EpisodeProfile(ctx)
	if err != nil {
		return library.Library{}, EpisodeFileSet{}, EpisodeData{}, EpisodeProfile{}, err
	}
	data := EpisodeData{
		ShowTitle: full.Title, ShowOriginalTitle: full.OriginalTitle,
		ShowYear: full.Year, ShowTMDBID: full.TMDBID,
		Season: row.Season, Episodes: row.Episodes, Title: row.LocalTitle,
	}
	if data.Title == "" {
		data.Title = it.ParsedTitle
	}
	return lib, fs, data, profile, nil
}

// EpisodeProfile resolves the TV profile (settings JSON) or the default.
func (s *Service) EpisodeProfile(ctx context.Context) (EpisodeProfile, error) {
	raw, ok, err := s.settings.Get(ctx, settings.KeyEpisodeProfile)
	if err != nil {
		return DefaultEpisodeProfile(), err
	}
	if !ok || strings.TrimSpace(raw) == "" {
		return DefaultEpisodeProfile(), nil
	}
	var p EpisodeProfile
	if err := json.Unmarshal([]byte(raw), &p); err != nil {
		return DefaultEpisodeProfile(), fmt.Errorf("renamer: invalid episode profile: %w", err)
	}
	def := DefaultEpisodeProfile()
	if p.FilePattern == "" {
		p.FilePattern = def.FilePattern
	}
	if p.MultiEpisodeStyle == "" {
		p.MultiEpisodeStyle = def.MultiEpisodeStyle
	}
	if len(p.NFONames) == 0 {
		p.NFONames = def.NFONames
	}
	return p, nil
}

// PlanEpisodeForItem computes the dry-run plan for one episode item
// (read-only).
func (s *Service) PlanEpisodeForItem(ctx context.Context, itemID string) (*Plan, error) {
	lib, fs, data, profile, err := s.EpisodeContext(ctx, itemID)
	if err != nil {
		return nil, err
	}
	return PlanEpisodeRename(lib.Path, fs, s.view, data, profile), nil
}

// RenameMovieLibraryHandler adapts batch movie renaming into a task
// handler: every matched movie item in the library gets planned and
// executed; unmatched items are skipped with a log line.
func RenameMovieLibraryHandler(media *media.Store, svc *Service) task.Handler {
	return func(ctx context.Context, _ /*targetType*/, libraryID string) (string, error) {
		items, err := media.ListByLibrary(ctx, libraryID)
		if err != nil {
			return "", err
		}
		matched, renamed, skipped := 0, 0, 0
		for _, it := range items {
			if it.Kind != "movie" {
				continue
			}
			if it.Status != "matched" && it.MovieID == "" {
				skipped++
				continue
			}
			matched++
			plan, _, err := svc.ExecuteForItem(ctx, it.ID)
			if err != nil {
				// Unmatched/invalid plans (e.g. pattern rendered empty) are
				// per-item failures; keep processing the rest.
				skipped++
				_ = plan
				continue
			}
			renamed += len(plan.Actions)
		}
		return fmt.Sprintf("matched %d, renamed %d action(s), skipped %d", matched, renamed, skipped), nil
	}
}

// ExecuteEpisodeForItem plans and executes, then updates media_items.path.
func (s *Service) ExecuteEpisodeForItem(ctx context.Context, itemID string) (*Plan, []ActionResult, error) {
	_, _, _, _, err := s.EpisodeContext(ctx, itemID)
	if err != nil {
		return nil, nil, err
	}
	plan, err := s.PlanEpisodeForItem(ctx, itemID)
	if err != nil {
		return nil, nil, err
	}
	if !plan.Valid {
		return plan, nil, fmt.Errorf("renamer: plan is invalid: %s", strings.Join(plan.Invalid, "; "))
	}
	results, err := Execute(plan)
	if err != nil {
		return plan, results, err
	}
	newPath := plan.Video.To
	if newPath == "" {
		it, err := s.media.GetItem(ctx, itemID)
		if err != nil {
			return plan, results, err
		}
		newPath = it.Path
	}
	if err := s.media.UpdatePath(ctx, itemID, newPath); err != nil {
		return plan, results, fmt.Errorf("renamer: update media item: %w", err)
	}
	return plan, results, nil
}
