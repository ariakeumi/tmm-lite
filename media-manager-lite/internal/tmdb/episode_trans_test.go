package tmdb

import (
	"encoding/json"
	"testing"
)

func jsonUnmarshal(payload string, v any) error {
	return json.Unmarshal([]byte(payload), v)
}

func TestIsGenericEpisodeTitle(t *testing.T) {
	generic := []string{
		"", " ", "5", "5.", "(5)", "第 1 集", "第5集", "第 12 話", "第3话",
		"Episode 5", "episode 12", "EPISODE 1", "Folge 7", "Épisode 3",
		"Episodio 2", "Episódio 9", "Odcinek 4", "Эпизод 6", "エピソード 5",
		"에피소드 2", "Aflevering 8", "Jakso 1", "Bölüm 3", "Epizoda 7",
	}
	for _, in := range generic {
		if !IsGenericEpisodeTitle(in) {
			t.Errorf("IsGenericEpisodeTitle(%q) = false, want true", in)
		}
	}

	specific := []string{
		"Pilot",
		"The One Where Everything Happens",
		"Ozymandias",
		"第一话：启程", // has a real subtitle after the generic marker
		"第 1 集：序幕",
		"Winter Is Coming",
		"名场面",
	}
	for _, in := range specific {
		if IsGenericEpisodeTitle(in) {
			t.Errorf("IsGenericEpisodeTitle(%q) = true, want false", in)
		}
	}
}

func TestEpisodeGroupMapping(t *testing.T) {
	d := &EpisodeGroupDetail{}
	payload := `{"id":"g1","name":"Absolute order","groups":[
		{"type":2,"order":0,"episodes":[
			{"id":9001,"season_number":1,"episode_number":1,"order":0},
			{"id":9002,"season_number":1,"episode_number":2,"order":1},
			{"id":9003,"season_number":2,"episode_number":1,"order":2}]}]}`
	if err := jsonUnmarshal(payload, &d); err != nil {
		t.Fatal(err)
	}
	m := d.Mapping()
	if len(m) != 3 {
		t.Fatalf("mapping size = %d, want 3", len(m))
	}
	if p := m[9003]; p.SeasonNumber != 2 || p.EpisodeNumber != 1 || p.Order != 3 {
		t.Errorf("placement 9003 = %+v, want order 3 (0-based + 1)", p)
	}
	if p := m[9001]; p.Order != 1 {
		t.Errorf("placement 9001 = %+v, want order 1", p)
	}
}
