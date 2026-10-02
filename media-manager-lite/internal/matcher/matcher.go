// Package matcher scores local parsed filenames against TMDB search
// candidates, replicating tinyMediaManager's scoring core (Phase 0 §3.2):
//
//	score = max(calculateScore(query, title), calculateScore(query, originalTitle))
//	        - yearPenalty - posterPenalty
//
// with calculateScore = max(StrikeAMatch, StrikeAMatch on cleaned title,
// StrikeAMatch with trailing year stripped, Jaro-Winkler). A direct TMDB id
// hit short-circuits to 1.0. The package is pure: no I/O of any kind.
package matcher

import (
	"regexp"
	"sort"
	"strings"
)

// Local is the local side of a match: what the parser extracted from the
// filename plus any known external ids.
type Local struct {
	Title  string
	Year   int
	TMDBID int // usually 0 for filename-only sources
}

// Candidate is one TMDB search result to score.
type Candidate struct {
	TMDBID        int    `json:"tmdbId"`
	Title         string `json:"title"`
	OriginalTitle string `json:"originalTitle,omitempty"`
	Year          int    `json:"year,omitempty"`
	Overview      string `json:"overview,omitempty"`
	PosterPath    string `json:"posterPath,omitempty"`
	ReleaseDate   string `json:"releaseDate,omitempty"`
}

// MatchResult pairs a candidate with its score.
type MatchResult struct {
	Score     float64   `json:"score"`
	IDMatch   bool      `json:"idMatch"`
	Candidate Candidate `json:"candidate"`
}

// --- string similarity primitives ---

// letterPairs returns the multiset of adjacent character pairs of a string
// (runes, lowercased), the core of the StrikeAMatch algorithm. Unlike the
// classic ASCII-only variant this keeps CJK characters, so Chinese titles
// produce meaningful bigrams.
func letterPairs(s string) []string {
	runes := []rune(strings.ToLower(strings.Join(strings.Fields(s), " ")))
	if len(runes) < 2 {
		return nil
	}
	pairs := make([]string, 0, len(runes)-1)
	for i := 0; i+1 < len(runes); i++ {
		pairs = append(pairs, string(runes[i:i+2]))
	}
	return pairs
}

// StrikeAMatch compares two strings via their letter-pair similarity
// (Dice coefficient): 2*|intersection| / (|a|+|b|). Returns 1.0 for equal
// (non-empty) inputs.
func StrikeAMatch(a, b string) float64 {
	pa, pb := letterPairs(a), letterPairs(b)
	if len(pa) == 0 || len(pb) == 0 {
		if strings.TrimSpace(strings.ToLower(a)) == strings.TrimSpace(strings.ToLower(b)) && a != "" {
			return 1.0
		}
		return 0.0
	}

	used := make([]bool, len(pb))
	hits := 0
	for _, p := range pa {
		for j, q := range pb {
			if !used[j] && p == q {
				used[j] = true
				hits++
				break
			}
		}
	}
	return 2.0 * float64(hits) / float64(len(pa)+len(pb))
}

var nonSearchChars = regexp.MustCompile(`[\[\]_.:|]`)
var trailingYear = regexp.MustCompile(`\s*(?:,\s*)?\b(19\d{2}|20[0-2]\d)\s*$`)

// calculateScore replicates tinyMediaManager's MetadataUtil.calculateScore:
// the max of StrikeAMatch against the raw and separator-cleaned titles, a
// StrikeAMatch with a trailing year stripped from the query, and
// Jaro-Winkler.
func calculateScore(query, matchTitle string) float64 {
	if query == "" || matchTitle == "" {
		return 0
	}
	cleaned := nonSearchChars.ReplaceAllString(matchTitle, " ")
	stripped := trailingYear.ReplaceAllString(query, "")
	return maxOf(
		StrikeAMatch(query, matchTitle),
		StrikeAMatch(query, cleaned),
		StrikeAMatch(stripped, matchTitle),
		JaroWinkler(query, matchTitle),
	)
}

func maxOf(values ...float64) float64 {
	m := values[0]
	for _, v := range values[1:] {
		if v > m {
			m = v
		}
	}
	return m
}

