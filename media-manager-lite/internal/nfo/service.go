package nfo

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"media-manager-lite/internal/library"
	"media-manager-lite/internal/media"
	"media-manager-lite/internal/movie"
	"media-manager-lite/internal/paths"
	"media-manager-lite/internal/settings"
	"media-manager-lite/internal/tmdb"
	"media-manager-lite/internal/tv"
)

// Service orchestrates NFO writing for matched movies: it loads the movie
// and its media item, merges user data from an existing NFO, builds the
// flavor-specific document and writes it atomically next to the video.
type Service struct {
	media    *media.Store
	movies   *movie.Store
	settings *settings.Store
	tmdb     *tmdb.Service
	tv       *tv.Store
	libs     *library.Service
}

// NewService returns an NFO service.
func NewService(m *media.Store, mv *movie.Store, st *settings.Store, tm *tmdb.Service, t *tv.Store, l *library.Service) *Service {
	return &Service{media: m, movies: mv, settings: st, tmdb: tm, tv: t, libs: l}
}

// ensureWithinLibrary verifies a write destination against the library
// root at write time (a directory swapped for a symlink after the scan
// must not redirect NFO writes out of the root, P0-4).
func (s *Service) ensureWithinLibrary(ctx context.Context, libraryID, dest string) error {
	if s.libs == nil {
		return nil
	}
	lib, err := s.libs.Get(ctx, libraryID)
	if err != nil {
		return fmt.Errorf("nfo: resolve library: %w", err)
	}
	resolved, err := filepath.EvalSymlinks(filepath.Dir(dest))
	if err != nil {
		return fmt.Errorf("nfo: destination directory: %w", err)
	}
	if !paths.WithinRoot(lib.Path, resolved) {
		return fmt.Errorf("nfo: destination resolves outside the library root")
	}
	return nil
}

// Result reports the outcome of a write.
type Result struct {
	Path    string `json:"path"`
	Written bool   `json:"written"` // false = unchanged, write skipped
	Flavor  string `json:"flavor"`
}

// WriteForItem builds and writes the NFO for a matched movie media item.
// The target file is <video basename>.nfo next to the video.
func (s *Service) WriteForItem(ctx context.Context, itemID string) (Result, error) {
	it, err := s.media.GetItem(ctx, itemID)
	if err != nil {
		return Result{}, fmt.Errorf("nfo: %w", err)
	}
	if it.Kind != media.KindMovie {
		return Result{}, fmt.Errorf("nfo: only movie items are supported")
	}
	if it.MovieID == "" {
		return Result{}, fmt.Errorf("nfo: media item is not matched to a movie")
	}
	m, err := s.movies.GetByID(ctx, it.MovieID)
	if err != nil {
		return Result{}, fmt.Errorf("nfo: %w", err)
	}

	flavorRaw, err := s.settings.String(ctx, settings.KeyNFOFlavor, string(Kodi))
	if err != nil {
		return Result{}, err
	}
	flavor := Flavor(flavorRaw)
	if !flavor.Valid() {
		flavor = Kodi
	}

	dir := filepath.Dir(it.Path)
	base := strings.TrimSuffix(it.Filename, filepath.Ext(it.Filename))
	target := filepath.Join(dir, base+".nfo")

	// Round-trip: parse the first existing NFO (basename.nfo, then movie.nfo).
	var userData UserData
	var unknown []UnknownElement
	existing := ""
	for _, candidate := range []string{target, filepath.Join(dir, "movie.nfo")} {
		b, err := os.ReadFile(candidate)
		if err != nil {
			continue
		}
		ud, unk, err := Parse(b)
		if err != nil {
			continue // unparseable: proceed without merge (kept untouched)
		}
		userData, unknown, existing = ud, unk, string(b)
		break
	}
	merged := MergeUserData(userData)
	if merged.DateAdded == "" && it.CreatedAt != "" {
		if t, err := time.Parse(time.RFC3339, it.CreatedAt); err == nil {
			merged.DateAdded = t.Format("2006-01-02 15:04:05")
		}
	}

	certCountry, err := s.settings.String(ctx, settings.KeyCertCountry, "US")
	if err != nil {
		certCountry = "US"
	}

	data := s.buildData(ctx, m, merged, unknown, certCountry)
	doc := Build(data, flavor)

	if existing != "" && Unchanged(existing, doc) {
		return Result{Path: target, Written: false, Flavor: string(flavor)}, nil
	}

	if err := s.ensureWithinLibrary(ctx, it.LibraryID, target); err != nil {
		return Result{}, err
	}
	if err := writeAtomic(target, doc); err != nil {
		return Result{}, err
	}
	if err := s.movies.SetNfoStatus(ctx, m.ID, "written"); err != nil {
		return Result{Path: target, Written: true, Flavor: string(flavor)}, err
	}
	return Result{Path: target, Written: true, Flavor: string(flavor)}, nil
}

