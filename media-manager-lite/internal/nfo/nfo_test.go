package nfo

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// sampleData is the shared fixture: Chinese metadata, a movie set, ids,
// ratings, artwork URLs, preserved user data and unknown elements.
func sampleData() MovieData {
	return MovieData{
		Title:         "盗梦空间",
		OriginalTitle: "Inception",
		Year:          2010,
		Rating:        8.4,
		Votes:         35000,
		SetName:       "盗梦空间合集",
		SetTMDBID:     119,
		Plot:          "一名造梦师，深入他人梦境窃取机密。",
		Tagline:       "你的梦，是他人的现实。",
		Runtime:       148,
		Certification: "PG-13",
		CertCountry:   "US",
		TMDBID:        27205,
		IMDBID:        "tt1375666",
		Premiered:     "2010-09-01",
		Genres:        []string{"动作", "科幻"},
		Studios:       []string{"Warner Bros. Pictures"},
		Writers:       []string{"Christopher Nolan"},
		Directors:     []string{"Christopher Nolan"},
		Actors: []Actor{
			{Name: "莱昂纳多·迪卡普里奥", Role: "Cobb", Thumb: "http://image.example/h632/leo.jpg"},
			{Name: "约瑟夫·高登-莱维特", Role: "Arthur"},
		},
		PosterURL: "http://image.example/original/inc.jpg",
		FanartURL: "http://image.example/original/inc-fan.jpg",
		UserData: UserData{
			Watched:    true,
			Playcount:  2,
			LastPlayed: "2026-10-01 11:00:00",
			DateAdded:  "2026-10-01 12:00:00",
			UserRating: 9.0,
		},
		Unknown: []UnknownElement{
			{Name: "resume", Inner: "<position>0</position><total>8880</total>"},
			{Name: "epbookmark"},
			{Name: "fileinfo", Inner: "<streamdetails><video><codec>h264</codec></video></streamdetails>"},
			{Name: "customtag", Attrs: []Attr{{Name: "source", Value: "legacy &amp; &lt;old&gt;"}}, Inner: "keeps &amp; &lt;special&gt; chars"},
		},
	}
}

// goldenCompare compares got against testdata/<name>.xml, writing the file
// first when UPDATE_GOLDEN=1 is set.
func goldenCompare(t *testing.T, name, got string) {
	t.Helper()
	path := filepath.Join("testdata", name+".xml")
	if os.Getenv("UPDATE_GOLDEN") != "" {
		if err := os.WriteFile(path, []byte(got), 0o644); err != nil {
			t.Fatal(err)
		}
		return
	}
	want, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("golden file missing (%s); run with UPDATE_GOLDEN=1 to create: %v", path, err)
	}
	if got != string(want) {
		t.Errorf("output differs from golden %s:\n--- got ---\n%s\n--- want ---\n%s", path, got, string(want))
	}
}

func TestBuildGolden(t *testing.T) {
	d := sampleData()
	for _, f := range []Flavor{Kodi, Emby, Jellyfin} {
		t.Run(string(f), func(t *testing.T) {
			SetStamp("2026-10-01 12:00:00")
			defer SetStamp("")
			goldenCompare(t, "movie_"+string(f), Build(d, f))
		})
	}
}

func TestBuildFlavorDifferences(t *testing.T) {
	d := sampleData()
	kodi := Build(d, Kodi)
	emby := Build(d, Emby)
	jellyfin := Build(d, Jellyfin)

	// Emby: credits/directors after actors + lockdata.
	if strings.Index(emby, "<actor>") > strings.Index(emby, "<credits>") {
		t.Error("emby must emit actors before credits")
	}
	if !strings.Contains(emby, "<lockdata>true</lockdata>") {
		t.Error("emby must emit lockdata")
	}
	// Kodi: credits/directors before actors, no lockdata.
	if strings.Index(kodi, "<credits>") > strings.Index(kodi, "<actor>") {
		t.Error("kodi must emit credits before actors")
	}
	if strings.Contains(kodi, "lockdata") {
		t.Error("kodi must not emit lockdata")
	}
	// Jellyfin: collectionnumber; lockdata is Emby-only.
	if !strings.Contains(jellyfin, "<collectionnumber>119</collectionnumber>") {
		t.Error("jellyfin must emit collectionnumber for the movie set")
	}
	if strings.Contains(jellyfin, "lockdata") {
		t.Error("jellyfin must not emit lockdata")
	}
}

func TestUniqueidOrdering(t *testing.T) {
	withImdb := Build(sampleData(), Kodi)
	if !strings.Contains(withImdb, `<uniqueid type="imdb" default="true">tt1375666</uniqueid>`) {
		t.Error("imdb uniqueid must carry default=true when present")
	}
	if strings.Contains(withImdb, `<uniqueid type="tmdb" default="true">`) {
		t.Error("tmdb uniqueid must not be default when an imdb id exists")
	}

	d := sampleData()
	d.IMDBID = ""
	onlyTMDB := Build(d, Kodi)
	if !strings.Contains(onlyTMDB, `<uniqueid type="tmdb" default="true">27205</uniqueid>`) {
		t.Error("tmdb uniqueid must be default when no imdb id exists")
	}
}

func TestYearEmptyWhenZero(t *testing.T) {
	d := sampleData()
	d.Year = 0
	out := Build(d, Kodi)
	if !strings.Contains(out, "<year />") {
		t.Error("year must be an empty element when 0")
	}
}

