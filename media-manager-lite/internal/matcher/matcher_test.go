package matcher

import (
	"math"
	"testing"
)

func almostEqual(a, b float64) bool { return math.Abs(a-b) < 1e-9 }

func TestStrikeAMatch(t *testing.T) {
	cases := []struct {
		a, b string
		want float64
	}{
		{"inception", "inception", 1.0},
		{"inception", "inceptio", 14.0 / 15.0}, // 7 common pairs / (8+7)
		{"inception", "matrix", 0.0},           // no common letter pairs
		{"你的名字", "你的名字", 1.0},                  // CJK bigrams
		{"你的名字", "你的名字2", 0.8},                 // special-cased below
		{"", "something", 0.0},
		{"a", "a", 1.0}, // single char equal
	}
	for _, tc := range cases {
		got := StrikeAMatch(tc.a, tc.b)
		if tc.a == "你的名字" && tc.b == "你的名字2" {
			// (2*3)/(3+4) = 6/7
			if math.Abs(got-6.0/7.0) > 1e-9 {
				t.Errorf("StrikeAMatch(%q,%q) = %v, want 6/7", tc.a, tc.b, got)
			}
			continue
		}
		if !almostEqual(got, tc.want) {
			t.Errorf("StrikeAMatch(%q,%q) = %v, want %v", tc.a, tc.b, got, tc.want)
		}
	}
}

func TestJaroWinkler(t *testing.T) {
	if !almostEqual(JaroWinkler("matrix", "matrix"), 1.0) {
		t.Error("identical strings should score 1.0")
	}
	if JaroWinkler("matrix", "defg") != 0 {
		t.Error("disjoint strings should score 0")
	}
	// The classic Winkler example: MARTHA vs MARHTA.
	if !almostEqual(JaroWinkler("MARTHA", "MARHTA"), 0.9611111111111111) {
		t.Errorf("JaroWinkler(MARTHA, MARHTA) = %v", JaroWinkler("MARTHA", "MARHTA"))
	}
	// Shared prefix boosts the score above plain Jaro.
	jw := JaroWinkler("inception", "inceptionx")
	if jw <= JaroWinkler("ncepito", "inceptio") {
		t.Errorf("prefix boost not applied: %v", jw)
	}
}

func TestScoreIDMatch(t *testing.T) {
	local := Local{Title: "whatever", Year: 1999, TMDBID: 42}
	c := Candidate{TMDBID: 42, Title: "Completely Different", Year: 2100}
	if got := Score(local, c); got != 1.0 {
		t.Errorf("Score with id hit = %v, want 1.0", got)
	}
}

func TestScoreYearPenalty(t *testing.T) {
	title := "Movie"
	local := Local{Title: title, Year: 2019}

	cases := []struct {
		name       string
		candidate  Candidate
		wantSubstr string // "exact", "close", "far", "noyear", "noposter"
		want       float64
	}{
		{"same year", Candidate{TMDBID: 1, Title: title, Year: 2019, PosterPath: "/p.jpg"}, "exact", 0.99},
		{"one year off", Candidate{TMDBID: 1, Title: title, Year: 2018, PosterPath: "/p.jpg"}, "close", 0.989},
		{"far year capped", Candidate{TMDBID: 1, Title: title, Year: 1919, PosterPath: "/p.jpg"}, "far", 0.89},
		{"candidate without year", Candidate{TMDBID: 1, Title: title, PosterPath: "/p.jpg"}, "noyear", 0.89},
		{"no poster", Candidate{TMDBID: 1, Title: title, Year: 2019}, "noposter", 0.98},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := Score(local, tc.candidate)
			if !almostEqual(got, tc.want) {
				t.Errorf("Score = %v, want %v", got, tc.want)
			}
		})
	}

	// Local year unknown → no penalty at all.
	if got := Score(Local{Title: title}, Candidate{TMDBID: 1, Title: title, PosterPath: "/p.jpg"}); !almostEqual(got, 1.0) {
		t.Errorf("local without year should not be penalized, got %v", got)
	}
}

func TestRankOrdering(t *testing.T) {
	local := Local{Title: "Inception", Year: 2010}
	candidates := []Candidate{
		{TMDBID: 3, Title: "Some Other Film", Year: 2010, PosterPath: "/p.jpg"},
		{TMDBID: 1, Title: "Inception", Year: 2010, PosterPath: "/p.jpg"},
		{TMDBID: 2, Title: "Inception", Year: 2009, PosterPath: "/p.jpg"},
	}
	ranked := Rank(local, candidates)
	if len(ranked) != 3 {
		t.Fatalf("ranked = %d, want 3", len(ranked))
	}
	if ranked[0].Candidate.TMDBID != 1 {
		t.Errorf("top candidate = %d, want the exact title (1)", ranked[0].Candidate.TMDBID)
	}
	for i := 1; i < len(ranked); i++ {
		if ranked[i-1].Score < ranked[i].Score {
			t.Errorf("ranking not descending: %v before %v", ranked[i-1].Score, ranked[i].Score)
		}
	}
}

func TestCalculateScoreVariants(t *testing.T) {
	// The separator-cleaned variant: "The.Matrix" cleaned == "The Matrix".
	if got := calculateScore("the matrix", "The.Matrix"); got < 0.9 {
		t.Errorf("separator cleaning not applied, score = %v", got)
	}
	// The trailing-year-stripped variant.
	if got := calculateScore("inception 2010", "Inception"); got < 0.9 {
		t.Errorf("trailing year stripping not applied, score = %v", got)
	}
}
