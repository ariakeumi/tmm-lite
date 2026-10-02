// Package renamer implements the movie renamer with a strict planner /
// executor split (Phase 0 §3.5):
//
//   - Plan is a pure function: it renders the configured templates, applies
//     the filename sanitation pipeline, computes the action list and detects
//     conflicts — it never touches the filesystem (a read-only FSView is
//     injected for existence checks).
//   - Execute performs the actions: preflight re-validation, folder rename
//     first, then the video, then derived NFO/artwork copies. Strict
//     operations (folder, video) fail when the destination exists and is
//     not the same file; derived copies (NFO/artwork naming variants) are
//     idempotent overwrites.
//
// Safety: dry-run = Plan only (zero writes); every destination is validated
// to stay inside the library root; a video destination that already exists
// fails the whole plan; re-running a completed rename is a no-op.
package renamer

import (
	"fmt"
	"path/filepath"
	"regexp"
	"strings"
)

// ActionKind classifies a planned action.
type ActionKind string

const (
	KindFolder  ActionKind = "folder"
	KindVideo   ActionKind = "video"
	KindNFO     ActionKind = "nfo"
	KindArtwork ActionKind = "artwork"
)

// Op names the filesystem operation.
const (
	OpMove      = "move"
	OpCopy      = "copy"
	OpCreateDir = "create_dir"
)

// Action is one planned filesystem operation. From paths are in the
// CURRENT (old) directory coordinates; To paths are final coordinates.
type Action struct {
	Kind ActionKind `json:"kind"`
	Op   string     `json:"op"`
	From string     `json:"from"`
	To   string     `json:"to"`
}

// Data are the token values for template rendering (movie metadata merged
// with the parsed technical tags of the media item).
type Data struct {
	Title           string
	OriginalTitle   string
	EnglishTitle    string
	Year            int
	IMDBID          string
	TMDBID          int
	VideoCodec      string
	AudioCodec      string
	VideoResolution string
	HDR             string
	MediaSource     string
}

// Profile configures patterns and the NFO/artwork naming variants
// ("basename" and "movie" for NFO; "poster"/"basename-poster" and
// "fanart"/"basename-fanart" for artwork).
type Profile struct {
	FolderPattern string   `json:"folderPattern"`
	FilePattern   string   `json:"filePattern"`
	NFONames      []string `json:"nfoNames"`
	PosterNames   []string `json:"posterNames"`
	FanartNames   []string `json:"fanartNames"`
}

// DefaultProfile matches the Phase-0 target layout:
// Movie Name (Year)/Movie Name (Year).ext with poster.jpg/fanart.jpg.
func DefaultProfile() Profile {
	return Profile{
		FolderPattern: "${title} (${year})",
		FilePattern:   "${title} (${year})",
		NFONames:      []string{"basename"},
		PosterNames:   []string{"poster"},
		FanartNames:   []string{"fanart"},
	}
}

// FileSet describes the current on-disk state of one movie folder as seen
// by the caller (the service enumerates the directory; Plan never does).
type FileSet struct {
	Dir    string   // canonical current directory of the video
	Video  string   // video filename
	Others []string // other files present in the same directory
}

// FSView is the read-only existence view injected into Plan.
type FSView interface {
	Exists(path string) bool
	IsDir(path string) bool
}

// Plan is the outcome of planning.
type Plan struct {
	Root   string `json:"root"`
	OldDir string `json:"oldDir"`
	NewDir string `json:"newDir"`
	Video  struct {
		From string `json:"from"`
		To   string `json:"to"`
	} `json:"video"`
	Actions     []Action `json:"actions"` // in execution order
	Problems    []string `json:"problems,omitempty"`
	Invalid     []string `json:"invalid,omitempty"`
	NewBasename string   `json:"newBasename"`
	Valid       bool     `json:"valid"`
}

// --- tokens ---

var tokenRe = regexp.MustCompile(`\$\{([A-Za-z0-9_]+)\}`)

