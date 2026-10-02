// Package library implements the library domain: the model, the SQLite
// repository and the validation service used by the HTTP layer.
//
// A library is a user-registered media root directory (a bind mount in the
// container deployment). Milestone 1 only records it in the database —
// scanning starts in the next milestone and never mutates user files.
package library

import (
	"errors"

	"media-manager-lite/internal/paths"
)

// Type discriminates the kind of media a library holds.
type Type string

const (
	TypeMovie Type = "movie"
	TypeTV    Type = "tv"
)

// Valid reports whether t is one of the supported library types.
func (t Type) Valid() bool {
	return t == TypeMovie || t == TypeTV
}

// Library is a registered media root directory.
type Library struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	Path      string `json:"path"`
	Type      Type   `json:"type"`
	CreatedAt string `json:"createdAt"`
	UpdatedAt string `json:"updatedAt"`
}

// Sentinel errors mapped to HTTP statuses by the transport layer. Path
// errors are shared with the paths package (the scanner reuses them).
var (
	ErrNotFound          = errors.New("library not found")
	ErrInvalidName       = errors.New("name must not be empty")
	ErrInvalidType       = errors.New("type must be \"movie\" or \"tv\"")
	ErrInvalidPath       = paths.ErrInvalidPath
	ErrPathNotDir        = paths.ErrPathNotDir
	ErrPathNotAccessible = paths.ErrPathNotAccessible
	ErrDuplicatePath     = errors.New("a library with this path already exists")
)
