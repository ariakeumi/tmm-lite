// Package nfo writes and reads movie NFO files in the Kodi / Emby /
// Jellyfin flavors, replicating tinyMediaManager's behavior spec from the
// Phase-0 analysis (docs/media-manager-lite/phase0-source-analysis.md §3.3):
//
//   - fixed element order, UTF-8 standalone XML, 2-space indent, CRLF
//   - a "created on" comment header; writes are skipped when the content
//     is unchanged modulo comments (write stability)
//   - uniqueid default flag: IMDb when present, else TMDB
//   - Emby: credits/directors emitted after actors, plus <lockdata>
//   - Jellyfin: <collectionnumber> for the movie set
//   - user data (watched/playcount/lastplayed/dateadded/userrating) is
//     merged from an existing NFO with max/newer semantics
//   - unknown root-level elements (resume, fileinfo, trailer, ...) survive
//     rewrites verbatim
package nfo

import (
	"bytes"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"strconv"
	"strings"
)

// Flavor selects the connector output.
type Flavor string

const (
	Kodi     Flavor = "kodi"
	Emby     Flavor = "emby"
	Jellyfin Flavor = "jellyfin"
)

// Valid reports whether f is a supported flavor.
func (f Flavor) Valid() bool {
	switch f {
	case Kodi, Emby, Jellyfin:
		return true
	}
	return false
}

// Attr is a single XML attribute of an unknown element.
type Attr struct {
	Name  string
	Value string
}

// UnknownElement is a root-level element the writer does not manage; it is
// re-emitted verbatim across rewrites (inner XML preserved as-is).
type UnknownElement struct {
	Name  string
	Attrs []Attr
	Inner string
}

// UserData carries the watched-state values preserved across rewrites.
type UserData struct {
	Watched    bool
	Playcount  int
	LastPlayed string // "yyyy-MM-dd HH:mm:ss"
	DateAdded  string // "yyyy-MM-dd HH:mm:ss"
	UserRating float64
}

// Actor is one cast entry.
type Actor struct {
	Name  string
	Role  string
	Thumb string
}

// MovieData is the input for Build. Empty fields are omitted (year is
// always emitted, empty when 0, per the TMM behavior).
type MovieData struct {
	Title         string
	OriginalTitle string
	Year          int
	Rating        float64
	Votes         int
	SetName       string
	SetTMDBID     int
	Plot          string
	Tagline       string
	Runtime       int
	Certification string // e.g. "PG-13"
	CertCountry   string // e.g. "US"
	TMDBID        int
	IMDBID        string
	Premiered     string // yyyy-MM-dd
	Genres        []string
	Studios       []string
	Writers       []string
	Directors     []string
	Actors        []Actor
	PosterURL     string
	FanartURL     string

	UserData
	Unknown []UnknownElement
}

// --- XML building primitives (depth-aware, 2-space indent, CRLF) ---

func escapeText(s string) string {
	var b strings.Builder
	xml.EscapeText(&b, []byte(s))
	return b.String()
}

func escapeAttr(s string) string {
	s = strings.ReplaceAll(s, "&", "&amp;")
	s = strings.ReplaceAll(s, "<", "&lt;")
	s = strings.ReplaceAll(s, ">", "&gt;")
	s = strings.ReplaceAll(s, `"`, "&quot;")
	return s
}

type builder struct {
	b     strings.Builder
	depth int
}

func (w *builder) indent() { w.b.WriteString(strings.Repeat("  ", w.depth)) }
func (w *builder) nl()     { w.b.WriteString("\r\n") }

// elem emits <name>escaped text</name> at the current depth.
func (w *builder) elem(name, text string) {
	w.indent()
	w.b.WriteString("<" + name + ">" + escapeText(text) + "</" + name + ">")
	w.nl()
}

// elemNotEmpty emits the element only when text is non-empty.
func (w *builder) elemNotEmpty(name, text string) {
	if text != "" {
		w.elem(name, text)
	}
}

func (w *builder) elemInt(name string, v int) { w.elem(name, strconv.Itoa(v)) }

func (w *builder) elemList(name string, values []string) {
	for _, v := range values {
		if v != "" {
			w.elem(name, v)
		}
	}
}

// begin opens a container element (<name attrs>) and increases the depth.
func (w *builder) begin(name string, attrs ...string) {
	w.indent()
	w.b.WriteString("<" + name)
	for i := 0; i+1 < len(attrs); i += 2 {
		w.b.WriteString(` ` + attrs[i] + `="` + escapeAttr(attrs[i+1]) + `"`)
	}
	w.b.WriteString(">")
	w.nl()
	w.depth++
}

// end closes a container element.
func (w *builder) end(name string) {
	w.depth--
	w.indent()
	w.b.WriteString("</" + name + ">")
	w.nl()
}

