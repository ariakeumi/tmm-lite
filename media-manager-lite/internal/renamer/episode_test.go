package renamer

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func sampleEpisodeData() EpisodeData {
	return EpisodeData{ShowTitle: "北斗剧集", ShowYear: 2021, ShowTMDBID: 555, Season: 1, Episodes: []int{5}, Title: "启程"}
}

func TestRenderMultiEpisode(t *testing.T) {
	cases := []struct {
		nums   []int
		style  string
		padded bool
		want   string
	}{
		{[]int{5}, "repeat", true, "05"},
		{[]int{5, 6}, "repeat", true, "05E06"},
		{[]int{5, 6, 7}, "repeat", true, "05E06E07"},
		{[]int{5, 6}, "range", true, "05-E06"},
		{[]int{5, 7}, "range", true, "05E07"}, // non-contiguous falls back
		{[]int{5, 6}, "repeat", false, "5E6"},
	}
	for _, tc := range cases {
		if got := renderMultiEpisode(tc.nums, tc.padded, tc.style); got != tc.want {
			t.Errorf("renderMultiEpisode(%v, %v, %q) = %q, want %q", tc.nums, tc.padded, tc.style, got, tc.want)
		}
	}
}

func TestRenderEpisodeTemplate(t *testing.T) {
	d := sampleEpisodeData()
	d.Episodes = []int{5, 6}
	got, _ := renderEpisodeTemplate("${showTitle} - S${seasonNr2}E${episodeNr2} - ${title}", d, "repeat")
	if got != "北斗剧集 - S01E05E06 - 启程" {
		t.Errorf("repeat render = %q", got)
	}
	got, _ = renderEpisodeTemplate("${showTitle} - S${seasonNr2}E${episodeNr2}", d, "range")
	if got != "北斗剧集 - S01E05-E06" {
		t.Errorf("range render = %q", got)
	}
}

func TestPlanEpisodeRenameFull(t *testing.T) {
	root := "/tv"
	fs := EpisodeFileSet{
		Dir:    "/tv/北斗.S01.1080p",
		Video:  "北斗.S01E05.1080p.mkv",
		Others: []string{"北斗.S01E05.1080p.nfo"},
	}
	plan := PlanEpisodeRename(root, fs, fakeView{}, sampleEpisodeData(), DefaultEpisodeProfile())
	if !plan.Valid {
		t.Fatalf("plan invalid: %v", plan.Invalid)
	}
	wantDir := "/tv/北斗剧集/Season 01"
	if plan.NewDir != wantDir {
		t.Errorf("new dir = %q, want %q", plan.NewDir, wantDir)
	}
	if plan.Video.To != filepath.Join(wantDir, "北斗剧集 - S01E05 - 启程.mkv") {
		t.Errorf("video to = %q", plan.Video.To)
	}
	var creates, moves, copies int
	for _, a := range plan.Actions {
		switch a.Op {
		case OpCreateDir:
			creates++
		case OpMove:
			moves++
		case OpCopy:
			copies++
			if !strings.HasSuffix(a.To, "北斗剧集 - S01E05 - 启程.nfo") {
				t.Errorf("nfo target = %q", a.To)
			}
		}
	}
	if creates != 2 || moves != 2 || copies != 0 {
		t.Errorf("actions = creates %d moves %d copies %d (%+v)", creates, moves, copies, plan.Actions)
	}
	for _, a := range plan.Actions {
		if !strings.HasPrefix(a.To, root+"/") {
			t.Errorf("destination escapes root: %q", a.To)
		}
	}
}

func TestPlanEpisodeNoopWhenInPlace(t *testing.T) {
	root := "/tv"
	showDir := "/tv/北斗剧集"
	seasonDir := filepath.Join(showDir, "Season 01")
	fs := EpisodeFileSet{
		Dir:    seasonDir,
		Video:  "北斗剧集 - S01E05 - 启程.mkv",
		Others: []string{"北斗剧集 - S01E05 - 启程.nfo"},
	}
	view := fakeView{dirs: map[string]bool{showDir: true, seasonDir: true}}
	plan := PlanEpisodeRename(root, fs, view, sampleEpisodeData(), DefaultEpisodeProfile())
	if !plan.Valid {
		t.Fatalf("plan invalid: %v", plan.Invalid)
	}
	for _, a := range plan.Actions {
		if a.Op == OpMove || a.Op == OpCreateDir {
			t.Errorf("no-op plan has action: %+v", a)
		}
	}
	// NFO copy: same name → skipped as unchanged.
	for _, a := range plan.Actions {
		if a.Kind == KindNFO && a.From == a.To {
			t.Errorf("identity nfo copy planned: %+v", a)
		}
	}
}

