package tmdb

import (
	"regexp"
	"strings"
)

// EPISODE_TRANS replicates the Phase-0 behavior (§3.2 item 3): TMDB serves
// machine-translated generic episode names in the requested language
// ("Episode 5", "第 5 集", "エピソード 5", …). Without filtering, zh-CN
// episode lists fill up with these useless names. IsGenericEpisodeTitle
// reports whether a title is such a generic name: digits and punctuation
// are stripped and the remainder must be a localized "episode" word (or
// empty), or follow the CJK 第…集/第…話 pattern.
var episodeTransWords = map[string]bool{
	// Germanic / Romance / Slavic / others — the same language coverage
	// TMM ships (31 localized words).
	"episode": true, "episodes": true, "episodecards": true,
	"folge": true, "épisode": true, "episodio": true,
	"episodioi": true, "episódio": true, "episódios": true,
	"odcinek": true, "odcinki": true, "эпизод": true, "эпизоды": true,
	"aflevering": true, "avsnitt": true, "episodeb": true,
	"jakso": true, "jaksot": true, "episoden": true,
	"episodioa": true, "episodi": true, "bölüm": true,
	"epizoda": true, "epizód": true, "epizod": true,
	"επεισόδιο": true, "פרק": true, "エピソード": true,
	"에피소드": true, "لقطة": true, "หมวด": true,
}

var (
	episodeNumberStrip = regexp.MustCompile(`[\d\s\p{P}\p{S}]+`)
	// 第…集 / 第…話 / 第…话 (Chinese/Japanese "Episode N") with any
	// separator/number in between; also bare 第…部 style is NOT generic.
	cjkEpisodeRe = regexp.MustCompile(`^第[\s\d]*(?:集|話|话|季)$`)
)

// IsGenericEpisodeTitle reports whether title is a TMDB machine-translated
// generic episode name. Empty titles are generic by definition.
func IsGenericEpisodeTitle(title string) bool {
	t := strings.TrimSpace(title)
	if t == "" {
		return true
	}
	if cjkEpisodeRe.MatchString(strings.TrimSpace(t)) {
		return true
	}
	core := episodeNumberStrip.ReplaceAllString(strings.ToLower(t), "")
	if core == "" {
		return true // "5", "5.", "(5)" — number-only titles
	}
	return episodeTransWords[core]
}
