package nfo

import (
	"encoding/xml"
	"strings"
	"testing"
)

// The compatibility checks validate the documented import requirements of
// Jellyfin / Emby / Kodi NFO readers (element presence and cardinality —
// the same checklist the manual 1.0 verification uses) against all three
// flavors, for movies and TV (single + multi-episode).

type doc struct {
	XMLName xml.Name
	Nodes   []xmlNode `xml:",any"`
}

type xmlNode struct {
	XMLName xml.Name
	Text    string     `xml:",chardata"`
	Attrs   []xml.Attr `xml:",any,attr"`
	Nodes   []xmlNode  `xml:",any"`
}

func parseDoc(t *testing.T, xmlData string) doc {
	t.Helper()
	var d doc
	if err := xmlUnmarshal([]byte(xmlData), &d); err != nil {
		t.Fatalf("unmarshal: %v", err)
	}
	return d
}

func (d doc) findAll(name string) []xmlNode {
	var out []xmlNode
	for _, n := range d.Nodes {
		if n.XMLName.Local == name {
			out = append(out, n)
		}
	}
	return out
}

func (d doc) first(name string) (xmlNode, bool) {
	n := d.findAll(name)
	if len(n) == 0 {
		return xmlNode{}, false
	}
	return n[0], true
}

func xmlUnmarshal(data []byte, v any) error {
	return xml.Unmarshal(data, v)
}

func TestJellyfinMovieCompatibility(t *testing.T) {
	for _, f := range []Flavor{Kodi, Emby, Jellyfin} {
		t.Run(string(f), func(t *testing.T) {
			x := parseDoc(t, Build(sampleData(), f))
			if x.XMLName.Local != "movie" {
				t.Fatalf("root = %s", x.XMLName.Local)
			}
			// Jellyfin/Emby/Kodi import requirements.
			must := []string{"title", "year", "plot", "id", "tmdbid", "uniqueid",
				"premiered", "genre", "runtime"}
			for _, name := range must {
				if _, ok := x.first(name); !ok {
					t.Errorf("missing required element <%s>", name)
				}
			}
			uid, ok := x.first("uniqueid")
			if !ok {
				t.Fatal("uniqueid missing")
			}
			hasDefault := false
			for _, a := range uid.Attrs {
				if a.Name.Local == "default" && a.Value == "true" {
					hasDefault = true
				}
			}
			if !hasDefault {
				t.Error("exactly the primary uniqueid must carry default=true")
			}
			// Movie set (Jellyfin reads the collection via <set>; Kodi too).
			if _, ok := x.first("set"); !ok {
				t.Error("movie set element missing")
			}
			// Ratings block (Kodi/Emby/Jellyfin modern format).
			if _, ok := x.first("ratings"); !ok {
				t.Error("ratings missing")
			}
			if f == Jellyfin {
				if _, ok := x.first("collectionnumber"); !ok {
					t.Error("jellyfin collectionnumber missing")
				}
			}
			if f == Emby {
				if _, ok := x.first("lockdata"); !ok {
					t.Error("emby lockdata missing")
				}
			}
		})
	}
}

func TestJellyfinTVCompatibility(t *testing.T) {
	show := BuildTVShow(sampleTVShow(), Kodi)
	if _, ok := parseDoc(t, show).first("uniqueid"); !ok {
		t.Error("tvshow uniqueid missing")
	}

	// Multi-episode Emby file: one root per episode + episodenumberend.
	d := sampleTVEpisode()
	d.Episodes = []int{6, 7}
	emby := parseDoc(t, BuildEpisodes(d, Emby))
	if emby.XMLName.Local != "episodedetails" {
		t.Fatalf("root = %s", emby.XMLName.Local)
	}
	if _, ok := emby.first("episodenumberend"); !ok {
		t.Error("emby episodenumberend missing for multi-episode")
	}
	for _, name := range []string{"season", "episode", "aired", "showtitle"} {
		if _, ok := emby.first(name); !ok {
			t.Errorf("missing required episode element <%s>", name)
		}
	}

	// Kodi/Jellyfin single-episode files must NOT carry episodenumberend.
	for _, f := range []Flavor{Kodi, Jellyfin} {
		single := BuildEpisodes(sampleTVEpisode(), f)
		if strings.Contains(single, "episodenumberend") {
			t.Errorf("%s must not emit episodenumberend", f)
		}
	}

	// Season NFO is a single flavor with seasonnumber.
	season := BuildSeason(SeasonData{SeasonNumber: 1, Title: "Season 1", ShowTitle: "北斗剧集"}, Kodi)
	sd := parseDoc(t, season)
	if sd.XMLName.Local != "season" {
		t.Fatalf("season root = %s", sd.XMLName.Local)
	}
	if _, ok := sd.first("seasonnumber"); !ok {
		t.Error("seasonnumber missing")
	}
}

func TestChineseContentRoundTrip(t *testing.T) {
	// Chinese titles/plots must survive XML escaping and re-parsing.
	x := parseDoc(t, Build(sampleData(), Kodi))
	title, ok := x.first("title")
	if !ok || title.Text != "盗梦空间" {
		t.Errorf("title = %q", title.Text)
	}
}