// renderTemplate substitutes ${token} placeholders. Unknown tokens render
// as empty and are reported back for the problem list.
func renderTemplate(pattern string, d Data) (string, []string) {
	var unknown []string
	out := tokenRe.ReplaceAllStringFunc(pattern, func(m string) string {
		name := tokenRe.FindStringSubmatch(m)[1]
		switch name {
		case "title":
			return d.Title
		case "originalTitle":
			return d.OriginalTitle
		case "englishTitle":
			return d.EnglishTitle
		case "year":
			if d.Year > 0 {
				return fmt.Sprintf("%d", d.Year)
			}
			return ""
		case "imdb":
			return d.IMDBID
		case "tmdb":
			if d.TMDBID > 0 {
				return fmt.Sprintf("%d", d.TMDBID)
			}
			return ""
		case "videoCodec":
			return d.VideoCodec
		case "audioCodec":
			return d.AudioCodec
		case "videoResolution":
			return d.VideoResolution
		case "hdr":
			return d.HDR
		case "mediaSource":
			return d.MediaSource
		default:
			unknown = append(unknown, name)
			return ""
		}
	})
	return out, unknown
}

// --- sanitation pipeline (Phase 0 §3.5) ---

var (
	illegalChars   = regexp.MustCompile(`[<>:"/\\|?*\x00-\x1f]`)
	emptyBrackets  = regexp.MustCompile(`\([[:space:]\-.]*\)|\[[[:space:]\-.]*\]|\{[[:space:]\-.]*\}`)
	multiSpaces    = regexp.MustCompile(`[[:space:]]+`)
	edgeSeparators = " -_.,"
)

// sanitizeName applies the createDestination pipeline: remove illegal
// characters, strip now-empty bracket groups, collapse whitespace, trim
// separator/dot edges (also guards ".."-style traversal fragments).
func sanitizeName(s string) string {
	s = illegalChars.ReplaceAllString(s, " ")
	for {
		next := emptyBrackets.ReplaceAllString(s, " ")
		if next == s {
			break
		}
		s = next
	}
	s = multiSpaces.ReplaceAllString(s, " ")
	s = strings.Trim(s, edgeSeparators+" ")
	s = strings.TrimRight(s, ".") // Windows-style trailing dots
	s = strings.TrimSpace(s)
	return s
}

// --- planning ---