// buildStamp is the comment timestamp. Production callers set it via
// SetStamp; it defaults to empty so Build is deterministic (golden files
// and the unchanged-check are comment-insensitive).
var buildStamp = ""

// SetStamp overrides the comment timestamp.
func SetStamp(s string) { buildStamp = s }

// Build renders a complete movie NFO document for the flavor.
func Build(d MovieData, f Flavor) string {
	w := &builder{}
	w.b.WriteString(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` + "\r\n")
	fmt.Fprintf(&w.b, "<!-- created on %s by Media Manager Lite for %s -->\r\n",
		buildStamp, strings.ToUpper(string(f)))
	w.begin("movie")

	w.elem("title", d.Title)
	w.elemNotEmpty("originaltitle", d.OriginalTitle)
	if d.Year > 0 {
		w.elemInt("year", d.Year)
	} else {
		w.indent()
		w.b.WriteString("<year />")
		w.nl()
	}

	// ratings (Kodi style; the TMDB source is renamed "themoviedb").
	if d.Rating > 0 {
		w.begin("ratings")
		w.begin("rating", "name", "themoviedb", "max", "10", "default", "true")
		w.elem("value", strconv.FormatFloat(d.Rating, 'f', 1, 64))
		w.elemInt("votes", d.Votes)
		w.end("rating")
		w.end("ratings")
	}
	if d.UserRating > 0 {
		w.elem("userrating", strconv.FormatFloat(d.UserRating, 'f', 1, 64))
	}

	// set (basic movie set info; tmdbcolid attribute on all flavors).
	if d.SetName != "" || d.SetTMDBID > 0 {
		if d.SetTMDBID > 0 {
			w.begin("set", "tmdbcolid", strconv.Itoa(d.SetTMDBID))
		} else {
			w.begin("set")
		}
		w.elem("name", d.SetName)
		w.end("set")
	}

	w.elem("plot", d.Plot)
	w.elemNotEmpty("tagline", d.Tagline)
	if d.Runtime > 0 {
		w.elemInt("runtime", d.Runtime)
	}

	if d.PosterURL != "" {
		w.indent()
		w.b.WriteString(`<thumb aspect="poster">` + escapeText(d.PosterURL) + "</thumb>")
		w.nl()
	}
	if d.FanartURL != "" {
		w.begin("fanart")
		w.elem("thumb", d.FanartURL)
		w.end("fanart")
	}

	w.elemNotEmpty("mpaa", d.Certification)
	if d.Certification != "" && d.CertCountry != "" {
		w.elem("certification", d.CertCountry+":"+d.Certification)
	}

	// ids: <id> is the IMDb id (written even when empty), tmdbid explicit.
	w.elem("id", d.IMDBID)
	if d.TMDBID > 0 {
		w.elemInt("tmdbid", d.TMDBID)
	}

	// uniqueid: default flag on IMDb when present, else TMDB.
	if d.IMDBID != "" {
		w.indent()
		w.b.WriteString(`<uniqueid type="imdb" default="true">` + escapeText(d.IMDBID) + "</uniqueid>")
		w.nl()
	}
	if d.TMDBID > 0 {
		w.indent()
		if d.IMDBID != "" {
			w.b.WriteString(`<uniqueid type="tmdb">` + strconv.Itoa(d.TMDBID) + "</uniqueid>")
		} else {
			w.b.WriteString(`<uniqueid type="tmdb" default="true">` + strconv.Itoa(d.TMDBID) + "</uniqueid>")
		}
		w.nl()
	}

	w.elemNotEmpty("premiered", d.Premiered)

	if d.Watched {
		w.elem("watched", "true")
	} else {
		w.elem("watched", "false")
	}
	w.elemInt("playcount", d.Playcount)
	w.elemNotEmpty("lastplayed", d.LastPlayed)

	w.elemList("genre", d.Genres)
	w.elemList("studio", d.Studios)

	// Emby ordering quirk (TMM issue #2780): credits/directors after actors.
	if f == Emby {
		w.emitActors(d)
		w.elemList("credits", d.Writers)
		w.elemList("director", d.Directors)
	} else {
		w.elemList("credits", d.Writers)
		w.elemList("director", d.Directors)
		w.emitActors(d)
	}

	if f == Jellyfin && d.SetTMDBID > 0 {
		w.elemInt("collectionnumber", d.SetTMDBID)
	}
	if f == Emby {
		w.elem("lockdata", "true")
	}

	w.elemNotEmpty("dateadded", d.DateAdded)

	// Unknown root-level elements, verbatim in their original order.
	for _, u := range d.Unknown {
		w.raw(u)
	}

	w.end("movie")
	return w.b.String()
}

func (w *builder) emitActors(d MovieData) {
	for _, a := range d.Actors {
		if a.Name == "" {
			continue
		}
		w.begin("actor")
		w.elem("name", a.Name)
		w.elemNotEmpty("role", a.Role)
		w.elemNotEmpty("thumb", a.Thumb)
		w.end("actor")
	}
}

func (w *builder) raw(u UnknownElement) {
	w.indent()
	w.b.WriteString("<" + u.Name)
	for _, a := range u.Attrs {
		w.b.WriteString(` ` + a.Name + `="` + escapeAttr(a.Value) + `"`)
	}
	if strings.TrimSpace(u.Inner) == "" {
		w.b.WriteString(" />")
		w.nl()
		return
	}
	w.b.WriteString(">")
	w.b.WriteString(u.Inner)
	w.b.WriteString("</" + u.Name + ">")
	w.nl()
}

// --- parsing (round-trip) ---

// ErrNotMovieNFO is returned when the document root is not <movie>.
var ErrNotMovieNFO = errors.New("nfo: root element is not <movie>")

type rawNode struct {
	XMLName xml.Name
	Attrs   []xml.Attr `xml:",any,attr"`
	Inner   string     `xml:",innerxml"`
}

type parsedDoc struct {
	XMLName    xml.Name
	Watched    string    `xml:"watched"`
	Playcount  int       `xml:"playcount"`
	LastPlayed string    `xml:"lastplayed"`
	DateAdded  string    `xml:"dateadded"`
	UserRating float64   `xml:"userrating"`
	Nodes      []rawNode `xml:",any"`
}

// knownElements are the tags Build manages; everything else is unknown and
// preserved verbatim. lockdata is deliberately dropped like in TMM.
var knownElements = map[string]bool{
	"title": true, "originaltitle": true, "sorttitle": true, "year": true,
	"ratings": true, "rating": true, "userrating": true,
	"set": true, "plot": true, "outline": true, "tagline": true,
	"runtime": true, "thumb": true, "fanart": true, "mpaa": true,
	"certification": true, "id": true, "tmdbid": true, "uniqueid": true,
	"country": true, "premiered": true, "watched": true, "playcount": true,
	"lastplayed": true, "genre": true, "studio": true, "credits": true,
	"director": true, "actor": true, "dateadded": true, "lockdata": true,
}

// Parse reads an existing movie NFO: the user data values and every unknown
// root-level element (with attributes and raw inner XML). Malformed XML is
// an error; callers treat it as "no existing data".
func Parse(data []byte) (UserData, []UnknownElement, error) {
	dec := xml.NewDecoder(bytes.NewReader(data))
	dec.Strict = false // tolerate small irregularities like TMM's JSoup
	var doc parsedDoc
	if err := dec.Decode(&doc); err != nil {
		if errors.Is(err, io.EOF) {
			return UserData{}, nil, ErrNotMovieNFO
		}
		return UserData{}, nil, fmt.Errorf("nfo: parse: %w", err)
	}
	if doc.XMLName.Local != "movie" {
		return UserData{}, nil, ErrNotMovieNFO
	}

	var ud UserData
	ud.Watched = doc.Watched == "true" || doc.Watched == "1"
	ud.Playcount = doc.Playcount
	ud.LastPlayed = strings.TrimSpace(doc.LastPlayed)
	ud.DateAdded = strings.TrimSpace(doc.DateAdded)
	ud.UserRating = doc.UserRating
	// TMM: playcount > 0 implies watched.
	if ud.Playcount > 0 {
		ud.Watched = true
	}

	var unknown []UnknownElement
	for _, n := range doc.Nodes {
		if knownElements[n.XMLName.Local] {
			continue
		}
		u := UnknownElement{Name: n.XMLName.Local, Inner: n.Inner}
		for _, a := range n.Attrs {
			u.Attrs = append(u.Attrs, Attr{Name: a.Name.Local, Value: a.Value})
		}
		unknown = append(unknown, u)
	}
	return ud, unknown, nil
}

// MergeUserData applies the Phase-0 max/newer rules on top of parsed user
// data: playcount max (implying watched), watched implies playcount >= 1.
func MergeUserData(existing UserData) UserData {
	ud := existing
	if ud.Playcount > 0 {
		ud.Watched = true
	}
	if ud.Watched && ud.Playcount == 0 {
		ud.Playcount = 1
	}
	return ud
}

// StripComments removes XML comments — the basis of the "unchanged modulo
// comments" skip rule.
func StripComments(s string) string {
	for {
		i := strings.Index(s, "<!--")
		if i < 0 {
			return s
		}
		j := strings.Index(s[i:], "-->")
		if j < 0 {
			return s[:i]
		}
		s = s[:i] + s[i+j+3:]
	}
}

// Unchanged reports whether the new content equals the existing file
// content modulo comments and line-ending style.
func Unchanged(existing, next string) bool {
	norm := func(s string) string {
		s = StripComments(s)
		return strings.ReplaceAll(strings.TrimSpace(s), "\r\n", "\n")
	}
	return norm(existing) == norm(next)
}