// JaroWinkler returns the Jaro-Winkler similarity of two strings
// (prefix scale 0.1, max prefix 4, boost threshold 0.7), rune-based.
func JaroWinkler(a, b string) float64 {
	sa, sb := []rune(a), []rune(b)
	if len(sa) == 0 || len(sb) == 0 {
		if string(sa) == string(sb) && len(sa) > 0 {
			return 1.0
		}
		return 0.0
	}

	window := max(len(sa), len(sb))/2 - 1
	if window < 0 {
		window = 0
	}

	aMatch := make([]bool, len(sa))
	bMatch := make([]bool, len(sb))
	matches := 0
	for i := 0; i < len(sa); i++ {
		lo := i - window
		if lo < 0 {
			lo = 0
		}
		hi := i + window
		if hi >= len(sb) {
			hi = len(sb) - 1
		}
		for j := lo; j <= hi; j++ {
			if !bMatch[j] && sa[i] == sb[j] {
				aMatch[i], bMatch[j] = true, true
				matches++
				break
			}
		}
	}
	if matches == 0 {
		return 0
	}

	// Transpositions (each mismatch is one half-transposition, which is the
	// "t" unit the Jaro formula consumes directly).
	transpositions := 0
	k := 0
	for i := 0; i < len(sa); i++ {
		if !aMatch[i] {
			continue
		}
		for !bMatch[k] {
			k++
		}
		if sa[i] != sb[k] {
			transpositions++
		}
		k++
	}

	jaro := (float64(matches)/float64(len(sa)) +
		float64(matches)/float64(len(sb)) +
		(float64(matches)-float64(transpositions)/2)/float64(matches)) / 3

	// Winkler prefix boost.
	prefix := 0
	for i := 0; i < len(sa) && i < len(sb) && i < 4; i++ {
		if sa[i] == sb[i] {
			prefix++
		} else {
			break
		}
	}
	if jaro > 0.7 {
		jaro += 0.1 * float64(prefix) * (1 - jaro)
	}
	return jaro
}

// --- scoring & ranking ---

// Score scores one candidate against the local item, following Phase 0:
// id hit = 1.0; otherwise max similarity over title and original title,
// minus the year penalty (0.01 + diff/1000, capped 0.11; 0.11 when the
// candidate carries no year) and 0.01 when there is no poster.
func Score(local Local, c Candidate) float64 {
	if local.TMDBID != 0 && c.TMDBID == local.TMDBID {
		return 1.0
	}

	sim := calculateScore(local.Title, c.Title)
	if c.OriginalTitle != "" && c.OriginalTitle != c.Title {
		if s := calculateScore(local.Title, c.OriginalTitle); s > sim {
			sim = s
		}
	}
	if sim == 0 {
		return 0
	}

	penalty := 0.0
	switch {
	case local.Year > 0 && c.Year == 0:
		penalty = 0.11
	case local.Year > 0 && c.Year > 0:
		diff := local.Year - c.Year
		if diff < 0 {
			diff = -diff
		}
		penalty = 0.01 + float64(diff)/1000
		if penalty > 0.11 {
			penalty = 0.11
		}
	}
	if c.PosterPath == "" {
		penalty += 0.01
	}

	score := sim - penalty
	if score < 0 {
		score = 0
	}
	return score
}

// Rank scores every candidate and returns them sorted by score descending,
// ties broken by year then TMDB id for determinism (mirroring the TMM
// TreeSet ordering of score desc, then year).
func Rank(local Local, candidates []Candidate) []MatchResult {
	out := make([]MatchResult, 0, len(candidates))
	for _, c := range candidates {
		out = append(out, MatchResult{
			Score:     Score(local, c),
			IDMatch:   local.TMDBID != 0 && c.TMDBID == local.TMDBID,
			Candidate: c,
		})
	}
	sort.SliceStable(out, func(i, j int) bool {
		if out[i].Score != out[j].Score {
			return out[i].Score > out[j].Score
		}
		if out[i].Candidate.Year != out[j].Candidate.Year {
			return out[i].Candidate.Year < out[j].Candidate.Year
		}
		return out[i].Candidate.TMDBID < out[j].Candidate.TMDBID
	})
	return out
}
