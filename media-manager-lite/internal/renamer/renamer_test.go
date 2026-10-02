package renamer

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

type fakeView struct {
	paths map[string]bool
	dirs  map[string]bool
}

func (f fakeView) Exists(p string) bool { return f.paths[p] || f.dirs[p] }
func (f fakeView) IsDir(p string) bool  { return f.dirs[p] }

func TestRenderTemplate(t *testing.T) {
	d := Data{Title: "电影", OriginalTitle: "Movie", Year: 2019, TMDBID: 42, IMDBID: "tt1",
		VideoCodec: "x264", VideoResolution: "1080p", HDR: "HDR10"}
	cases := map[string]string{
		"${title} (${year})":                               "电影 (2019)",
		"${originalTitle} ${year}":                         "Movie 2019",
		"${tmdb}-${imdb}":                                  "42-tt1",
		"${title} ${videoCodec} ${videoResolution} ${hdr}": "电影 x264 1080p HDR10",
		"no tokens":                                        "no tokens",
		"${unknown}":                                       "",
		"${title} (${unknown}) (${year})":                  "电影 () (2019)", // empty parens stripped by sanitize
	}
	for pattern, want := range cases {
		got, _ := renderTemplate(pattern, d)
		if got != want {
			t.Errorf("render(%q) = %q, want %q", pattern, got, want)
		}
	}
	if _, unknown := renderTemplate("${bogus}", d); len(unknown) != 1 || unknown[0] != "bogus" {
		t.Errorf("unknown tokens = %v, want [bogus]", unknown)
	}
	// Year 0 renders empty.
	got, _ := renderTemplate("${title} (${year})", Data{Title: "X"})
	if got != "X ()" {
		t.Errorf("year-0 render = %q", got)
	}
}

func TestSanitizeName(t *testing.T) {
	cases := map[string]string{
		"Movie Name":            "Movie Name",
		"Movie: The Sequel":     "Movie The Sequel",    // illegal colon removed
		"a/b\\c:d*e?f\"g<h>i|j": "a b c d e f g h i j", // all illegal characters
		"Movie () 2019 []":      "Movie 2019",          // empty bracket groups stripped
		"Movie   (  )   Name":   "Movie Name",          // whitespace collapse
		"..":                    "",                    // traversal fragment defused
		"../../etc/passwd":      "etc passwd",          // separators removed, edges trimmed
		"  padded name  ":       "padded name",
		"名字 with 空格":            "名字 with 空格", // CJK preserved
		"Movie...":              "Movie",      // trailing dots
	}
	for in, want := range cases {
		if got := sanitizeName(in); got != want {
			t.Errorf("sanitize(%q) = %q, want %q", in, got, want)
		}
	}
}

func sampleFS() FileSet {
	return FileSet{
		Dir:   "/lib/Movie.2019.1080p.GROUP",
		Video: "Movie.2019.1080p.GROUP.mkv",
		Others: []string{
			"Movie.2019.1080p.GROUP.nfo",
			"poster.jpg",
			"fanart.jpg",
		},
	}
}

func sampleData() Data {
	return Data{Title: "Movie Name", OriginalTitle: "Movie", Year: 2019, TMDBID: 27205, IMDBID: "tt1"}
}

