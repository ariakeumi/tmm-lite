package nfo

import (
	"bytes"
	"encoding/xml"
	"fmt"
	"io"
	"strconv"
	"strings"
)

// TVShowData is the input for BuildTVShow (root <tvshow>).
type TVShowData struct {
	Title         string
	OriginalTitle string
	Year          int
	Rating        float64
	Votes         int
	Plot          string
	Status        string // aired status from TMDB
	Certification string
	CertCountry   string
	TMDBID        int
	IMDBID        string
	Premiered     string // first air date yyyy-MM-dd
	Genres        []string
	Studios       []string

	UserData
	Unknown []UnknownElement
}

// SeasonData is the input for BuildSeason (root <season>).
type SeasonData struct {
	SeasonNumber int
	Title        string
	ShowTitle    string
	ShowYear     int
	TMDBID       int
	IMDBID       string

	UserData
	Unknown []UnknownElement
}

// EpisodeData is the input for BuildEpisodes. Episodes with more than one
// number produce multiple <episodedetails> roots (multi-episode files).
type EpisodeData struct {
	Title         string
	ShowTitle     string
	Season        int
	Episodes      []int
	AirDate       string // yyyy-MM-dd
	Runtime       int
	Rating        float64
	Votes         int
	Plot          string
	EpisodeTMDBID int // episode TMDB id
	ShowTMDBID    int
	ShowIMDBID    string
	ShowStatus    string

	UserData
	Unknown []UnknownElement
}

// BuildTVShow renders a complete <tvshow> NFO document.
func BuildTVShow(d TVShowData, f Flavor) string {
	w := &builder{}
	w.b.WriteString(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` + "\r\n")
	fmt.Fprintf(&w.b, "<!-- created on %s by Media Manager Lite for %s -->\r\n",
		buildStamp, strings.ToUpper(string(f)))
	w.begin("tvshow")

	w.elem("title", d.Title)
	w.elemNotEmpty("originaltitle", d.OriginalTitle)
	w.elemNotEmpty("showtitle", d.Title)
	if d.Year > 0 {
		w.elemInt("year", d.Year)
	} else {
		w.indent()
		w.b.WriteString("<year />")
		w.nl()
	}
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
	w.elem("plot", d.Plot)
	w.elemNotEmpty("status", d.Status)
	w.elemNotEmpty("mpaa", d.Certification)
	if d.Certification != "" && d.CertCountry != "" {
		w.elem("certification", d.CertCountry+":"+d.Certification)
	}
	// TMM writes <id> as the TVDB id for shows; Lite has no TVDB, so the
	// element is written empty like the movie variant does for IMDb.
	w.elem("id", "")
	if d.TMDBID > 0 {
		w.elemInt("tmdbid", d.TMDBID)
	}
	if d.IMDBID != "" {
		w.elem("imdbid", d.IMDBID)
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
	if f == Emby {
		w.elem("lockdata", "true")
	}
	w.elemNotEmpty("dateadded", d.DateAdded)
	for _, u := range d.Unknown {
		w.raw(u)
	}
	w.end("tvshow")
	return w.b.String()
}

// BuildSeason renders a <season> NFO document (single flavor, like TMM).
func BuildSeason(d SeasonData, f Flavor) string {
	w := &builder{}
	w.b.WriteString(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` + "\r\n")
	fmt.Fprintf(&w.b, "<!-- created on %s by Media Manager Lite for %s -->\r\n",
		buildStamp, strings.ToUpper(string(f)))
	w.begin("season")

	w.elemInt("seasonnumber", d.SeasonNumber)
	w.elemNotEmpty("title", d.Title)
	w.elemNotEmpty("showtitle", d.ShowTitle)
	if d.ShowYear > 0 {
		w.elemInt("year", d.ShowYear)
	}
	if d.TMDBID > 0 {
		w.elemInt("tmdbid", d.TMDBID)
	}
	if d.IMDBID != "" {
		w.elem("imdbid", d.IMDBID)
	}
	if f == Emby {
		w.elem("lockdata", "true")
	}
	w.elemNotEmpty("dateadded", d.DateAdded)
	for _, u := range d.Unknown {
		w.raw(u)
	}
	w.end("season")
	return w.b.String()
}