func (s *Service) buildData(ctx context.Context, m movie.Movie, ud UserData, unknown []UnknownElement, certCountry string) MovieData {
	d := MovieData{
		Title:         m.Title,
		OriginalTitle: m.OriginalTitle,
		Year:          m.Year,
		Rating:        m.Rating,
		Votes:         m.Votes,
		Plot:          m.Plot,
		Tagline:       m.Tagline,
		Runtime:       m.Runtime,
		Certification: m.Certification,
		CertCountry:   certCountry,
		TMDBID:        m.TMDBID,
		IMDBID:        m.IMDBID,
		Premiered:     m.ReleaseDate,
		Genres:        []string{},
		Studios:       []string{},
		Writers:       []string{},
		Directors:     []string{},
		Actors:        []Actor{},
		UserData:      ud,
		Unknown:       unknown,
	}
	if m.Artwork != nil {
		d.PosterURL = m.Artwork.PosterURL
		d.FanartURL = m.Artwork.FanartURL
	}
	if md := m.Metadata; md != nil {
		d.SetName = md.SetName
		d.SetTMDBID = md.SetTMDBID
		d.Genres = md.Genres
		d.Studios = md.Studios
		d.Writers = md.Writers
		d.Directors = md.Directors
		for _, c := range md.Cast {
			thumb := ""
			if c.ProfilePath != "" && s.tmdb != nil {
				if base, err := s.tmdb.ImageBaseURL(ctx); err == nil {
					thumb = base + "h632" + c.ProfilePath
				}
			}
			d.Actors = append(d.Actors, Actor{Name: c.Name, Role: c.Character, Thumb: thumb})
		}
	}
	return d
}

// writeAtomic writes the document via a temp file in the same directory and
// a final rename; a failed write leaves any existing file untouched.
func writeAtomic(path, content string) error {
	f, err := os.CreateTemp(filepath.Dir(path), filepath.Base(path)+".*.part")
	if err != nil {
		return fmt.Errorf("nfo: temp file: %w", err)
	}
	tmp := f.Name()
	cleanup := func() { _ = f.Close(); _ = os.Remove(tmp) }

	if _, err := f.WriteString(content); err != nil {
		cleanup()
		return fmt.Errorf("nfo: write: %w", err)
	}
	if err := f.Close(); err != nil {
		_ = os.Remove(tmp)
		return fmt.Errorf("nfo: close: %w", err)
	}
	if err := os.Rename(tmp, path); err != nil {
		_ = os.Remove(tmp)
		return fmt.Errorf("nfo: rename: %w", err)
	}
	return nil
}

// TVWriteResult summarizes one written/skipped TV NFO file.
type TVWriteResult struct {
	Path    string `json:"path"`
	Kind    string `json:"kind"` // show | season | episode
	Written bool   `json:"written"`
}

// tvItemRef is one media item of a show (renamer shares this shape).
type tvItemRef struct {
	ItemID    string
	Path      string
	Filename  string
	CreatedAt string
}

