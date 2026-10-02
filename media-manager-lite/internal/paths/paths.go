// Package paths provides filesystem path canonicalization and validation for
// user-supplied media directories. It is shared by the library service (at
// registration time) and the scanner (before every walk, so an unmounted
// volume fails loudly instead of yielding an empty scan).
package paths

import (
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// Sentinel errors mapped to HTTP statuses by the transport layer.
var (
	ErrInvalidPath       = errors.New("path must be an existing directory")
	ErrPathNotDir        = errors.New("path is not a directory")
	ErrPathNotAccessible = errors.New("path is not accessible")
)

// CanonicalizeDir validates a user-supplied directory path and returns its
// canonical form: absolute, cleaned, symlinks resolved. It must exist, be a
// directory, and be readable (a directory listing is attempted).
func CanonicalizeDir(raw string) (string, error) {
	p := strings.TrimSpace(raw)
	if p == "" {
		return "", ErrInvalidPath
	}
	abs, err := filepath.Abs(p)
	if err != nil {
		return "", ErrInvalidPath
	}
	abs = filepath.Clean(abs)

	info, err := os.Stat(abs)
	if err != nil {
		return "", ErrInvalidPath
	}
	if !info.IsDir() {
		return "", ErrPathNotDir
	}

	resolved, err := filepath.EvalSymlinks(abs)
	if err != nil {
		// Typically a broken symlink.
		return "", ErrInvalidPath
	}
	info, err = os.Stat(resolved)
	if err != nil {
		return "", ErrInvalidPath
	}
	if !info.IsDir() {
		return "", ErrPathNotDir
	}

	dir, err := os.Open(resolved)
	if err != nil {
		return "", ErrPathNotAccessible
	}
	defer dir.Close()
	// An empty directory yields io.EOF, which still proves readability.
	if _, err := dir.Readdirnames(1); err != nil && !errors.Is(err, io.EOF) {
		return "", ErrPathNotAccessible
	}
	return resolved, nil
}

// WithinRoot reports whether target (already canonical) lies at or below the
// canonical root directory.
func WithinRoot(root, target string) bool {
	if target == root {
		return true
	}
	return strings.HasPrefix(target, root+string(filepath.Separator))
}