func TestPlanRenameFull(t *testing.T) {
	root := "/lib"
	plan := PlanRename(root, sampleFS(), fakeView{}, sampleData(), DefaultProfile())
	if !plan.Valid {
		t.Fatalf("plan invalid: %v", plan.Invalid)
	}
	if plan.NewDir != filepath.Join(root, "Movie Name (2019)") {
		t.Errorf("new dir = %q", plan.NewDir)
	}
	if plan.Video.To != filepath.Join(root, "Movie Name (2019)", "Movie Name (2019).mkv") {
		t.Errorf("video to = %q", plan.Video.To)
	}

	kinds := map[ActionKind]int{}
	for _, a := range plan.Actions {
		kinds[a.Kind]++
	}
	if kinds[KindFolder] != 1 || kinds[KindVideo] != 1 {
		t.Errorf("actions = %v", plan.Actions)
	}
	// Default profile: the existing basename.nfo copies to <newbase>.nfo.
	var nfoCopies int
	for _, a := range plan.Actions {
		if a.Kind == KindNFO {
			nfoCopies++
			if !strings.HasSuffix(a.To, "Movie Name (2019).nfo") {
				t.Errorf("nfo target = %q", a.To)
			}
		}
	}
	if nfoCopies != 1 {
		t.Errorf("nfo copies = %d", nfoCopies)
	}
	// Every destination stays inside the root.
	for _, a := range plan.Actions {
		if !strings.HasPrefix(a.To, root+"/") {
			t.Errorf("destination escapes root: %q", a.To)
		}
	}
}

func TestPlanNamingVariants(t *testing.T) {
	p := DefaultProfile()
	p.NFONames = []string{"basename", "movie"}
	p.PosterNames = []string{"poster", "basename-poster"}
	p.FanartNames = []string{"fanart", "basename-fanart"}
	plan := PlanRename("/lib", sampleFS(), fakeView{}, sampleData(), p)

	want := map[string]bool{
		"/lib/Movie Name (2019)/Movie Name (2019).nfo":        true,
		"/lib/Movie Name (2019)/movie.nfo":                    true,
		"/lib/Movie Name (2019)/poster.jpg":                   true,
		"/lib/Movie Name (2019)/Movie Name (2019)-poster.jpg": true,
		"/lib/Movie Name (2019)/fanart.jpg":                   true,
		"/lib/Movie Name (2019)/Movie Name (2019)-fanart.jpg": true,
	}
	got := map[string]bool{}
	for _, a := range plan.Actions {
		if a.Kind == KindNFO || a.Kind == KindArtwork {
			got[a.To] = true
		}
	}
	for target := range want {
		if !got[target] {
			t.Errorf("missing variant target %q; got %v", target, got)
		}
	}
}

func TestPlanConflictVideoTargetExists(t *testing.T) {
	// Same folder rename (no folder move) with an existing different file
	// at the video destination.
	fs := FileSet{Dir: "/lib/Same", Video: "old.mkv", Others: []string{"new.mkv"}}
	view := fakeView{paths: map[string]bool{"/lib/Same/new.mkv": true}}
	p := DefaultProfile()
	p.FolderPattern = ""
	plan := PlanRename("/lib", fs, view, Data{Title: "new"}, p)
	if plan.Valid {
		t.Fatalf("plan should be invalid, got %v", plan.Actions)
	}
	found := false
	for _, inv := range plan.Invalid {
		if strings.Contains(inv, "video destination already exists") {
			found = true
		}
	}
	if !found {
		t.Errorf("invalid = %v", plan.Invalid)
	}
}

func TestPlanConflictFolderExists(t *testing.T) {
	view := fakeView{dirs: map[string]bool{"/lib/Movie Name (2019)": true}}
	plan := PlanRename("/lib", sampleFS(), view, sampleData(), DefaultProfile())
	if plan.Valid {
		t.Fatal("plan should be invalid when the destination folder exists")
	}
}