// listTVItems returns the media items of a show via the episode join.
func (s *Service) listTVItems(ctx context.Context, showID string) ([]tvItemRef, error) {
	rows, err := s.tv.ListEpisodeItems(ctx, showID)
	if err != nil {
		return nil, err
	}
	out := make([]tvItemRef, 0, len(rows))
	for _, r := range rows {
		out = append(out, tvItemRef{ItemID: r.ItemID, Path: r.Path, Filename: r.Filename, CreatedAt: r.CreatedAt})
	}
	return out, nil
}

// commonDir returns the deepest directory shared by all paths.
func commonDir(paths []string) string {
	if len(paths) == 0 {
		return ""
	}
	base := filepath.Dir(paths[0])
	for _, p := range paths[1:] {
		d := filepath.Dir(p)
		for base != d && strings.HasPrefix(base, string(filepath.Separator)) {
			parent := filepath.Dir(base)
			if parent == base {
				break
			}
			if strings.HasPrefix(d, base+string(filepath.Separator)) || d == base {
				break
			}
			base = parent
		}
	}
	return base
}

// WriteTVForShow writes all NFO files of one show: tvshow.nfo in the
// common episode directory, season%02d.nfo per scanned season and one
// <basename>.nfo per episode item. Existing user data is merged; unchanged
// documents are skipped.
func (s *Service) WriteTVForShow(ctx context.Context, showID string) ([]TVWriteResult, error) {
	full, err := s.tv.GetShowFull(ctx, showID)
	if err != nil {
		return nil, fmt.Errorf("nfo: %w", err)
	}
	if full.TMDBID <= 0 {
		return nil, fmt.Errorf("nfo: tv show is not matched to a TMDB show")
	}
	flavorRaw, err := s.settings.String(ctx, settings.KeyNFOFlavor, string(Kodi))
	if err != nil {
		return nil, err
	}
	flavor := Flavor(flavorRaw)
	if !flavor.Valid() {
		flavor = Kodi
	}
	certCountry, _ := s.settings.String(ctx, settings.KeyCertCountry, "US")

	items, err := s.listTVItems(ctx, showID)
	if err != nil {
		return nil, err
	}
	if len(items) == 0 {
		return nil, fmt.Errorf("nfo: show has no scanned episode files")
	}

	SetStamp(time.Now().UTC().Format("2006-01-02 15:04:05"))
	defer SetStamp("")

	var results []TVWriteResult

	// Show + seasons data from the DB (TMDB enrich result).
	seasons, err := s.tv.ListSeasons(ctx, showID)
	if err != nil {
		return nil, err
	}
	episodes, err := s.tv.ListEpisodes(ctx, showID)
	if err != nil {
		return nil, err
	}
	showYear := full.Year
	showData := TVShowData{
		Title: full.Title, OriginalTitle: full.OriginalTitle,
		Year: showYear, Rating: full.Rating, Votes: full.Votes,
		Plot: full.Plot, Status: full.AiredStatus,
		Certification: full.Certification, CertCountry: certCountry,
		TMDBID: full.TMDBID, IMDBID: full.IMDBID,
	}
	if md := full.Metadata; md != nil {
		if g, ok := md["genres"].([]any); ok {
			for _, v := range g {
				if s, ok := v.(string); ok {
					showData.Genres = append(showData.Genres, s)
				}
			}
		}
		if st, ok := md["studios"].([]any); ok {
			for _, v := range st {
				if s, ok := v.(string); ok {
					showData.Studios = append(showData.Studios, s)
				}
			}
		}
	}
	// Premiered: earliest aired episode date (TMDB first_air_date is not
	// persisted by the M7 confirm step).
	if len(episodes) > 0 {
		earliest := ""
		for _, e := range episodes {
			if e.AirDate != "" && (earliest == "" || e.AirDate < earliest) {
				earliest = e.AirDate
			}
		}
		showData.Premiered = earliest
	}

	// --- tvshow.nfo in the common episode directory ---
	paths := make([]string, 0, len(items))
	for _, it := range items {
		paths = append(paths, it.Path)
	}
	common := commonDir(paths)
	// Show/Season layouts: the common episode directory may itself be a
	// season folder — the show NFO belongs one level up (next to the
	// season folders), like TMM writing to the show folder root.
	if seasonFolderNameRe.MatchString(strings.ToLower(filepath.Base(common))) {
		parent := filepath.Dir(common)
		if parent != common {
			common = parent
		}
	}

	existingShow, _ := os.ReadFile(filepath.Join(common, "tvshow.nfo"))
	if len(existingShow) > 0 {
		if ud, err := ParseTV(existingShow); err == nil && ud.Root == "tvshow" {
			agg := ud.Aggregate()
			showData.UserData = UserData{Watched: agg.Watched, Playcount: agg.Playcount,
				LastPlayed: agg.LastPlayed, DateAdded: agg.DateAdded, UserRating: agg.UserRating}
			showData.Unknown = ud.Unknown
		}
	}
	if showData.DateAdded == "" && len(items) > 0 {
		if t, err := time.Parse(time.RFC3339, items[0].CreatedAt); err == nil {
			showData.DateAdded = t.Format("2006-01-02 15:04:05")
		}
	}
	showDoc := BuildTVShow(showData, flavor)
	showPath := filepath.Join(common, "tvshow.nfo")
	written := len(existingShow) == 0 || !Unchanged(string(existingShow), showDoc)
	if written {
		if err := s.ensureWithinLibrary(ctx, full.LibraryID, showPath); err != nil {
			return results, err
		}
		if err := writeAtomic(showPath, showDoc); err != nil {
			return results, err
		}
	}
	results = append(results, TVWriteResult{Path: showPath, Kind: "show", Written: written})

	// --- season%02d.nfo next to the episodes of that season ---
	dirForSeason := map[int]string{}
	for _, it := range items {
		if row, err := s.tv.EpisodeNumbersForItem(ctx, it.ItemID); err == nil {
			dirForSeason[row.Season] = filepath.Dir(it.Path)
		}
	}
	for _, se := range seasons {
		dir, ok := dirForSeason[se.Number]
		if !ok {
			continue
		}
		sd := SeasonData{
			SeasonNumber: se.Number, Title: se.Title,
			ShowTitle: full.Title, ShowYear: showYear,
			TMDBID: full.TMDBID, IMDBID: full.IMDBID,
		}
		existing, _ := os.ReadFile(filepath.Join(dir, seasonFilename(se.Number)))
		var unknown []UnknownElement
		var agg UserData
		if len(existing) > 0 {
			if ud, err := ParseTV(existing); err == nil && ud.Root == "season" {
				agg = ud.Aggregate()
				unknown = ud.Unknown
			}
		}
		if agg.DateAdded == "" {
			if t, err := time.Parse(time.RFC3339, se.CreatedAt); err == nil {
				agg.DateAdded = t.Format("2006-01-02 15:04:05")
			}
		}
		sd.UserData = UserData{DateAdded: agg.DateAdded, Watched: agg.Watched, Playcount: agg.Playcount}
		sd.Unknown = unknown
		doc := BuildSeason(sd, flavor)
		path := filepath.Join(dir, seasonFilename(se.Number))
		w := len(existing) == 0 || !Unchanged(string(existing), doc)
		if w {
			if err := s.ensureWithinLibrary(ctx, full.LibraryID, path); err != nil {
				return results, err
			}
			if err := writeAtomic(path, doc); err != nil {
				return results, err
			}
		}
		results = append(results, TVWriteResult{Path: path, Kind: "season", Written: w})
	}

	// --- <basename>.nfo per episode item ---
	bySE := map[string]tv.Episode{}
	for _, e := range episodes {
		bySE[fmt.Sprintf("%d:%d", e.SeasonNumber, e.EpisodeNumber)] = e
	}
	for _, it := range items {
		dir := filepath.Dir(it.Path)
		base := strings.TrimSuffix(it.Filename, filepath.Ext(it.Filename))
		target := filepath.Join(dir, base+".nfo")

		// Which episode(s) does this item cover? Parsed JSON of the item.
		var nums []int
		seasonNum := 0
		localTitle := ""
		if row, err := s.tv.EpisodeNumbersForItem(ctx, it.ItemID); err == nil {
			seasonNum = row.Season
			nums = row.Episodes
			localTitle = row.LocalTitle
		}
		if len(nums) == 0 {
			continue
		}

		existing, _ := os.ReadFile(target)
		var ud UserData
		var unknown []UnknownElement
		if len(existing) > 0 {
			if tud, err := ParseTV(existing); err == nil && tud.Root == "episodedetails" {
				ud = tud.Aggregate()
				unknown = tud.Unknown
			}
		}
		if ud.DateAdded == "" {
			if t, err := time.Parse(time.RFC3339, it.CreatedAt); err == nil {
				ud.DateAdded = t.Format("2006-01-02 15:04:05")
			}
		}

		var eps []tv.Episode
		for _, n := range nums {
			e := bySE[fmt.Sprintf("%d:%d", seasonNum, n)]
			eps = append(eps, e)
		}
		ed := EpisodeData{
			Title:         firstNonEmpty(epTitle(eps), localTitle),
			EpisodeTMDBID: firstTMDB(eps),
			ShowTitle:     full.Title,
			Season:        seasonNum, Episodes: nums,
			AirDate: firstAirDate(eps), Runtime: firstRuntime(eps),
			Rating: firstRating(eps), Votes: firstVotes(eps),
			Plot:       firstPlot(eps),
			ShowTMDBID: full.TMDBID, ShowIMDBID: full.IMDBID,
			UserData: ud, Unknown: unknown,
		}
		doc := BuildEpisodes(ed, flavor)
		w := len(existing) == 0 || !Unchanged(string(existing), doc)
		if w {
			if err := s.ensureWithinLibrary(ctx, full.LibraryID, target); err != nil {
				return results, err
			}
			if err := writeAtomic(target, doc); err != nil {
				return results, err
			}
		}
		results = append(results, TVWriteResult{Path: target, Kind: "episode", Written: w})
	}
	return results, nil
}

