package parser

import (
	"errors"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
)

// ParsedEpisode is the structured result of parsing a TV episode filename.
// Season 0 with Episodes set marks specials; a non-nil Absolute marks an
// anime-style absolute number (Season/Episodes are then not derived).
// Date-based daily shows encode Season = year and Episodes = [month*100+day]
// (see docs/decisions.md D19).
type ParsedEpisode struct {
	Season   int
	Episodes []int
	Absolute *int
	Title    string
}

// Detected reports whether an episode marker was found at all.
func (p ParsedEpisode) Detected() bool {
	return len(p.Episodes) > 0 || p.Absolute != nil
}

type episodeMatcher struct {
	re   *regexp.Regexp
	kind string // sxe | axx | words | episode | date | part | anime
	// extract receives the capture groups and the text following the match
	// (for chained markers) and returns the season, episodes, optional
	// absolute number and how many chars of rest the full chain consumed
	// (title extraction skips those).
	extract func(m []string, rest string) (season int, episodes []int, absolute *int, consumed int)
}

// contRe continues an SxxEyy match across stacked/range markers: an
// optional dash run, an optional E, then the number — anchored to the rest
// start so only adjacent markers chain ("E03", "-E03", "-03", " - E03").
var contRe = regexp.MustCompile(`(?i)^[\s\-]*E?(\d{1,3})`)

var episodeMatchers = []episodeMatcher{
	// S01E02 with chained S01E02E03 / S01E02-E03 / S01E02 - E03
	{regexp.MustCompile(`(?i)\bS(\d{1,3})\s*E(\d{1,3})`), "sxe",
		func(m []string, rest string) (int, []int, *int, int) {
			s, _ := strconv.Atoi(m[1])
			first, _ := strconv.Atoi(m[2])
			eps := []int{first}
			consumed := 0
			for {
				loc := contRe.FindStringSubmatchIndex(rest)
				if loc == nil {
					break
				}
				n, _ := strconv.Atoi(rest[loc[2]:loc[3]])
				last := eps[len(eps)-1]
				// Guard against technical tags: a chain member must be a
				// plausible next episode (increasing, within 50).
				if n <= last || n-last > 50 {
					break
				}
				if strings.ContainsRune(rest[loc[0]:loc[2]], '-') {
					// Dash form (S01E02-E05) expands the range.
					for e := last + 1; e <= n; e++ {
						eps = append(eps, e)
					}
				} else {
					eps = append(eps, n)
				}
				consumed += loc[1]
				rest = rest[loc[1]:]
			}
			return s, eps, nil, consumed
		}},
	// 1x02 (optionally ranged 1x02-03)
	{regexp.MustCompile(`(?i)\b(\d{1,3})x(\d{1,3})(?:\s*-\s*(\d{1,3}))?\b`), "axx",
		func(m []string, rest string) (int, []int, *int, int) {
			s, _ := strconv.Atoi(m[1])
			first, _ := strconv.Atoi(m[2])
			eps := []int{first}
			if m[3] != "" {
				last, _ := strconv.Atoi(m[3])
				if last > first && last-first <= 50 {
					for e := first + 1; e <= last; e++ {
						eps = append(eps, e)
					}
				}
			}
			return s, eps, nil, 0
		}},
	// Season 1 Episode 2 (several languages), S1 E2
	{regexp.MustCompile(`(?i)\b(?:season|serie|saison|staffel|temporada|s)\s*(\d{1,2})\b.*?\b(?:episode|episodio|ep|e)\s*(\d{1,3})\b`), "words",
		func(m []string, rest string) (int, []int, *int, int) {
			s, _ := strconv.Atoi(m[1])
			e, _ := strconv.Atoi(m[2])
			return s, []int{e}, nil, 0
		}},
	// Episode 5 / Episodio 12 (season unknown → 1)
	{regexp.MustCompile(`(?i)\bepisodes?\s*[-. ]?\s*(\d{1,3})\b`), "episode",
		func(m []string, rest string) (int, []int, *int, int) {
			e, _ := strconv.Atoi(m[1])
			return 1, []int{e}, nil, 0
		}},
	// yyyy-mm-dd / yyyy_mm_dd / yyyy mm dd (daily shows)
	{regexp.MustCompile(`\b(\d{4})[-. ](\d{2})[-. ](\d{2})\b`), "date",
		func(m []string, rest string) (int, []int, *int, int) {
			y, _ := strconv.Atoi(m[1])
			mo, _ := strconv.Atoi(m[2])
			d, _ := strconv.Atoi(m[3])
			if mo < 1 || mo > 12 || d < 1 || d > 31 {
				return 0, nil, nil, 0
			}
			return y, []int{mo*100 + d}, nil, 0
		}},
	// dd-mm-yyyy daily shows (ambiguous dates resolve as DD.MM, like TMM)
	{regexp.MustCompile(`\b(\d{2})[-. ](\d{2})[-. ](\d{4})\b`), "date2",
		func(m []string, rest string) (int, []int, *int, int) {
			d, _ := strconv.Atoi(m[1])
			mo, _ := strconv.Atoi(m[2])
			y, _ := strconv.Atoi(m[3])
			if mo < 1 || mo > 12 || d < 1 || d > 31 || y < 1900 {
				return 0, nil, nil, 0
			}
			return y, []int{mo*100 + d}, nil, 0
		}},
	// Season N Part/Disc IV|2 → episode in season 1
	{regexp.MustCompile(`(?i)\b(?:part|disc|disk)\s*(\d{1,3}|[ivx]+)\b`), "part",
		func(m []string, rest string) (int, []int, *int, int) {
			if n, err := strconv.Atoi(m[1]); err == nil && n > 0 {
				return 1, []int{n}, nil, 0
			}
			if n, err := romanToArabic(m[1]); err == nil {
				return 1, []int{n}, nil, 0
			}
			return 0, nil, nil, 0
		}},
	// Anime bracket numbering: "[Group][Show][13][1080p]"
	{regexp.MustCompile(`(?i)\[(\d{1,3})(?:v\d)?\]`), "anime_bracket",
		func(m []string, rest string) (int, []int, *int, int) {
			n, _ := strconv.Atoi(m[1])
			if n <= 0 {
				return 0, nil, nil, 0
			}
			abs := n
			return 1, []int{n}, &abs, 0
		}},
}