// BuildEpisodes renders one or several <episodedetails> roots. The XML
// declaration and comment appear only on the first block; later roots are
// concatenated verbatim (multi-episode files).
func BuildEpisodes(d EpisodeData, f Flavor) string {
	if len(d.Episodes) == 0 {
		d.Episodes = []int{0}
	}
	var out strings.Builder
	for i, ep := range d.Episodes {
		block := buildOneEpisode(d, ep, f, i == 0)
		out.WriteString(block)
	}
	return out.String()
}

func buildOneEpisode(d EpisodeData, ep int, f Flavor, first bool) string {
	w := &builder{}
	if first {
		w.b.WriteString(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>` + "\r\n")
		fmt.Fprintf(&w.b, "<!-- created on %s by Media Manager Lite for %s -->\r\n",
			buildStamp, strings.ToUpper(string(f)))
	}
	w.begin("episodedetails")

	w.elem("title", d.Title)
	w.elemNotEmpty("showtitle", d.ShowTitle)
	w.elemInt("season", d.Season)
	w.elemInt("episode", ep)
	if d.EpisodeTMDBID > 0 {
		w.elemInt("id", d.EpisodeTMDBID)
		w.indent()
		w.b.WriteString(`<uniqueid type="tmdb" default="true">` + strconv.Itoa(d.EpisodeTMDBID) + "</uniqueid>")
		w.nl()
	}
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
	w.elem("plot", d.Plot)
	if d.Runtime > 0 {
		w.elemInt("runtime", d.Runtime)
	}
	w.elemNotEmpty("aired", d.AirDate)
	w.elemNotEmpty("premiered", d.AirDate)
	if d.Watched {
		w.elem("watched", "true")
	} else {
		w.elem("watched", "false")
	}
	w.elemInt("playcount", d.Playcount)
	w.elemNotEmpty("lastplayed", d.LastPlayed)
	// Emby multi-episode marker (issue #2780 family).
	if f == Emby && len(d.Episodes) > 1 {
		w.elemInt("episodenumberend", d.Episodes[len(d.Episodes)-1])
	}
	if f == Emby {
		w.elem("lockdata", "true")
	}
	w.elemNotEmpty("dateadded", d.DateAdded)
	for _, u := range d.Unknown {
		w.raw(u)
	}
	w.end("episodedetails")
	return w.b.String()
}

// --- TV round-trip parsing ---

// TVUserData aggregates user data across one or more root blocks with
// additive/first-wins semantics (multi-episode files carry one
// <episodedetails> per episode).
type TVUserData struct {
	Root    string
	Blocks  []UserData
	Unknown []UnknownElement // unknowns of the first root only
}

// Aggregate folds all blocks: watched = any, playcount summed,
// dateadded/lastplayed = first non-empty, userrating = max.
func (t TVUserData) Aggregate() UserData {
	var out UserData
	for _, b := range t.Blocks {
		if b.Watched {
			out.Watched = true
		}
		out.Playcount += b.Playcount
		if out.DateAdded == "" {
			out.DateAdded = b.DateAdded
		}
		if out.LastPlayed == "" {
			out.LastPlayed = b.LastPlayed
		}
		if b.UserRating > out.UserRating {
			out.UserRating = b.UserRating
		}
	}
	if out.Playcount > 0 {
		out.Watched = true
	}
	return out
}

// tvTopLevel is one top-level element of a TV NFO document.
type tvTopLevel struct {
	name       string
	watched    string
	playcount  int
	lastplayed string
	dateadded  string
	userrating float64
	unknown    []UnknownElement
}

var allowedTVRoots = map[string]bool{"tvshow": true, "season": true, "episodedetails": true}

// ParseTV reads a TV NFO document: a <tvshow>, <season> or
// <episodedetails> root (the last may repeat for multi-episode files).
// Unknown root-level children of the first block are preserved verbatim
// (re-encoded from the token stream, matching the movie parser's contract).
func ParseTV(data []byte) (TVUserData, error) {
	dec := xml.NewDecoder(bytes.NewReader(data))
	dec.Strict = false

	var out TVUserData
	first := true
	for {
		tok, err := dec.Token()
		if err == io.EOF {
			break
		}
		if err != nil {
			return TVUserData{}, fmt.Errorf("nfo: parse: %w", err)
		}
		se, ok := tok.(xml.StartElement)
		if !ok {
			continue
		}
		if ProcInst(se) {
			continue
		}
		if !allowedTVRoots[se.Name.Local] {
			// skip the whole foreign root
			if err := skipElement(dec, se); err != nil {
				return TVUserData{}, fmt.Errorf("nfo: parse: %w", err)
			}
			continue
		}
		tl, err := decodeTopLevel(dec, se)
		if err != nil {
			return TVUserData{}, fmt.Errorf("nfo: parse %s: %w", se.Name.Local, err)
		}
		ud := UserData{
			Watched:    tl.watched == "true" || tl.watched == "1",
			Playcount:  tl.playcount,
			LastPlayed: strings.TrimSpace(tl.lastplayed),
			DateAdded:  strings.TrimSpace(tl.dateadded),
			UserRating: tl.userrating,
		}
		if ud.Playcount > 0 {
			ud.Watched = true
		}
		out.Root = se.Name.Local
		out.Blocks = append(out.Blocks, ud)
		if first {
			out.Unknown = tl.unknown
			first = false
		}
	}
	if out.Root == "" {
		return TVUserData{}, ErrNotMovieNFO
	}
	return out, nil
}

// ProcInst reports whether a start token is actually a processing
// instruction target (never true; the function documents intent).
func ProcInst(se xml.StartElement) bool { return false }

// decodeTopLevel consumes the children of se, capturing known leaf values
// and re-encoding unknown children verbatim.
func decodeTopLevel(dec *xml.Decoder, root xml.StartElement) (tvTopLevel, error) {
	var tl tvTopLevel
	tl.name = root.Name.Local
	depth := 1
	var unknownName string
	var unknownAttrs []Attr
	var unknownBuf strings.Builder
	var unknownDepth int
	var leafName string
	var leafBuf strings.Builder

	flushLeaf := func() {
		if leafName != "" {
			switch leafName {
			case "watched":
				tl.watched = strings.TrimSpace(leafBuf.String())
			case "playcount":
				tl.playcount, _ = strconv.Atoi(strings.TrimSpace(leafBuf.String()))
			case "lastplayed":
				tl.lastplayed = leafBuf.String()
			case "dateadded":
				tl.dateadded = leafBuf.String()
			case "userrating":
				tl.userrating, _ = strconv.ParseFloat(strings.TrimSpace(leafBuf.String()), 64)
			}
			leafName = ""
			leafBuf.Reset()
		}
	}

	for depth > 0 {
		tok, err := dec.Token()
		if err != nil {
			return tl, err
		}
		switch t := tok.(type) {
		case xml.StartElement:
			depth++
			if depth == 2 {
				if !knownElements[t.Name.Local] {
					// Start capturing an unknown child verbatim.
					unknownName = t.Name.Local
					for _, a := range t.Attr {
						unknownAttrs = append(unknownAttrs, Attr{Name: a.Name.Local, Value: a.Value})
					}
					unknownBuf.Reset()
					unknownDepth = depth
					// Self-closing?
					// (Go reports self-closing via an immediate EndElement.)
					continue
				}
				leafName = t.Name.Local
				leafBuf.Reset()
			} else if unknownName != "" {
				// Nested inside an unknown child: re-encode.
				unknownBuf.WriteString("<" + t.Name.Local)
				for _, a := range t.Attr {
					unknownBuf.WriteString(` ` + a.Name.Local + `="` + escapeAttr(a.Value) + `"`)
				}
				unknownBuf.WriteString(">")
			} else if leafName != "" {
				// Nested element inside a known leaf: ignore (TMM keeps raw).
				leafName = ""
				leafBuf.Reset()
			}
		case xml.CharData:
			if unknownName != "" {
				unknownBuf.WriteString(escapeText(string(t)))
			} else if leafName != "" {
				leafBuf.Write(t)
			}
		case xml.EndElement:
			depth--
			if unknownName != "" && depth < unknownDepth {
				inner := strings.TrimSpace(unknownBuf.String())
				tl.unknown = append(tl.unknown, UnknownElement{
					Name: unknownName, Attrs: unknownAttrs, Inner: inner,
				})
				unknownName, unknownAttrs = "", nil
			} else if unknownName != "" {
				unknownBuf.WriteString("</" + t.Name.Local + ">")
			} else if depth == 1 && leafName == t.Name.Local {
				flushLeaf()
			} else if depth == 1 {
				// end of root
			}
		}
	}
	flushLeaf()
	return tl, nil
}

// skipElement consumes until the matching end element of start.
func skipElement(dec *xml.Decoder, start xml.StartElement) error {
	depth := 1
	for depth > 0 {
		tok, err := dec.Token()
		if err != nil {
			return err
		}
		switch tok.(type) {
		case xml.StartElement:
			depth++
		case xml.EndElement:
			depth--
		}
	}
	return nil
}