// Plan computes the rename actions for one movie folder. It is pure with
// respect to the filesystem: all existence checks go through view.
func PlanRename(root string, fs FileSet, view FSView, d Data, p Profile) *Plan {
	root = filepath.Clean(root)
	fs.Dir = filepath.Clean(fs.Dir)
	plan := &Plan{Root: root, OldDir: fs.Dir, NewDir: fs.Dir, Actions: []Action{}, Problems: []string{}, Invalid: []string{}}

	oldBase := strings.TrimSuffix(fs.Video, filepath.Ext(fs.Video))
	ext := filepath.Ext(fs.Video)

	// 1) Render + sanitize the file basename.
	rendered, unknownFile := renderTemplate(p.FilePattern, d)
	newBase := sanitizeName(rendered)
	for _, u := range unknownFile {
		plan.Invalid = append(plan.Invalid, fmt.Sprintf("unknown token ${%s} in file pattern", u))
	}
	if newBase == "" {
		plan.Invalid = append(plan.Invalid, "file pattern rendered an empty name")
	}
	plan.NewBasename = newBase

	// 2) Folder rename (skipped when the pattern is empty or other videos
	// share the folder — a folder move would strand them).
	newFolder := ""
	if strings.TrimSpace(p.FolderPattern) != "" {
		renderedF, unknownFolder := renderTemplate(p.FolderPattern, d)
		newFolder = sanitizeName(renderedF)
		for _, u := range unknownFolder {
			plan.Invalid = append(plan.Invalid, fmt.Sprintf("unknown token ${%s} in folder pattern", u))
		}
		for _, other := range fs.Others {
			if other != fs.Video && isVideoName(other) {
				plan.Problems = append(plan.Problems,
					"folder not renamed: other video files share this directory")
				newFolder = ""
				break
			}
		}
	}
	if newFolder != "" && newFolder != filepath.Base(fs.Dir) {
		newDir := filepath.Join(root, newFolder)
		switch {
		case !withinRoot(root, newDir) || newDir == root:
			plan.Invalid = append(plan.Invalid, "destination folder escapes the library root")
		case fs.Dir == root:
			// The library root itself is never renamed (a rename would
			// move the whole library into itself). Like TMM, the files are
			// gathered into a new subfolder instead.
			if view.Exists(newDir) {
				plan.Invalid = append(plan.Invalid, fmt.Sprintf("destination folder already exists: %s", newDir))
			} else {
				plan.NewDir = newDir
				plan.Actions = append(plan.Actions, Action{Kind: KindFolder, Op: OpCreateDir, To: newDir})
			}
		case view.Exists(newDir):
			plan.Invalid = append(plan.Invalid, fmt.Sprintf("destination folder already exists: %s", newDir))
		default:
			plan.NewDir = newDir
			plan.Actions = append(plan.Actions, Action{Kind: KindFolder, Op: OpMove, From: plan.OldDir, To: plan.NewDir})
		}
	}

	// 3) Video move (strict: destination must not exist unless unchanged).
	if newBase != "" {
		to := filepath.Join(plan.NewDir, newBase+ext)
		from := filepath.Join(fs.Dir, fs.Video)
		if to == from {
			plan.Problems = append(plan.Problems, "video filename unchanged")
		} else {
			folderOp := ""
			for _, a := range plan.Actions {
				if a.Kind == KindFolder {
					folderOp = a.Op
				}
			}
			if folderOp == "" && view.Exists(to) {
				plan.Invalid = append(plan.Invalid, fmt.Sprintf("video destination already exists: %s", to))
			}
			plan.Video.From, plan.Video.To = from, to
			plan.Actions = append(plan.Actions, Action{Kind: KindVideo, Op: OpMove, From: from, To: to})
		}
	}

	// 4) Derived NFO / artwork naming variants (idempotent copies 1:N).
	addVariants := func(kind ActionKind, sources []string, nameVariants []string) {
		src := ""
		for _, candidate := range sources {
			if containsName(fs.Others, candidate) {
				src = filepath.Join(fs.Dir, candidate)
				break
			}
		}
		if src == "" {
			return
		}
		seen := map[string]bool{}
		for _, v := range nameVariants {
			var target string
			switch v {
			case "basename":
				suffix := ".jpg"
				if kind == KindNFO {
					suffix = ".nfo"
				}
				target = filepath.Join(plan.NewDir, newBase+suffix)
			case "movie":
				target = filepath.Join(plan.NewDir, "movie.nfo")
			case "poster":
				target = filepath.Join(plan.NewDir, "poster.jpg")
			case "fanart":
				target = filepath.Join(plan.NewDir, "fanart.jpg")
			case "basename-poster":
				target = filepath.Join(plan.NewDir, newBase+"-poster.jpg")
			case "basename-fanart":
				target = filepath.Join(plan.NewDir, newBase+"-fanart.jpg")
			default:
				plan.Problems = append(plan.Problems, fmt.Sprintf("unknown %s naming variant %q", kind, v))
				continue
			}
			if seen[target] {
				continue
			}
			seen[target] = true
			if target == src {
				plan.Problems = append(plan.Problems, fmt.Sprintf("%s already at %s", kind, filepath.Base(target)))
				continue
			}
			plan.Actions = append(plan.Actions, Action{Kind: kind, Op: OpCopy, From: src, To: target})
		}
	}
	addVariants(KindNFO, []string{oldBase + ".nfo", "movie.nfo"}, p.NFONames)
	addVariants(KindArtwork, []string{PosterFile, oldBase + "-poster.jpg"}, p.PosterNames)
	addVariants(KindArtwork, []string{FanartFile, oldBase + "-fanart.jpg"}, p.FanartNames)

	// 5) Duplicate destinations are a hard conflict.
	dests := map[string]string{}
	for _, a := range plan.Actions {
		if prev, dup := dests[a.To]; dup {
			plan.Invalid = append(plan.Invalid, fmt.Sprintf("duplicate destination %s (also target of %s)", a.To, prev))
			continue
		}
		dests[a.To] = string(a.Kind)
	}

	// 6) Every destination stays inside the library root.
	for _, a := range plan.Actions {
		if !withinRoot(root, a.To) {
			plan.Invalid = append(plan.Invalid, fmt.Sprintf("destination escapes the library root: %s", a.To))
		}
	}

	plan.Valid = len(plan.Invalid) == 0
	return plan
}

const (
	PosterFile = "poster.jpg"
	FanartFile = "fanart.jpg"
)

func containsName(names []string, needle string) bool {
	for _, n := range names {
		if n == needle {
			return true
		}
	}
	return false
}

func withinRoot(root, target string) bool {
	if target == root {
		return true
	}
	return strings.HasPrefix(target, root+string(filepath.Separator))
}

func isVideoName(name string) bool {
	switch strings.ToLower(filepath.Ext(name)) {
	case ".mkv", ".mp4", ".avi", ".mov", ".wmv", ".mpg", ".mpeg", ".m2ts", ".ts", ".m4v", ".iso", ".vob", ".webm", ".flv":
		return true
	}
	return false
}