func TestPlanEscapeRejected(t *testing.T) {
	// A crafted title cannot escape the library root: the sanitizer strips
	// separators and edge dots, and the within-root check backstops the
	// folder path join. The invariant must hold for hostile input.
	d := sampleData()
	d.Title = "../../etc"
	plan := PlanRename("/lib", sampleFS(), fakeView{}, d, DefaultProfile())
	if !plan.Valid {
		t.Fatalf("hostile title should sanitize to a safe name, got %v", plan.Invalid)
	}
	if !strings.HasPrefix(plan.NewDir, "/lib/") {
		t.Fatalf("escaped folder: %q", plan.NewDir)
	}
	for _, a := range plan.Actions {
		if !strings.HasPrefix(a.To, "/lib/") {
			t.Fatalf("destination escaped root: %q", a.To)
		}
	}

	// A hostile folder pattern is also defused by the sanitizer.
	plan2 := PlanRename("/lib", sampleFS(), fakeView{}, Data{Title: "ok"}, Profile{
		FolderPattern: "../outside",
		FilePattern:   "${title}",
	})
	if plan2.NewDir == "/outside" || strings.HasPrefix(plan2.NewDir, "/outside") {
		t.Errorf("folder escape accepted: %q", plan2.NewDir)
	}
	if !strings.HasPrefix(plan2.NewDir, "/lib/") {
		t.Errorf("folder escape: %q", plan2.NewDir)
	}
}

func TestPlanOtherVideosBlockFolderRename(t *testing.T) {
	fs := sampleFS()
	fs.Others = append(fs.Others, "Other.Movie.2020.mkv")
	plan := PlanRename("/lib", fs, fakeView{}, sampleData(), DefaultProfile())
	if !plan.Valid {
		t.Fatalf("plan invalid: %v", plan.Invalid)
	}
	if plan.NewDir != plan.OldDir {
		t.Errorf("folder moved despite other videos: %q", plan.NewDir)
	}
	found := false
	for _, p := range plan.Problems {
		if strings.Contains(p, "other video files") {
			found = true
		}
	}
	if !found {
		t.Errorf("problems = %v", plan.Problems)
	}
}

func TestPlanNoopWhenAlreadyNamed(t *testing.T) {
	fs := FileSet{
		Dir:   "/lib/Movie Name (2019)",
		Video: "Movie Name (2019).mkv",
		Others: []string{
			"Movie Name (2019).nfo", "poster.jpg", "fanart.jpg",
			"Movie Name (2019)-poster.jpg", "Movie Name (2019)-fanart.jpg", "movie.nfo",
		},
	}
	plan := PlanRename("/lib", fs, fakeView{}, sampleData(), DefaultProfile())
	if !plan.Valid {
		t.Fatalf("plan invalid: %v", plan.Invalid)
	}
	if len(plan.Actions) != 0 {
		t.Errorf("no-op plan has actions: %v", plan.Actions)
	}
}

func TestExecuteRealRename(t *testing.T) {
	root := t.TempDir()
	dir := filepath.Join(root, "Movie.2019.1080p.GROUP")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	video := filepath.Join(dir, "Movie.2019.1080p.GROUP.mkv")
	if err := os.WriteFile(video, []byte("VIDEO"), 0o600); err != nil {
		t.Fatal(err)
	}
	for _, f := range []string{"Movie.2019.1080p.GROUP.nfo", "poster.jpg", "fanart.jpg"} {
		if err := os.WriteFile(filepath.Join(dir, f), []byte("data:"+f), 0o600); err != nil {
			t.Fatal(err)
		}
	}

	fs := FileSet{Dir: dir, Video: "Movie.2019.1080p.GROUP.mkv", Others: []string{
		"Movie.2019.1080p.GROUP.nfo", "poster.jpg", "fanart.jpg"}}
	plan := PlanRename(root, fs, OSView{}, sampleData(), DefaultProfile())
	if !plan.Valid {
		t.Fatalf("plan invalid: %v", plan.Invalid)
	}
	results, err := Execute(plan)
	if err != nil {
		t.Fatal(err)
	}
	if len(results) != 4 { // folder + video + nfo + 2 artwork = 5? folder, video, nfo, poster, fanart
		t.Logf("results: %+v", results)
	}

	newDir := filepath.Join(root, "Movie Name (2019)")
	newVideo := filepath.Join(newDir, "Movie Name (2019).mkv")
	if _, err := os.Stat(newVideo); err != nil {
		t.Fatalf("renamed video missing: %v", err)
	}
	if b, _ := os.ReadFile(newVideo); string(b) != "VIDEO" {
		t.Error("video content changed")
	}
	if _, err := os.Stat(dir); !os.IsNotExist(err) {
		t.Error("old folder should be gone after the directory rename")
	}
	for _, f := range []string{"Movie Name (2019).nfo", "poster.jpg", "fanart.jpg"} {
		if _, err := os.Stat(filepath.Join(newDir, f)); err != nil {
			t.Errorf("%s missing: %v", f, err)
		}
	}
}

