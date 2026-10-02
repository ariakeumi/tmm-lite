package matcher

import (
	"testing"
)

func TestScoreTVShow(t *testing.T) {
	local := TVShowLocal{Title: "Breaking Bad", Year: 2008}

	// ID hit short-circuits to 1.0.
	idLocal := TVShowLocal{Title: "Breaking Bad", Year: 2008, TMDBID: 42}
	if got := ScoreTVShow(idLocal, TVShowCandidate{TMDBID: 42, Name: "Unrelated"}); got != 1.0 {
		t.Errorf("id hit = %v, want 1.0", got)
	}

	// Exact title + year beats title-only.
	exact := ScoreTVShow(local, TVShowCandidate{TMDBID: 1, Name: "Breaking Bad", Year: 2008, PosterPath: "/p.jpg"})
	titleOnly := ScoreTVShow(local, TVShowCandidate{TMDBID: 2, Name: "Breaking Bad", Year: 2015, PosterPath: "/p.jpg"})
	if exact <= titleOnly {
		t.Errorf("exact (%v) should outrank year-off (%v)", exact, titleOnly)
	}
	if exact != 0.99 {
		t.Errorf("exact = %v, want 0.99 (1.0 - same-year penalty)", exact)
	}

	// Original-name similarity counts.
	viaOriginal := ScoreTVShow(local, TVShowCandidate{TMDBID: 3, Name: "别的名字", OriginalName: "Breaking Bad", Year: 2008, PosterPath: "/p.jpg"})
	if viaOriginal != 0.99 {
		t.Errorf("original-name match = %v, want 0.99", viaOriginal)
	}
}

func TestRankTVShows(t *testing.T) {
	local := TVShowLocal{Title: "北斗", Year: 2021}
	candidates := []TVShowCandidate{
		{TMDBID: 3, Name: "Unrelated Show", Year: 2021, PosterPath: "/p.jpg"},
		{TMDBID: 1, Name: "北斗", Year: 2021, PosterPath: "/p.jpg"},
		{TMDBID: 2, Name: "北斗之拳", Year: 1984, PosterPath: "/p.jpg"},
	}
	ranked := RankTVShows(local, candidates)
	if ranked[0].Candidate.TMDBID != 1 {
		t.Errorf("top = %d, want exact title (1)", ranked[0].Candidate.TMDBID)
	}
	for i := 1; i < len(ranked); i++ {
		if ranked[i-1].Score < ranked[i].Score {
			t.Errorf("ranking not descending at %d", i)
		}
	}
}

func TestScoreTVEpisode(t *testing.T) {
	local := TVEpisodeLocal{Season: 2, Episode: 5, Title: "第一话"}

	// Number hit dominates: 1.0 even against a generic/garbage name.
	if got := ScoreTVEpisode(local, TVEpisodeCandidate{TMDBID: 9, Season: 2, Episode: 5, Name: "第 5 集"}); got != 1.0 {
		t.Errorf("number hit = %v, want 1.0", got)
	}
	// TMDB id hit likewise.
	idLocal := TVEpisodeLocal{Season: 2, Episode: 5, Title: "第一话", TMDBID: 7}
	if got := ScoreTVEpisode(idLocal, TVEpisodeCandidate{TMDBID: 7, Season: 9, Episode: 9, Name: "x"}); got != 1.0 {
		t.Errorf("id hit = %v, want 1.0", got)
	}
	// Wrong numbers: only title similarity, half weight.
	titleHit := ScoreTVEpisode(local, TVEpisodeCandidate{TMDBID: 3, Season: 2, Episode: 6, Name: "第一话"})
	if titleHit != 0.5 {
		t.Errorf("title-only = %v, want 0.5", titleHit)
	}
	// Nothing matches.
	if got := ScoreTVEpisode(local, TVEpisodeCandidate{TMDBID: 4, Season: 3, Episode: 1, Name: "无关"}); got != 0 {
		t.Errorf("no match = %v, want 0", got)
	}
}

func TestRankTVEpisodes(t *testing.T) {
	local := TVEpisodeLocal{Season: 1, Episode: 2, Title: "第二话"}
	candidates := []TVEpisodeCandidate{
		{TMDBID: 3, Season: 1, Episode: 3, Name: "第三话"},
		{TMDBID: 1, Season: 1, Episode: 2, Name: "第 2 集"},
		{TMDBID: 2, Season: 2, Episode: 1, Name: "第二话"},
	}
	ranked := RankTVEpisodes(local, candidates)
	if ranked[0].Candidate.TMDBID != 1 {
		t.Errorf("top = %d, want the number hit (1)", ranked[0].Candidate.TMDBID)
	}
	if !ranked[0].NumberHit {
		t.Error("top candidate should be flagged as number hit")
	}
}
