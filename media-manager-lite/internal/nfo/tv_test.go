package nfo

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func sampleTVShow() TVShowData {
	return TVShowData{
		Title: "北斗剧集", OriginalTitle: "北斗", Year: 2021,
		Rating: 8.1, Votes: 99,
		Plot: "一部关于北方星辰的剧集。", Status: "Returning Series",
		Certification: "TV-MA", CertCountry: "US",
		TMDBID: 555, IMDBID: "tt555",
		Premiered: "2021-05-01",
		Genres:    []string{"Drama"},
		Studios:   []string{"Example Studios"},
		UserData:  UserData{Watched: true, Playcount: 2, LastPlayed: "2026-10-01 11:00:00", DateAdded: "2026-10-01 12:00:00", UserRating: 8.0},
	}
}

func sampleTVEpisode() EpisodeData {
	return EpisodeData{
		Title: "启程", ShowTitle: "北斗剧集",
		Season: 1, Episodes: []int{1},
		AirDate: "2021-05-01", Runtime: 45, Rating: 7.5, Votes: 10,
		Plot: "故事从北方开始。", ShowTMDBID: 555, ShowIMDBID: "tt555",
		UserData: UserData{Watched: true, Playcount: 1, DateAdded: "2026-10-01 12:00:00"},
	}
}

func TestBuildTVGolden(t *testing.T) {
	for _, f := range []Flavor{Kodi, Emby, Jellyfin} {
		t.Run("show_"+string(f), func(t *testing.T) {
			SetStamp("2026-10-01 12:00:00")
			defer SetStamp("")
			goldenCompare(t, "tvshow_"+string(f), BuildTVShow(sampleTVShow(), f))
		})
		t.Run("season_"+string(f), func(t *testing.T) {
			SetStamp("2026-10-01 12:00:00")
			defer SetStamp("")
			goldenCompare(t, "season_"+string(f), BuildSeason(SeasonData{
				SeasonNumber: 1, Title: "Season 1", ShowTitle: "北斗剧集",
				ShowYear: 2021, TMDBID: 555, IMDBID: "tt555",
				UserData: UserData{DateAdded: "2026-10-01 12:00:00"},
			}, f))
		})
		t.Run("episode_"+string(f), func(t *testing.T) {
			SetStamp("2026-10-01 12:00:00")
			defer SetStamp("")
			goldenCompare(t, "episode_"+string(f), BuildEpisodes(sampleTVEpisode(), f))
		})
	}
}

func TestBuildMultiEpisode(t *testing.T) {
	d := sampleTVEpisode()
	d.Episodes = []int{6, 7}
	d.EpisodeTMDBID = 9007

	kodi := BuildEpisodes(d, Kodi)
	if got := strings.Count(kodi, "<episodedetails>"); got != 2 {
		t.Errorf("roots = %d, want 2", got)
	}
	if got := strings.Count(kodi, `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>`); got != 1 {
		t.Errorf("XML declarations = %d, want 1 (first block only)", got)
	}
	if !strings.Contains(kodi, "<episode>6</episode>") || !strings.Contains(kodi, "<episode>7</episode>") {
		t.Error("episode numbers missing")
	}

	emby := BuildEpisodes(d, Emby)
	if !strings.Contains(emby, "<episodenumberend>7</episodenumberend>") {
		t.Error("emby must emit episodenumberend for multi-episode files")
	}
}

func TestTVNFOUserDateMerge(t *testing.T) {
	existing := `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<episodedetails>
  <title>Old</title>
  <watched>true</watched>
  <playcount>4</playcount>
  <dateadded>2025-02-02 08:00:00</dateadded>
  <resume><position>300</position><total>2700</total></resume>
  <customtag a="1">keep</customtag>
</episodedetails>`

	ud, err := ParseTV([]byte(existing))
	if err != nil {
		t.Fatal(err)
	}
	if ud.Root != "episodedetails" || len(ud.Blocks) != 1 {
		t.Fatalf("parse = %+v", ud)
	}
	agg := ud.Aggregate()
	if !agg.Watched || agg.Playcount != 4 || agg.DateAdded != "2025-02-02 08:00:00" {
		t.Errorf("aggregate = %+v", agg)
	}
	foundResume, foundCustom := false, false
	for _, u := range ud.Unknown {
		if u.Name == "resume" && strings.Contains(u.Inner, "<position>300</position>") {
			foundResume = true
		}
		if u.Name == "customtag" {
			for _, a := range u.Attrs {
				if a.Name == "a" && a.Value == "1" {
					foundCustom = true
				}
			}
		}
	}
	if !foundResume || !foundCustom {
		t.Errorf("unknown elements lost: %+v", ud.Unknown)
	}

	// Rebuild merges user data + unknowns, fresh metadata wins on title.
	d := sampleTVEpisode()
	d.UserData = MergeUserData(agg)
	d.Unknown = ud.Unknown
	out := BuildEpisodes(d, Kodi)
	if !strings.Contains(out, "<playcount>4</playcount>") {
		t.Error("playcount not preserved")
	}
	if !strings.Contains(out, "<dateadded>2025-02-02 08:00:00</dateadded>") {
		t.Error("dateadded not preserved")
	}
	if !strings.Contains(out, "<resume><position>300</position><total>2700</total></resume>") {
		t.Error("resume not preserved")
	}
	if strings.Contains(out, "<title>Old</title>") {
		t.Error("stale title must be replaced")
	}

	// Round-trip stability.
	ud2, err := ParseTV([]byte(out))
	if err != nil {
		t.Fatal(err)
	}
	if ud2.Aggregate().Playcount != 4 {
		t.Errorf("second parse playcount = %d", ud2.Aggregate().Playcount)
	}
}

func TestParseTVShowRoot(t *testing.T) {
	doc := `<?xml version="1.0"?>
<tvshow><title>X</title><userrating>6.5</userrating><unknown1>keep</unknown1></tvshow>`
	ud, err := ParseTV([]byte(doc))
	if err != nil {
		t.Fatal(err)
	}
	if ud.Root != "tvshow" || len(ud.Blocks) != 1 {
		t.Fatalf("parse = %+v", ud)
	}
	if ud.Blocks[0].UserRating != 6.5 {
		t.Errorf("userrating = %v", ud.Blocks[0].UserRating)
	}
	if len(ud.Unknown) != 1 || ud.Unknown[0].Name != "unknown1" {
		t.Errorf("unknown = %+v", ud.Unknown)
	}
	if _, err := ParseTV([]byte("<movie><title>x</title></movie>")); err != ErrNotMovieNFO {
		t.Errorf("movie root error = %v, want ErrNotMovieNFO", err)
	}
}

func TestSeasonFilename(t *testing.T) {
	if seasonFilename(0) != "season-specials.nfo" {
		t.Errorf("specials = %q", seasonFilename(0))
	}
	if seasonFilename(2) != "season02.nfo" {
		t.Errorf("season 2 = %q", seasonFilename(2))
	}
}

func TestWriteTVForShowIntegration(t *testing.T) {
	// End-to-end through the service is covered by tests/integration_test.go;
	// here we verify golden stability of a second write (unchanged skip).
	d := sampleTVShow()
	a := BuildTVShow(d, Kodi)
	b := BuildTVShow(d, Kodi)
	if a != b {
		t.Error("BuildTVShow must be deterministic")
	}
	dir := t.TempDir()
	path := filepath.Join(dir, "tvshow.nfo")
	if err := writeAtomic(path, a); err != nil {
		t.Fatal(err)
	}
	existing, _ := os.ReadFile(path)
	if !Unchanged(string(existing), b) {
		t.Error("second write should be unchanged")
	}
}