func seasonFilename(season int) string {
	if season == 0 {
		return "season-specials.nfo"
	}
	return fmt.Sprintf("season%02d.nfo", season)
}

func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if v != "" {
			return v
		}
	}
	return ""
}
func firstAirDate(eps []tv.Episode) string {
	for _, e := range eps {
		if e.AirDate != "" {
			return e.AirDate
		}
	}
	return ""
}
func firstRuntime(eps []tv.Episode) int {
	for _, e := range eps {
		if e.Runtime > 0 {
			return e.Runtime
		}
	}
	return 0
}
func firstRating(eps []tv.Episode) float64 {
	for _, e := range eps {
		if e.Rating > 0 {
			return e.Rating
		}
	}
	return 0
}
func firstVotes(eps []tv.Episode) int {
	for _, e := range eps {
		if e.Votes > 0 {
			return e.Votes
		}
	}
	return 0
}
func firstPlot(eps []tv.Episode) string {
	for _, e := range eps {
		if e.Plot != "" {
			return e.Plot
		}
	}
	return ""
}
func firstTMDB(eps []tv.Episode) int {
	for _, e := range eps {
		if e.TMDBID > 0 {
			return e.TMDBID
		}
	}
	return 0
}
func epTitle(eps []tv.Episode) string {
	for _, e := range eps {
		if e.Title != "" {
			return e.Title
		}
	}
	return ""
}

var seasonFolderNameRe = regexp.MustCompile(`^(s|season|saison|staffel|temporada|serie)\s*\d{1,2}$|^specials?$`)