// romanToArabic converts roman numerals (used by "Part IV") up to 20.
func romanToArabic(s string) (int, error) {
	s = strings.ToUpper(s)
	values := map[byte]int{'I': 1, 'V': 5, 'X': 10}
	total, prev := 0, 0
	for i := len(s) - 1; i >= 0; i-- {
		v, ok := values[s[i]]
		if !ok {
			return 0, errNotRoman
		}
		if v < prev {
			total -= v
		} else {
			total += v
		}
		prev = v
	}
	if total <= 0 || total > 20 {
		return 0, errNotRoman
	}
	return total, nil
}

var errNotRoman = errors.New("not a roman numeral")

var (
	// bare "Episode 5" / "EP 05" / "E05" (season unknown → 1)
	episodeOnlyRe = regexp.MustCompile(`(?i)\bE(?:P)?\s*(\d{1,3})\b`)
	// anime styles: "[Group] Show - 01v2", "Show - 01", trailing volume
	animeRe = regexp.MustCompile(`(?:^|[\s\-_])(\d{2,3})(?:v\d)?\s*(?:\[[^\]]*\])?\s*$`)
	// leading group tag "[Group] " in anime releases
	groupTagRe = regexp.MustCompile(`^\[[^\]]*\]\s*`)
)

// ParseEpisode extracts season/episode information from a TV episode
// filename (basename including extension). It is a pure function and never
// returns an error: files without a recognizable marker yield
// Detected() == false.
func ParseEpisode(filename string) ParsedEpisode {
	base := strings.TrimSpace(strings.TrimSuffix(filename, filepath.Ext(filename)))
	norm := normalize(base) // reuse the movie parser's separator handling

	bestPos := -1
	var result ParsedEpisode
	for _, m := range episodeMatchers {
		loc := m.re.FindStringSubmatchIndex(norm)
		if loc == nil {
			continue
		}
		if bestPos >= 0 && loc[0] >= bestPos {
			continue // an earlier marker already won
		}
		groups := make([]string, 0, len(loc)/2)
		for i := 0; i < len(loc); i += 2 {
			if loc[i] < 0 {
				groups = append(groups, "")
			} else {
				groups = append(groups, norm[loc[i]:loc[i+1]])
			}
		}
		season, episodes, absolute, consumed := m.extract(groups, norm[loc[1]:])
		if len(episodes) == 0 && absolute == nil {
			continue
		}
		bestPos = loc[0]
		result = ParsedEpisode{Season: season, Episodes: episodes, Absolute: absolute}
		result.Title = episodeTitleAfter(norm, loc[1]+consumed)
	}

	// Bare "Episode 5" / "E05": only when nothing stronger matched.
	if !result.Detected() {
		if loc := episodeOnlyRe.FindStringSubmatchIndex(norm); loc != nil {
			e, _ := strconv.Atoi(norm[loc[2]:loc[3]])
			result = ParsedEpisode{Season: 1, Episodes: []int{e}}
			result.Title = episodeTitleAfter(norm, loc[1])
		}
	}

	// Anime absolute numbering: trailing 2-3 digit number after " - "
	// (with or without a leading [Group] tag), only when no SxxEyy marker
	// was found.
	if !result.Detected() {
		if loc := animeRe.FindStringSubmatchIndex(norm); loc != nil {
			n, err := strconv.Atoi(norm[loc[2]:loc[3]])
			if err == nil && n > 0 {
				absolute := n
				result = ParsedEpisode{Absolute: &absolute, Season: 1, Episodes: []int{n}}
				result.Title = ""
			}
		}
	}

	// Clean group tags off the title.
	if result.Title != "" {
		result.Title = strings.TrimSpace(groupTagRe.ReplaceAllString(result.Title, ""))
	}
	return result
}

