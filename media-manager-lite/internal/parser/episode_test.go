package parser

import (
	"reflect"
	"testing"
)

func TestParseEpisode(t *testing.T) {
	i := func(n int) *int { return &n }

	cases := []struct {
		name string
		in   string
		want ParsedEpisode
	}{
		// --- SxxEyy core ---
		{"basic sxe", "Show.Name.S01E02.720p.mkv", ParsedEpisode{Season: 1, Episodes: []int{2}}},
		{"sxe with title", "Show.Name.S02E10.The.One.mkv", ParsedEpisode{Season: 2, Episodes: []int{10}, Title: "The One"}},
		{"sxe minimal", "S01E02.mkv", ParsedEpisode{Season: 1, Episodes: []int{2}}},
		{"sxe padded", "Show.S010E002.mkv", ParsedEpisode{Season: 10, Episodes: []int{2}}},
		{"sxe spaces", "Show S01 E02.mkv", ParsedEpisode{Season: 1, Episodes: []int{2}}},
		{"sxe lowercase", "show.name.s01e02.mkv", ParsedEpisode{Season: 1, Episodes: []int{2}}},

		// --- multi-episode ---
		{"range dash", "Show.S01E02-E03.mkv", ParsedEpisode{Season: 1, Episodes: []int{2, 3}}},
		{"range short", "Show.S01E02-03.mkv", ParsedEpisode{Season: 1, Episodes: []int{2, 3}}},
		{"stacked E", "Show.S01E02E03.mkv", ParsedEpisode{Season: 1, Episodes: []int{2, 3}}},
		{"stacked x3", "Show.S01E02E03E04.mkv", ParsedEpisode{Season: 1, Episodes: []int{2, 3, 4}}},
		{"wide range", "Show.S01E02-E05.mkv", ParsedEpisode{Season: 1, Episodes: []int{2, 3, 4, 5}}},
		{"range dash spaces", "Show.S01E02 - E03.mkv", ParsedEpisode{Season: 1, Episodes: []int{2, 3}}},

		// --- 1x02 ---
		{"axx basic", "Show.Name.1x02.mkv", ParsedEpisode{Season: 1, Episodes: []int{2}}},
		{"axx two digits", "Show.12x05.mkv", ParsedEpisode{Season: 12, Episodes: []int{5}}},
		{"axx range", "Show.1x02-03.mkv", ParsedEpisode{Season: 1, Episodes: []int{2, 3}}},

		// --- word forms ---
		{"season episode words", "Show.Season.1.Episode.2.mkv", ParsedEpisode{Season: 1, Episodes: []int{2}}},
		{"saison", "Show.Saison.2.Episode.7.mkv", ParsedEpisode{Season: 2, Episodes: []int{7}}},
		{"staffel", "Show.Staffel.3.Episode.4.mkv", ParsedEpisode{Season: 3, Episodes: []int{4}}},
		{"temporada", "Show.Temporada.1.Episodio.5.mkv", ParsedEpisode{Season: 1, Episodes: []int{5}}},
		{"s e short", "Show.S1.E2.mkv", ParsedEpisode{Season: 1, Episodes: []int{2}}},

		// --- date-based daily shows ---
		{"date iso", "Show.2021.03.05.mkv", ParsedEpisode{Season: 2021, Episodes: []int{305}}},
		{"date iso dashed", "Show.2021-03-05.mkv", ParsedEpisode{Season: 2021, Episodes: []int{305}}},
		// Ambiguous 03.05 resolves as DD.MM (TMM behavior): May 3rd.
		{"date us", "Show.03.05.2021.mkv", ParsedEpisode{Season: 2021, Episodes: []int{503}}},
		{"date underscore", "Show.2021_12_31.mkv", ParsedEpisode{Season: 2021, Episodes: []int{1231}}},

		// --- Part / Disc ---
		{"part arabic", "Show.S01.Part.3.mkv", ParsedEpisode{Season: 1, Episodes: []int{3}}},
		{"part roman", "Show.Part.IV.mkv", ParsedEpisode{Season: 1, Episodes: []int{4}}},
		{"disc arabic", "Show.Disc.2.mkv", ParsedEpisode{Season: 1, Episodes: []int{2}}},
		{"part lowercase roman", "Show.Part.vi.mkv", ParsedEpisode{Season: 1, Episodes: []int{6}}},

		// --- anime styles (absolute numbering) ---
		{"anime group prepend", "[SubGroup] Show - 01.mkv", ParsedEpisode{Season: 1, Episodes: []int{1}, Absolute: i(1)}},
		{"anime versioned", "[SubGroup] Show - 12v2.mkv", ParsedEpisode{Season: 1, Episodes: []int{12}, Absolute: i(12)}},
		{"anime plain", "Show - 07.mkv", ParsedEpisode{Season: 1, Episodes: []int{7}, Absolute: i(7)}},
		{"anime braced title", "[SubGroup][Show][13][1080p].mkv", ParsedEpisode{Season: 1, Episodes: []int{13}, Absolute: i(13)}},
		{"anime 3-digit", "Show - 105.mkv", ParsedEpisode{Season: 1, Episodes: []int{105}, Absolute: i(105)}},

		// --- episode only ---
		{"episode only word", "Show.Episode.5.mkv", ParsedEpisode{Season: 1, Episodes: []int{5}}},
		{"ep short", "Show.EP.12.mkv", ParsedEpisode{Season: 1, Episodes: []int{12}}},

		// --- titles / languages ---
		{"chinese title after marker", "北斗.S01E02.第一话.mkv", ParsedEpisode{Season: 1, Episodes: []int{2}, Title: "第一话"}},
		{"chinese show flat", "北斗.S01E05.mkv", ParsedEpisode{Season: 1, Episodes: []int{5}}},
		{"japanese title", "Show.S03E01.名場面.mkv", ParsedEpisode{Season: 3, Episodes: []int{1}, Title: "名場面"}},
		{"korean show", ".Show.S01E08.mkv", ParsedEpisode{Season: 1, Episodes: []int{8}}},

		// --- special seasons ---
		{"specials s00", "Show.S00E01.Web.Special.mkv", ParsedEpisode{Season: 0, Episodes: []int{1}, Title: "Web Special"}},
		{"high season", "Show.S21E15.mkv", ParsedEpisode{Season: 21, Episodes: []int{15}}},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := ParseEpisode(tc.in)
			if got.Season != tc.want.Season {
				t.Errorf("Season = %d, want %d", got.Season, tc.want.Season)
			}
			if !reflect.DeepEqual(got.Episodes, tc.want.Episodes) {
				t.Errorf("Episodes = %v, want %v", got.Episodes, tc.want.Episodes)
			}
			if (got.Absolute == nil) != (tc.want.Absolute == nil) ||
				(got.Absolute != nil && *got.Absolute != *tc.want.Absolute) {
				t.Errorf("Absolute = %v, want %v", got.Absolute, tc.want.Absolute)
			}
			if !got.Detected() {
				t.Errorf("marker not detected in %q", tc.in)
			}
		})
	}

	// Title checks (position-dependent, checked separately for clarity).
	titleCases := []struct {
		in   string
		want string
	}{
		{"Show.Name.S01E02.The.One.Where.Everything.Happens.mkv", "The One Where Everything Happens"},
		{"北斗.S01E02.第一话.mkv", "第一话"},
		{"Show.S01E03.mkv", ""}, // nothing after the marker
	}
	for _, tc := range titleCases {
		if got := ParseEpisode(tc.in).Title; got != tc.want {
			t.Errorf("ParseEpisode(%q).Title = %q, want %q", tc.in, got, tc.want)
		}
	}
}

