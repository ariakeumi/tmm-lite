package renamer

import (
	"fmt"
	"path/filepath"
	"strconv"
	"strings"
)

// EpisodeProfile configures the TV rename patterns.
type EpisodeProfile struct {
	ShowFolderPattern   string   `json:"showFolderPattern"`   // "" = keep current directory
	SeasonFolderPattern string   `json:"seasonFolderPattern"` // "" = keep current directory
	FilePattern         string   `json:"filePattern"`
	MultiEpisodeStyle   string   `json:"multiEpisodeStyle"` // repeat | range
	NFONames            []string `json:"nfoNames"`          // episode: ["basename"]
}

// DefaultEpisodeProfile matches the Phase-0 target layout:
// Show Name/Season 01/Show Name - S01E01 - Episode Title.ext (REPEAT).
func DefaultEpisodeProfile() EpisodeProfile {
	return EpisodeProfile{
		ShowFolderPattern:   "${showTitle}",
		SeasonFolderPattern: "Season ${seasonNr2}",
		FilePattern:         "${showTitle} - S${seasonNr2}E${episodeNr2} - ${title}",
		MultiEpisodeStyle:   "repeat",
		NFONames:            []string{"basename"},
	}
}

// EpisodeData are the token values for the TV templates.
type EpisodeData struct {
	ShowTitle         string
	ShowOriginalTitle string
	ShowYear          int
	ShowTMDBID        int
	Season            int
	Episodes          []int
	Title             string
}

// renderMultiEpisode renders the episode-number token per the style:
// REPEAT → "01E02", RANGE → "01-E02" (only when contiguous, else repeat).
func renderMultiEpisode(nums []int, padded bool, style string) string {
	fmtNum := func(n int) string {
		if padded {
			return fmt.Sprintf("%02d", n)
		}
		return strconv.Itoa(n)
	}
	if len(nums) == 0 {
		return ""
	}
	sep := "E"
	if style == "range" && len(nums) > 1 {
		contiguous := true
		for i := 1; i < len(nums); i++ {
			if nums[i] != nums[i-1]+1 {
				contiguous = false
				break
			}
		}
		if contiguous {
			out := fmtNum(nums[0])
			for _, n := range nums[1:] {
				out += "-" + sep + fmtNum(n)
			}
			return out
		}
		style = "repeat"
	}
	out := fmtNum(nums[0])
	for _, n := range nums[1:] {
		out += sep + fmtNum(n)
	}
	return out
}

// renderEpisodeTemplate substitutes the TV tokens.
func renderEpisodeTemplate(pattern string, d EpisodeData, style string) (string, []string) {
	var unknown []string
	out := tokenRe.ReplaceAllStringFunc(pattern, func(m string) string {
		name := tokenRe.FindStringSubmatch(m)[1]
		switch name {
		case "showTitle":
			return d.ShowTitle
		case "showOriginalTitle":
			return d.ShowOriginalTitle
		case "showYear":
			if d.ShowYear > 0 {
				return strconv.Itoa(d.ShowYear)
			}
			return ""
		case "showTmdb":
			if d.ShowTMDBID > 0 {
				return strconv.Itoa(d.ShowTMDBID)
			}
			return ""
		case "seasonNr":
			return strconv.Itoa(d.Season)
		case "seasonNr2":
			return fmt.Sprintf("%02d", d.Season)
		case "episodeNr":
			return renderMultiEpisode(d.Episodes, false, style)
		case "episodeNr2":
			return renderMultiEpisode(d.Episodes, true, style)
		case "title":
			return d.Title
		default:
			unknown = append(unknown, name)
			return ""
		}
	})
	return out, unknown
}

// EpisodeFileSet mirrors FileSet for one episode media item.
type EpisodeFileSet struct {
	Dir    string
	Video  string
	Others []string
}

