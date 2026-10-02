package library

import (
	"context"
	"strings"
	"time"

	"media-manager-lite/internal/paths"
	"media-manager-lite/internal/uid"
)

const maxNameLength = 255

// Service validates and applies library use cases. Milestone 1 covers CRUD
// with thorough path validation; scanning comes later.
type Service struct {
	store Store
}

// NewService returns a Service backed by the given store.
func NewService(store Store) *Service {
	return &Service{store: store}
}

// Create validates and registers a new library. The stored path is the
// canonicalized form of the input (absolute, symlink-resolved, cleaned).
// The filesystem is only inspected, never modified.
func (s *Service) Create(ctx context.Context, name, rawPath string, typ Type) (Library, error) {
	name = strings.TrimSpace(name)
	if name == "" || len(name) > maxNameLength {
		return Library{}, ErrInvalidName
	}
	if !typ.Valid() {
		return Library{}, ErrInvalidType
	}

	path, err := paths.CanonicalizeDir(rawPath)
	if err != nil {
		return Library{}, err
	}

	exists, err := s.store.ExistsByPath(ctx, path)
	if err != nil {
		return Library{}, err
	}
	if exists {
		return Library{}, ErrDuplicatePath
	}

	now := time.Now().UTC().Format(time.RFC3339)
	lib := Library{
		ID:        uid.New(),
		Name:      name,
		Path:      path,
		Type:      typ,
		CreatedAt: now,
		UpdatedAt: now,
	}
	if err := s.store.Create(ctx, lib); err != nil {
		return Library{}, err
	}
	return lib, nil
}

// List returns all libraries ordered by creation time.
func (s *Service) List(ctx context.Context) ([]Library, error) {
	return s.store.List(ctx)
}

// Get returns one library or ErrNotFound.
func (s *Service) Get(ctx context.Context, id string) (Library, error) {
	return s.store.Get(ctx, id)
}

// Delete removes the library database record (cascading to future child
// records). It never touches the filesystem.
func (s *Service) Delete(ctx context.Context, id string) error {
	return s.store.Delete(ctx, id)
}