// episodeTitleAfter extracts and cleans the title text following a marker:
// trailing technical tags are cut, separators normalized, edges trimmed.
func episodeTitleAfter(norm string, from int) string {
	title := strings.TrimSpace(norm[min_int(from, len(norm)):])
	if title == "" {
		return ""
	}
	if idx := TechTagStart(title); idx >= 0 {
		title = strings.TrimSpace(title[:idx])
	}
	title = strings.Trim(title, " -_[](){}")
	return strings.Join(strings.Fields(title), " ")
}

// min_int is a tiny local min for ints (kept name-distinct from the stdlib).
func min_int(a, b int) int {
	if a < b {
		return a
	}
	return b
}

// EpisodeShowTitle derives the show title for a file inside a TV library:
// the first path component relative to the library root (the show folder),
// cleaned of years and separators. Files directly in the root fall back to
// the episode title prefix (the text before the SxxEyy marker).
func EpisodeShowTitle(root, path string, filename string) string {
	rel, err := filepath.Rel(root, filepath.Dir(path))
	if err == nil && rel != "." && rel != "" && !strings.HasPrefix(rel, "..") {
		first := rel
		if i := strings.IndexAny(rel, "/\\"); i >= 0 {
			first = rel[:i]
		}
		first = cleanShowName(first)
		// A season container ("Season 01", "Specials") is not a show name —
		// fall through to the filename prefix.
		if first != "" && !isSeasonFolder(first) {
			return first
		}
	}
	// Flat layout: the show name is the text before the episode marker.
	base := normalize(strings.TrimSuffix(filename, filepath.Ext(filename)))
	loc := episodeMatchers[0].re.FindStringIndex(base)
	if loc == nil {
		loc = episodeMatchers[1].re.FindStringIndex(base)
	}
	if loc != nil {
		return cleanShowName(base[:loc[0]])
	}
	return cleanShowName(base)
}

// isSeasonFolder reports whether a folder name is a season container
// ("Season 01", "S01", "Specials", "Staffel 1", ...).
func isSeasonFolder(name string) bool {
	n := strings.ToLower(strings.TrimSpace(name))
	if strings.Contains(n, "special") || strings.Contains(n, "extra") {
		return true
	}
	return seasonFolderRe.MatchString(n)
}

var seasonFolderRe = regexp.MustCompile(`^(s|season|saison|staffel|temporada|serie)\s*\d{1,2}$`)

// cleanShowName strips a trailing year and separator noise from a show
// folder name.
func cleanShowName(name string) string {
	name = normalize(name)
	name = yearSuffixRe.ReplaceAllString(name, "")
	name = strings.Trim(name, " -_.")
	return strings.Join(strings.Fields(name), " ")
}

var yearSuffixRe = regexp.MustCompile(`\s*\((?:19\d{2}|20[0-2]\d)\)\s*$|\s+(?:19\d{2}|20[0-2]\d)\s*$`)