// PlanEpisodeRename computes the per-file TV rename actions. Folder
// structure is created (never renamed): the show folder and season folder
// are created inside the library root when missing, and the file (plus its
// NFO basename variant) moves in. Existing sibling episodes and folders are
// never touched, which keeps multi-season libraries safe and the operation
// idempotent.
func PlanEpisodeRename(root string, fs EpisodeFileSet, view FSView, d EpisodeData, p EpisodeProfile) *Plan {
	root = filepath.Clean(root)
	fs.Dir = filepath.Clean(fs.Dir)
	plan := &Plan{Root: root, OldDir: fs.Dir, NewDir: fs.Dir, Actions: []Action{}, Problems: []string{}, Invalid: []string{}}

	ext := filepath.Ext(fs.Video)

	// 1) Pure target folders from the patterns.
	renderedShow, unknownShow := renderEpisodeTemplate(p.ShowFolderPattern, d, p.MultiEpisodeStyle)
	showName := sanitizeName(renderedShow)
	for _, u := range unknownShow {
		plan.Invalid = append(plan.Invalid, fmt.Sprintf("unknown token ${%s} in show pattern", u))
	}
	renderedSeason, unknownSeason := renderEpisodeTemplate(p.SeasonFolderPattern, d, p.MultiEpisodeStyle)
	seasonName := sanitizeName(renderedSeason)
	for _, u := range unknownSeason {
		plan.Invalid = append(plan.Invalid, fmt.Sprintf("unknown token ${%s} in season pattern", u))
	}

	targetShow := fs.Dir
	if showName != "" {
		if name := sanitizeName(showName); name == "" {
			plan.Invalid = append(plan.Invalid, "show pattern rendered an empty name")
		} else {
			t := filepath.Join(root, name)
			if !withinRoot(root, t) || t == root {
				plan.Invalid = append(plan.Invalid, "show folder escapes the library root")
			} else {
				targetShow = t
			}
		}
	}

	targetSeason := targetShow
	if seasonName != "" {
		if name := sanitizeName(seasonName); name == "" {
			plan.Invalid = append(plan.Invalid, "season pattern rendered an empty name")
		} else {
			targetSeason = filepath.Join(targetShow, name)
		}
	}

	// 2) Reconcile with the current location. The file is never moved UP
	// out of a folder it already lives in when that folder is the intended
	// show/season container.
	seasonDir := targetSeason
	switch {
	case fs.Dir == targetSeason:
		seasonDir = fs.Dir
		plan.NewDir = seasonDir
	case withinRoot(targetSeason, fs.Dir):
		seasonDir = fs.Dir
		plan.NewDir = seasonDir
	case strings.HasPrefix(fs.Dir, targetShow+string(filepath.Separator)) || fs.Dir == targetShow:
		// Inside the show folder: reuse/derive the season folder.
		seasonDir = targetSeason
		plan.NewDir = seasonDir
	default:
		// Outside: ensure the show folder exists (create, never rename),
		// then the season folder inside it.
		plan.NewDir = targetSeason
		if !view.Exists(targetShow) {
			if view.IsDir(targetShow) {
				// unreachable; kept for clarity
			} else {
				plan.Actions = append(plan.Actions, Action{Kind: KindFolder, Op: OpCreateDir, To: targetShow})
			}
		}
		if !view.Exists(targetSeason) {
			plan.Actions = append(plan.Actions, Action{Kind: KindFolder, Op: OpCreateDir, To: targetSeason})
		}
	}
	if plan.NewDir != fs.Dir && view.Exists(plan.NewDir) && !view.IsDir(plan.NewDir) {
		plan.Invalid = append(plan.Invalid, fmt.Sprintf("folder path exists as a file: %s", plan.NewDir))
	}

	// 3) Episode file.
	rendered, unknown := renderEpisodeTemplate(p.FilePattern, d, p.MultiEpisodeStyle)
	newBase := sanitizeName(rendered)
	for _, u := range unknown {
		plan.Invalid = append(plan.Invalid, fmt.Sprintf("unknown token ${%s} in file pattern", u))
	}
	plan.NewBasename = newBase
	if newBase == "" {
		plan.Invalid = append(plan.Invalid, "file pattern rendered an empty name")
	} else {
		to := filepath.Join(plan.NewDir, newBase+ext)
		from := filepath.Join(fs.Dir, fs.Video)
		if to == from {
			plan.Problems = append(plan.Problems, "episode filename unchanged")
		} else {
			if view.Exists(to) {
				plan.Invalid = append(plan.Invalid, fmt.Sprintf("episode destination already exists: %s", to))
			}
			plan.Video.From, plan.Video.To = from, to
			plan.Actions = append(plan.Actions, Action{Kind: KindVideo, Op: OpMove, From: from, To: to})
		}
	}

	// 4) Episode NFO variant: moves with the video (metadata is not left
	// behind).
	nfoSrc := oldBase(fs.Video) + ".nfo"
	if containsName(fs.Others, nfoSrc) && newBase != "" {
		srcPath := filepath.Join(fs.Dir, nfoSrc)
		target := filepath.Join(plan.NewDir, newBase+".nfo")
		if target != srcPath {
			plan.Actions = append(plan.Actions, Action{Kind: KindNFO, Op: OpMove, From: srcPath, To: target})
		}
	}

	// Subtitles sharing the episode basename move along with it.
	planSubtitleMoves(plan, oldBase(fs.Video), newBase, fs.Dir, plan.NewDir, fs.Others, view, false)

	// 5) Destination containment + duplicate check.
	dests := map[string]bool{}
	for _, a := range plan.Actions {
		if a.To != "" && !withinRoot(root, a.To) {
			plan.Invalid = append(plan.Invalid, fmt.Sprintf("destination escapes the library root: %s", a.To))
		}
		if a.Op == OpMove {
			if dests[a.To] {
				plan.Invalid = append(plan.Invalid, fmt.Sprintf("duplicate destination: %s", a.To))
			}
			dests[a.To] = true
		}
	}

	plan.Valid = len(plan.Invalid) == 0
	return plan
}

func oldBase(name string) string {
	return strings.TrimSuffix(name, filepath.Ext(name))
}

func planContainsCreate(plan *Plan, dir string) bool {
	for _, a := range plan.Actions {
		if a.Kind == KindFolder && a.Op == OpCreateDir && a.To == dir {
			return true
		}
	}
	return false
}
