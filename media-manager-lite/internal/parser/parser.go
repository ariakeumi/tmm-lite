// Package parser implements pure movie-release filename parsing.
//
// Parse extracts title, year and technical tags (resolution, source, codec,
// HDR, audio, part markers) from a filename. It is a pure function with no
// dependency on the database, HTTP, settings or global state, per the
// Milestone-2 architecture (Scanner -> Parser -> Repository).
//
// Scope (deliberately basic, see docs/decisions.md D12):
//   - dots, underscores and spaces act as separators; hyphens are preserved
//     inside the title ("Spider-Man.Homecoming" keeps its hyphen), so pure
//     hyphen-separated release names ("Movie-2019-1080p") are not supported
//   - the year is the last standalone 1900-2029 token before the first
//     technical tag; a token like 2049 (out of range) stays in the title
//   - if taking the year would leave an empty title, the token is the title
//     and the year is unknown ("1917.1080p.BluRay" -> title "1917")
package parser

import (
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

// Parsed is the structured result of parsing a movie filename.
type Parsed struct {
	Title      string   `json:"title"`
	Year       int      `json:"year,omitempty"`
	Resolution string   `json:"resolution,omitempty"` // 2160p, 1080p, 720p, ...
	Source     string   `json:"source,omitempty"`     // BluRay, WEB-DL, HDTV, ...
	VideoCodec string   `json:"videoCodec,omitempty"` // x264, HEVC, AV1, ...
	HDR        string   `json:"hdr,omitempty"`        // HDR10, HDR10+, Dolby Vision, HDR
	Audio      []string `json:"audio,omitempty"`      // DTS-HD MA, TrueHD, Atmos, ...
	Part       string   `json:"part,omitempty"`       // CD1, DISC2, PART1, ...
}

// videoExtensions is the set of file extensions treated as video media.
var videoExtensions = map[string]struct{}{
	".3gp": {}, ".amv": {}, ".asf": {}, ".avi": {}, ".divx": {}, ".f4v": {},
	".flv": {}, ".iso": {}, ".m2t": {}, ".m2ts": {}, ".m2v": {}, ".m4v": {},
	".mkv": {}, ".mov": {}, ".mp4": {}, ".mpe": {}, ".mpeg": {}, ".mpg": {},
	".mts": {}, ".mxf": {}, ".ogm": {}, ".ogv": {}, ".qt": {}, ".rm": {},
	".rmvb": {}, ".tp": {}, ".trp": {}, ".ts": {}, ".vob": {}, ".webm": {},
	".wmv": {}, ".wtv": {},
}

// IsVideoFile reports whether the filename has a known video extension.
func IsVideoFile(filename string) bool {
	_, ok := videoExtensions[strings.ToLower(filepath.Ext(filename))]
	return ok
}

var (
	// "sample.mkv", "Sample.Movie.mkv": sample files lead with the marker.
	samplePrefix = regexp.MustCompile(`(?i)^sample([._\- ]|$)`)
	// "Movie.sample.mkv", "Movie.trailer.mkv": markers trail the title.
	trailerSampleSuffix = regexp.MustCompile(`(?i)[._\- ](sample|trailer)$`)
	// Exact markers. Note: a *prefix* "trailer." is deliberately NOT skipped
	// so titles like "Trailer.Park.Boys.2001" survive.
	exactSkipNames = map[string]struct{}{
		"sample": {}, "trailer": {}, "sample-trailer": {}, "trailer-sample": {},
	}
)

// IsTrailerOrSample reports whether the filename follows the release-scene
// naming convention for samples and trailers (which are never library
// movies).
func IsTrailerOrSample(filename string) bool {
	base := strings.ToLower(strings.TrimSuffix(filename, filepath.Ext(filename)))
	if _, ok := exactSkipNames[base]; ok {
		return true
	}
	return samplePrefix.MatchString(base) || trailerSampleSuffix.MatchString(base)
}

// metaPair maps a regex variant to its canonical value.
type metaPair struct {
	re    *regexp.Regexp
	canon string
}

func mustPair(pattern, canon string) metaPair {
	return metaPair{re: regexp.MustCompile(pattern), canon: canon}
}

var (
	resolutionPairs = []metaPair{
		mustPair(`(?i)\b4k\b`, "2160p"),
		mustPair(`(?i)\b8k\b`, "4320p"),
		mustPair(`(?i)\b4320p\b`, "4320p"),
		mustPair(`(?i)\b2160p\b`, "2160p"),
		mustPair(`(?i)\b1080p\b`, "1080p"),
		mustPair(`(?i)\b1080i\b`, "1080i"),
		mustPair(`(?i)\b720p\b`, "720p"),
		mustPair(`(?i)\b576p\b`, "576p"),
		mustPair(`(?i)\b480p\b`, "480p"),
	}
	sourcePairs = []metaPair{
		mustPair(`(?i)\bblu[ -]?ray\b`, "BluRay"),
		mustPair(`(?i)\bremux\b`, "Remux"),
		mustPair(`(?i)\bbd[ -]?rip\b`, "BDRip"),
		mustPair(`(?i)\bbrrip\b`, "BRRip"),
		mustPair(`(?i)\bweb[ -]?dl\b`, "WEB-DL"),
		mustPair(`(?i)\bweb[ -]?rip\b`, "WEBRip"),
		mustPair(`(?i)\bhdtv\b`, "HDTV"),
		mustPair(`(?i)\bdvd[ -]?rip\b`, "DVDRip"),
		mustPair(`(?i)\bdvd\b`, "DVD"),
		mustPair(`(?i)\bhdrip\b`, "HDRip"),
	}
	codecPairs = []metaPair{
		mustPair(`(?i)\bx265\b`, "x265"),
		mustPair(`(?i)\bx264\b`, "x264"),
		mustPair(`(?i)\bhevc\b`, "HEVC"),
		mustPair(`(?i)\bh[ .]?265\b`, "HEVC"),
		mustPair(`(?i)\bh[ .]?264\b`, "H.264"),
		mustPair(`(?i)\bavc\b`, "H.264"),
		mustPair(`(?i)\bav1\b`, "AV1"),
		mustPair(`(?i)\bvp9\b`, "VP9"),
		mustPair(`(?i)\bxvid\b`, "XviD"),
		mustPair(`(?i)\bdivx\b`, "DivX"),
		mustPair(`(?i)\bmpeg[ .]?2\b`, "MPEG2"),
		mustPair(`(?i)\bmvc\b`, "MVC"),
	}
	hdrPairs = []metaPair{
		mustPair(`(?i)\bhdr10\+`, "HDR10+"),
		mustPair(`(?i)\bhdr10plus\b`, "HDR10+"),
		mustPair(`(?i)\bdolby[ -]?vision\b`, "Dolby Vision"),
		mustPair(`(?i)\bdovi\b`, "Dolby Vision"),
		mustPair(`(?i)\bdv\b`, "Dolby Vision"),
		mustPair(`(?i)\bhdr10\b`, "HDR10"),
		mustPair(`(?i)\bhdr\b`, "HDR"),
	}
	audioPairs = []metaPair{
		mustPair(`(?i)\btruehd\b`, "TrueHD"),
		mustPair(`(?i)\batmos\b`, "Atmos"),
		mustPair(`(?i)\bdts[ -]?hd[ -]?ma\b`, "DTS-HD MA"),
		mustPair(`(?i)\bdtshdma\b`, "DTS-HD MA"),
		mustPair(`(?i)\bdts[ -]?x\b`, "DTS-X"),
		mustPair(`(?i)\bdts\b`, "DTS"),
		mustPair(`(?i)\bddp(?:[ .]?\d)?\b`, "DD+"),
		mustPair(`\bdd\+`, "DD+"),
		mustPair(`(?i)\be[ .-]?ac[ .-]?3\b`, "EAC3"),
		mustPair(`(?i)\bac[ .-]?3\b`, "AC3"),
		mustPair(`(?i)\bdolby[ -]?digital[ -]?plus\b`, "DD+"),
		mustPair(`(?i)\bdolby[ -]?digital\b`, "DD"),
		mustPair(`(?i)\baac[ .]?[257][ .]?[01]\b`, "AAC"),
		mustPair(`(?i)\baac\b`, "AAC"),
		mustPair(`(?i)\bflac\b`, "FLAC"),
		mustPair(`(?i)\blpcm\b`, "LPCM"),
		mustPair(`(?i)\bpcm\b`, "PCM"),
		mustPair(`(?i)\bopus\b`, "Opus"),
		mustPair(`(?i)\bmp3\b`, "MP3"),
	}
	partPairs = []struct {
		re    *regexp.Regexp
		label string
	}{
		{regexp.MustCompile(`(?i)\bcd[ .]?(\d{1,2})\b`), "CD"},
		{regexp.MustCompile(`(?i)\bdis[ck][ .]?(\d{1,2})\b`), "DISC"},
		{regexp.MustCompile(`(?i)\bpart[ .]?(\d{1,2})\b`), "PART"},
	}
)

// firstMatch returns the canonical value of the earliest matching pair.
// Ties at the same position resolve to the pair listed first.
func firstMatch(pairs []metaPair, s string) (string, int, bool) {
	bestIdx, bestCanon := -1, ""
	for _, p := range pairs {
		if loc := p.re.FindStringIndex(s); loc != nil {
			if bestIdx == -1 || loc[0] < bestIdx {
				bestIdx, bestCanon = loc[0], p.canon
			}
		}
	}
	return bestCanon, bestIdx, bestIdx >= 0
}

// audioHits returns de-duplicated audio codecs ordered by position.
// Overlapping matches are suppressed (e.g. "EAC3" also contains "AC3").
func audioHits(s string) []string {
	type hit struct {
		start, end, order int
		canon             string
	}
	var hits []hit
	for order, p := range audioPairs {
		for _, loc := range p.re.FindAllStringIndex(s, -1) {
			hits = append(hits, hit{start: loc[0], end: loc[1], order: order, canon: p.canon})
		}
	}
	sort.Slice(hits, func(i, j int) bool {
		if hits[i].start != hits[j].start {
			return hits[i].start < hits[j].start
		}
		return hits[i].order < hits[j].order
	})

	var out []string
	seen := map[string]struct{}{}
	lastEnd := -1
	for _, h := range hits {
		if h.start < lastEnd {
			continue // inside a previous (more specific) match
		}
		lastEnd = h.end
		if _, dup := seen[h.canon]; dup {
			continue
		}
		seen[h.canon] = struct{}{}
		out = append(out, h.canon)
	}
	return out
}

// firstPart returns the earliest CD/DISC/PART marker.
func firstPart(s string) (string, int, bool) {
	bestIdx, bestCanon := -1, ""
	for _, p := range partPairs {
		if loc := p.re.FindStringSubmatchIndex(s); loc != nil {
			if bestIdx == -1 || loc[0] < bestIdx {
				n, _ := strconv.Atoi(s[loc[2]:loc[3]])
				bestIdx, bestCanon = loc[0], p.label+strconv.Itoa(n)
			}
		}
	}
	return bestCanon, bestIdx, bestIdx >= 0
}

// TechTagStart returns the index of the first technical tag (resolution,
// source, codec, HDR, audio, part marker) in a normalized string, or -1.
// Exported for the episode title cleanup.
func TechTagStart(s string) int {
	best := -1
	for _, pairs := range [][]metaPair{resolutionPairs, sourcePairs, codecPairs, hdrPairs, audioPairs} {
		for _, p := range pairs {
			if loc := p.re.FindStringIndex(s); loc != nil {
				if best == -1 || loc[0] < best {
					best = loc[0]
				}
			}
		}
	}
	for _, p := range partPairs {
		if loc := p.re.FindStringIndex(s); loc != nil {
			if best == -1 || loc[0] < best {
				best = loc[0]
			}
		}
	}
	return best
}

// normalize maps filename separators to spaces (hyphens are preserved).
func normalize(s string) string {
	return strings.TrimSpace(strings.NewReplacer(".", " ", "_", " ").Replace(s))
}

type token struct {
	text       string
	start, end int
}

func tokens(s string) []token {
	var out []token
	for i := 0; i < len(s); {
		for i < len(s) && s[i] == ' ' {
			i++
		}
		j := i
		for j < len(s) && s[j] != ' ' {
			j++
		}
		if j > i {
			out = append(out, token{text: s[i:j], start: i, end: j})
		}
		i = j
	}
	return out
}

// isYearToken reports whether the token (after punctuation stripping) is a
// plausible standalone release year.
func isYearToken(t string) (int, bool) {
	c := strings.Trim(t, "()[]{}<>,;:!?\"'")
	if len(c) != 4 {
		return 0, false
	}
	for i := 0; i < len(c); i++ {
		if c[i] < '0' || c[i] > '9' {
			return 0, false
		}
	}
	y, err := strconv.Atoi(c)
	if err != nil || y < 1900 || y > 2029 {
		return 0, false
	}
	return y, true
}

func cleanTitle(s string) string {
	s = strings.Join(strings.Fields(s), " ")
	return strings.Trim(s, " -_.,;:!?()[]{}<>\"'")
}

// Parse extracts structured information from a movie release filename
// (basename including extension). It never returns an empty Title.
func Parse(filename string) Parsed {
	base := strings.TrimSpace(strings.TrimSuffix(filename, filepath.Ext(filename)))
	norm := normalize(base)

	var p Parsed
	if norm == "" {
		p.Title = base
		return p
	}

	// The title ends at the first technical tag.
	boundary := len(norm)
	if v, idx, ok := firstMatch(resolutionPairs, norm); ok && idx < boundary {
		p.Resolution, boundary = v, idx
	}
	if _, idx, ok := firstMatch(sourcePairs, norm); ok && idx < boundary {
		boundary = idx
	}
	if _, idx, ok := firstMatch(codecPairs, norm); ok && idx < boundary {
		boundary = idx
	}
	if _, idx, ok := firstMatch(hdrPairs, norm); ok && idx < boundary {
		boundary = idx
	}
	if v, idx, ok := firstPart(norm); ok {
		p.Part = v
		if idx < boundary {
			boundary = idx
		}
	}

	// Year: the last standalone year token before the boundary.
	year, yearIdx, yearText := 0, -1, ""
	for _, tk := range tokens(norm) {
		if tk.start >= boundary {
			break
		}
		if y, ok := isYearToken(tk.text); ok {
			year, yearIdx, yearText = y, tk.start, strconv.Itoa(y)
		}
	}

	titleEnd := boundary
	if yearIdx >= 0 {
		titleEnd = yearIdx
	}
	title := cleanTitle(norm[:titleEnd])
	if title == "" {
		if yearIdx >= 0 {
			// The year token is all there is: it is the title, year unknown.
			title, year = yearText, 0
		} else {
			title = cleanTitle(norm)
		}
	}
	if title == "" {
		title = base
	}

	p.Title = title
	p.Year = year
	if v, _, ok := firstMatch(sourcePairs, norm); ok {
		p.Source = v
	}
	if v, _, ok := firstMatch(codecPairs, norm); ok {
		p.VideoCodec = v
	}
	if v, _, ok := firstMatch(hdrPairs, norm); ok {
		p.HDR = v
	}
	p.Audio = audioHits(norm)
	return p
}