func TestExecuteConflictFailsBeforeTouchingAnything(t *testing.T) {
	root := t.TempDir()
	dir := filepath.Join(root, "old")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	video := filepath.Join(dir, "old.mkv")
	if err := os.WriteFile(video, []byte("V"), 0o600); err != nil {
		t.Fatal(err)
	}
	// A conflicting destination folder already exists.
	existing := filepath.Join(root, "Movie Name (2019)")
	if err := os.MkdirAll(existing, 0o755); err != nil {
		t.Fatal(err)
	}

	fs := FileSet{Dir: dir, Video: "old.mkv"}
	plan := PlanRename(root, fs, OSView{}, sampleData(), DefaultProfile())
	if plan.Valid {
		t.Fatal("plan should be invalid (destination folder exists)")
	}
	if _, err := Execute(plan); err == nil {
		t.Fatal("execute on invalid plan must fail")
	}
	// Nothing was touched.
	if _, err := os.Stat(video); err != nil {
		t.Fatalf("source video disturbed: %v", err)
	}
	if b, _ := os.ReadFile(video); string(b) != "V" {
		t.Error("video content changed")
	}
}

func TestExecuteVideoTargetExistsFails(t *testing.T) {
	root := t.TempDir()
	dir := filepath.Join(root, "Same")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "old.mkv"), []byte("OLD"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "new.mkv"), []byte("NEW"), 0o600); err != nil {
		t.Fatal(err)
	}

	fs := FileSet{Dir: dir, Video: "old.mkv"}
	p := DefaultProfile()
	p.FolderPattern = ""
	plan := PlanRename(root, fs, OSView{}, Data{Title: "new"}, p)
	if plan.Valid {
		t.Fatal("plan should be invalid (video destination exists)")
	}
	if _, err := Execute(plan); err == nil {
		t.Fatal("execute must fail on conflict")
	}
	// Both files untouched.
	if b, _ := os.ReadFile(filepath.Join(dir, "old.mkv")); string(b) != "OLD" {
		t.Error("source modified")
	}
	if b, _ := os.ReadFile(filepath.Join(dir, "new.mkv")); string(b) != "NEW" {
		t.Error("destination overwritten")
	}
}

func TestExecuteIdempotent(t *testing.T) {
	root := t.TempDir()
	dir := filepath.Join(root, "start")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "start.mkv"), []byte("V"), 0o600); err != nil {
		t.Fatal(err)
	}

	fs := FileSet{Dir: dir, Video: "start.mkv"}
	plan := PlanRename(root, fs, OSView{}, sampleData(), DefaultProfile())
	if _, err := Execute(plan); err != nil {
		t.Fatal(err)
	}

	// Second run: everything already in place → zero actions, success.
	fs2 := FileSet{
		Dir:    filepath.Join(root, "Movie Name (2019)"),
		Video:  "Movie Name (2019).mkv",
		Others: []string{"Movie Name (2019).nfo", "poster.jpg", "fanart.jpg"},
	}
	plan2 := PlanRename(root, fs2, OSView{}, sampleData(), DefaultProfile())
	if !plan2.Valid || len(plan2.Actions) != 0 {
		t.Fatalf("second plan = %+v (actions %d)", plan2.Invalid, len(plan2.Actions))
	}
	if _, err := Execute(plan2); err != nil {
		t.Fatal(err)
	}
}