func TestPlanEpisodeConflictVideoTarget(t *testing.T) {
	root := "/tv"
	showDir := "/tv/北斗剧集"
	seasonDir := filepath.Join(showDir, "Season 01")
	fs := EpisodeFileSet{Dir: root, Video: "other.mkv"}
	// The season dir exists; the target file exists as a different file.
	view := fakeView{
		dirs:  map[string]bool{showDir: true, seasonDir: true},
		paths: map[string]bool{filepath.Join(seasonDir, "北斗剧集 - S01E05 - 启程.mkv"): true},
	}
	plan := PlanEpisodeRename(root, fs, view, sampleEpisodeData(), DefaultEpisodeProfile())
	if plan.Valid {
		t.Fatal("plan should be invalid (episode destination exists)")
	}
	found := false
	for _, inv := range plan.Invalid {
		if strings.Contains(inv, "episode destination already exists") {
			found = true
		}
	}
	if !found {
		t.Errorf("invalid = %v", plan.Invalid)
	}
}

func TestPlanEpisodeEscapeDefused(t *testing.T) {
	d := sampleEpisodeData()
	d.ShowTitle = "../../etc"
	plan := PlanEpisodeRename("/tv", EpisodeFileSet{Dir: "/tv/x", Video: "x.S01E01.mkv"}, fakeView{}, d, DefaultEpisodeProfile())
	if !strings.HasPrefix(plan.NewDir, "/tv/") {
		t.Fatalf("escaped: %q", plan.NewDir)
	}
	for _, a := range plan.Actions {
		if !strings.HasPrefix(a.To, "/tv/") {
			t.Fatalf("destination escaped root: %q", a.To)
		}
	}
}

func TestExecuteEpisodeRename(t *testing.T) {
	root := t.TempDir()
	dir := filepath.Join(root, "start")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "start.mkv"), []byte("VIDEO"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "start.nfo"), []byte("NFO"), 0o600); err != nil {
		t.Fatal(err)
	}

	fs := EpisodeFileSet{Dir: dir, Video: "start.mkv", Others: []string{"start.nfo"}}
	plan := PlanEpisodeRename(root, fs, OSView{}, sampleEpisodeData(), DefaultEpisodeProfile())
	if !plan.Valid {
		t.Fatalf("plan invalid: %v", plan.Invalid)
	}
	if _, err := Execute(plan); err != nil {
		t.Fatal(err)
	}

	final := filepath.Join(root, "北斗剧集", "Season 01", "北斗剧集 - S01E05 - 启程.mkv")
	if b, err := os.ReadFile(final); err != nil || string(b) != "VIDEO" {
		t.Fatalf("renamed video = %v", err)
	}
	if b, err := os.ReadFile(filepath.Join(root, "北斗剧集", "Season 01", "北斗剧集 - S01E05 - 启程.nfo")); err != nil || string(b) != "NFO" {
		t.Fatalf("nfo copy = %v", err)
	}

	// Idempotent: planning again yields no moves (folders exist now).
	fs2 := EpisodeFileSet{
		Dir:    filepath.Join(root, "北斗剧集", "Season 01"),
		Video:  "北斗剧集 - S01E05 - 启程.mkv",
		Others: []string{"北斗剧集 - S01E05 - 启程.nfo"},
	}
	plan2 := PlanEpisodeRename(root, fs2, OSView{}, sampleEpisodeData(), DefaultEpisodeProfile())
	if !plan2.Valid {
		t.Fatalf("second plan invalid: %v", plan2.Invalid)
	}
	for _, a := range plan2.Actions {
		if a.Op == OpMove || a.Op == OpCreateDir {
			t.Errorf("second plan has action: %+v", a)
		}
	}
}