func TestParseEpisodeNotDetected(t *testing.T) {
	negatives := []string{
		"Movie.Name.2019.1080p.BluRay.x264.mkv",
		"random video.mkv",
		"poster.jpg",
		"some.file.txt",
		"1984.mkv", // a bare year is not an episode
	}
	for _, in := range negatives {
		got := ParseEpisode(in)
		if got.Detected() {
			t.Errorf("ParseEpisode(%q) unexpectedly detected: %+v", in, got)
		}
	}
}

func TestEpisodeShowTitle(t *testing.T) {
	cases := []struct {
		name     string
		root     string
		path     string
		filename string
		want     string
	}{
		{"show folder", "/tv", "/tv/Breaking Bad/Season 1/tv_ep.mkv", "Breaking Bad", "Breaking Bad"},
		{"show folder with year", "/tv", "/tv/Office (2005)/S01/e.mkv", "e.mkv", "Office"},
		{"nested deeper", "/tv", "/tv/北平无战事/Season 1/sub/e.mkv", "e.mkv", "北平无战事"},
		{"flat filename", "/tv", "/tv/Show.Name.S01E02.mkv", "Show.Name.S01E02.mkv", "Show Name"},
		{"flat chinese", "/tv", "/tv/北斗.S01E02.mkv", "北斗.S01E02.mkv", "北斗"},
		{"season folder fallback", "/tv", "/tv/Season 1/e.mkv", "e.mkv", "e"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := EpisodeShowTitle(tc.root, tc.path, tc.filename); got != tc.want {
				t.Errorf("EpisodeShowTitle = %q, want %q", got, tc.want)
			}
		})
	}
}

func TestIsSeasonFolder(t *testing.T) {
	positive := []string{"Season 1", "Season 01", "S01", "s2", "Staffel 3", "Specials", "season extras"}
	for _, in := range positive {
		if !isSeasonFolder(in) {
			t.Errorf("isSeasonFolder(%q) = false, want true", in)
		}
	}
	negative := []string{"Breaking Bad", "Season", "Seasons", "S01E02 folder"}
	for _, in := range negative {
		if isSeasonFolder(in) {
			t.Errorf("isSeasonFolder(%q) = true, want false", in)
		}
	}
}
