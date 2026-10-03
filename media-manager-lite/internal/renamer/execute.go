package renamer

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
)

// ActionResult reports one executed (or skipped) action.
type ActionResult struct {
	Kind    ActionKind `json:"kind"`
	Op      string     `json:"op"`
	From    string     `json:"from"`
	To      string     `json:"to"`
	Skipped bool       `json:"skipped"`
}

// Execute performs the planned actions against the real filesystem:
//
//  1. preflight — the plan must be valid; every strict destination must not
//     exist (unless it equals its source) and every source must exist;
//  2. folder rename (if planned);
//  3. video rename;
//  4. derived NFO/artwork copies (atomic via temp file + rename).
//
// A preflight failure aborts before any modification. A failure after the
// folder rename leaves a partially renamed tree (mirroring TMM's
// non-transactional behavior) and is reported as an error.
func Execute(p *Plan) ([]ActionResult, error) {
	if p == nil || !p.Valid {
		return nil, fmt.Errorf("renamer: refusing to execute an invalid plan")
	}

	// Defense in depth: re-verify containment at execution time (the plan
	// was validated earlier; the check guards against TOCTOU on roots).
	for _, a := range p.Actions {
		if a.To != "" && !withinRoot(p.Root, filepath.Clean(a.To)) {
			return nil, fmt.Errorf("renamer: destination outside library root: %s", a.To)
		}
	}

	folderOp := ""
	for _, a := range p.Actions {
		if a.Kind == KindFolder {
			folderOp = a.Op
		}
	}
	// With a folder RENAME the sources relocate into the new directory;
	// with a folder CREATE they stay where they are.
	dirRelocates := folderOp == OpMove

	// Preflight (current coordinates: nothing has happened yet).
	for _, a := range p.Actions {
		if a.Kind == KindFolder {
			if a.Op == OpMove {
				if _, err := os.Stat(a.From); err != nil {
					return nil, fmt.Errorf("renamer: preflight: source folder missing: %w", err)
				}
			}
			if _, err := os.Stat(a.To); err == nil {
				return nil, fmt.Errorf("renamer: preflight: destination folder exists: %s", a.To)
			}
			continue
		}
		if _, err := os.Stat(a.From); err != nil {
			return nil, fmt.Errorf("renamer: preflight: source missing: %w", err)
		}
		// NFO moves overwrite: the file is our own derived metadata.
		if a.Kind == KindNFO && a.Op == OpMove {
			continue
		}
		// Destinations inside a to-be-created/renamed folder cannot exist
		// yet; without a folder action the destination is checked now.
		if folderOp == "" {
			if _, err := os.Stat(a.To); err == nil && a.To != a.From {
				return nil, fmt.Errorf("renamer: preflight: destination exists: %s", a.To)
			}
		}
	}

	var results []ActionResult

	// 1) Folder rename or create.
	for _, a := range p.Actions {
		if a.Kind != KindFolder {
			continue
		}
		switch a.Op {
		case OpMove:
			if err := os.Rename(a.From, a.To); err != nil {
				return nil, fmt.Errorf("renamer: folder rename: %w", err)
			}
		case OpCreateDir:
			if err := os.MkdirAll(a.To, 0o755); err != nil {
				return nil, fmt.Errorf("renamer: folder create: %w", err)
			}
		}
		results = append(results, ActionResult{Kind: a.Kind, Op: a.Op, From: a.From, To: a.To})
	}

	// translate maps an old-coordinate source path to its post-folder-op
	// location.
	translate := func(path string) string {
		if !dirRelocates {
			return path
		}
		return filepath.Join(p.NewDir, filepath.Base(path))
	}

	for _, a := range p.Actions {
		if a.Kind == KindFolder {
			continue
		}
		switch a.Op {
		case OpMove: // video (strict) or NFO (our metadata: overwrite)
			from := translate(a.From)
			if a.Kind == KindNFO {
				if from == a.To {
					continue
				}
				if err := moveFileAtomicOverwrite(from, a.To); err != nil {
					return results, fmt.Errorf("renamer: nfo move: %w", err)
				}
				results = append(results, ActionResult{Kind: a.Kind, Op: a.Op, From: from, To: a.To})
				continue
			}
			if _, err := os.Stat(a.To); err == nil && a.To != from {
				return results, fmt.Errorf("renamer: video destination exists: %s", a.To)
			}
			if err := os.Rename(from, a.To); err != nil {
				return results, fmt.Errorf("renamer: video rename: %w", err)
			}
			results = append(results, ActionResult{Kind: a.Kind, Op: a.Op, From: from, To: a.To})
		case OpCopy: // derived NFO/artwork variant (idempotent overwrite)
			from := translate(a.From)
			if err := copyFileAtomic(from, a.To); err != nil {
				return results, fmt.Errorf("renamer: copy %s: %w", filepath.Base(a.To), err)
			}
			results = append(results, ActionResult{Kind: a.Kind, Op: a.Op, From: from, To: a.To})
		default:
			return results, fmt.Errorf("renamer: unknown op %q", a.Op)
		}
	}
	return results, nil
}

// copyFileAtomic copies src onto dest via a temp file + rename so a crash
// never leaves a truncated derived file. Overwriting the destination is
// intentional: derived variants are idempotent.
func copyFileAtomic(src, dest string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	f, err := os.CreateTemp(filepath.Dir(dest), filepath.Base(dest)+".*.part")
	if err != nil {
		return err
	}
	tmp := f.Name()
	if _, err := io.Copy(f, in); err != nil {
		_ = f.Close()
		_ = os.Remove(tmp)
		return err
	}
	if err := f.Close(); err != nil {
		_ = os.Remove(tmp)
		return err
	}
	if err := os.Rename(tmp, dest); err != nil {
		_ = os.Remove(tmp)
		return err
	}
	return nil
}

// moveFileAtomicOverwrite copies src onto dest (temp + rename) and removes
// the source — a metadata "move" that tolerates an existing destination
// because NFO content is derived from the database.
func moveFileAtomicOverwrite(src, dest string) error {
	if src == dest {
		return nil
	}
	if err := copyFileAtomic(src, dest); err != nil {
		return err
	}
	return os.Remove(src)
}