func TestRoundTripPreservesUserDataAndUnknowns(t *testing.T) {
	existing := `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<movie>
  <title>Old Title</title>
  <year>1999</year>
  <watched>true</watched>
  <playcount>3</playcount>
  <lastplayed>2025-12-24 22:10:00</lastplayed>
  <dateadded>2025-01-02 08:00:00</dateadded>
  <userrating>7.5</userrating>
  <resume><position>120</position><total>8880</total></resume>
  <epbookmark>300</epbookmark>
  <trailer>plugin://plugin.video.youtube/play/?video_id=xyz</trailer>
  <lockdata>true</lockdata>
</movie>`

	ud, unknown, err := Parse([]byte(existing))
	if err != nil {
		t.Fatal(err)
	}
	if !ud.Watched || ud.Playcount != 3 {
		t.Errorf("user data = %+v", ud)
	}
	if ud.UserRating != 7.5 || ud.LastPlayed == "" || ud.DateAdded == "" {
		t.Errorf("user data = %+v", ud)
	}

	names := map[string]bool{}
	for _, u := range unknown {
		names[u.Name] = true
	}
	// lockdata is dropped on purpose; the rest survive.
	for _, want := range []string{"resume", "epbookmark", "trailer"} {
		if !names[want] {
			t.Errorf("unknown element %q lost", want)
		}
	}
	if names["lockdata"] {
		t.Error("lockdata should be dropped like in TMM")
	}

	merged := MergeUserData(ud)
	if merged.Playcount != 3 || !merged.Watched {
		t.Errorf("merged = %+v", merged)
	}

	// Rebuild with fresh metadata but the parsed user data + unknowns.
	d := sampleData()
	d.UserData = merged
	d.Unknown = unknown
	out := Build(d, Kodi)
	if !strings.Contains(out, "<playcount>3</playcount>") {
		t.Error("playcount not preserved")
	}
	if !strings.Contains(out, "<userrating>7.5</userrating>") {
		t.Error("userrating not preserved")
	}
	if !strings.Contains(out, "<lastplayed>2025-12-24 22:10:00</lastplayed>") {
		t.Error("lastplayed not preserved")
	}
	if !strings.Contains(out, "<resume><position>120</position><total>8880</total></resume>") {
		t.Error("resume element not preserved verbatim")
	}
	if !strings.Contains(out, "<epbookmark>300</epbookmark>") {
		t.Error("epbookmark not preserved")
	}
	if !strings.Contains(out, "<trailer>plugin://plugin.video.youtube/play/?video_id=xyz</trailer>") {
		t.Error("trailer not preserved")
	}
	if strings.Contains(out, "Old Title") {
		t.Error("stale title must be replaced by fresh metadata")
	}

	// Round-trip stability: parsing our own output again yields the same
	// user data and unknowns.
	ud2, unknown2, err := Parse([]byte(out))
	if err != nil {
		t.Fatal(err)
	}
	if ud2.Playcount != 3 || !ud2.Watched || ud2.UserRating != 7.5 {
		t.Errorf("second parse user data = %+v", ud2)
	}
	if len(unknown2) != len(unknown) {
		t.Errorf("second parse unknown count = %d, want %d", len(unknown2), len(unknown))
	}
}

func TestPlaycountImpliesWatched(t *testing.T) {
	ud, _, err := Parse([]byte("<movie><playcount>5</playcount></movie>"))
	if err != nil {
		t.Fatal(err)
	}
	if !ud.Watched || ud.Playcount != 5 {
		t.Errorf("user data = %+v", ud)
	}
	merged := MergeUserData(UserData{Watched: true})
	if merged.Playcount != 1 {
		t.Errorf("watched without playcount must yield playcount 1, got %d", merged.Playcount)
	}
}

func TestParseRejectsNonMovieRoot(t *testing.T) {
	if _, _, err := Parse([]byte("<tvshow><title>x</title></tvshow>")); err != ErrNotMovieNFO {
		t.Errorf("error = %v, want ErrNotMovieNFO", err)
	}
	if _, _, err := Parse([]byte("")); err != ErrNotMovieNFO {
		t.Errorf("error = %v, want ErrNotMovieNFO", err)
	}
}

func TestBuildDeterministic(t *testing.T) {
	a := Build(sampleData(), Kodi)
	b := Build(sampleData(), Kodi)
	if a != b {
		t.Error("Build must be deterministic for identical input")
	}
}

func TestUnchangedModuloComments(t *testing.T) {
	SetStamp("2026-01-01 00:00:00")
	a := Build(sampleData(), Kodi)
	SetStamp("2027-12-31 23:59:59")
	b := Build(sampleData(), Kodi)
	SetStamp("")
	if a == b {
		t.Fatal("stamps should differ")
	}
	if !Unchanged(a, b) {
		t.Error("outputs differing only in the comment must be unchanged")
	}
	if Unchanged(a, Build(sampleData(), Emby)) {
		t.Error("different flavors must not be unchanged")
	}
}

func TestEscapeSpecialCharacters(t *testing.T) {
	d := MovieData{Title: `Action & <Sci-Fi> "Thriller"`, Plot: "a < b & c > d"}
	out := Build(d, Kodi)
	if !strings.Contains(out, "<title>Action &amp; &lt;Sci-Fi&gt; &#34;Thriller&#34;</title>") {
		t.Errorf("title escaping broken: %s", out)
	}
	if !strings.Contains(out, "<plot>a &lt; b &amp; c &gt; d</plot>") {
		t.Errorf("plot escaping broken: %s", out)
	}
}
