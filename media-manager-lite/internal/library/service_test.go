package library

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"testing"

	"media-manager-lite/internal/database"
)

// newTestService returns a Service on a migrated temporary SQLite database.
func newTestService(t *testing.T) *Service {
	t.Helper()
	db, err := database.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	if err := database.Migrate(context.Background(), db.DB); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	return NewService(NewStore(db.DB))
}

func TestRepositoryRoundTrip(t *testing.T) {
	db, err := database.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close()
	if err := database.Migrate(context.Background(), db.DB); err != nil {
		t.Fatal(err)
	}
	store := NewStore(db.DB)
	ctx := context.Background()

	lib := Library{
		ID: "11111111-1111-4111-8111-111111111111", Name: "Movies",
		Path: "/media/movies", Type: TypeMovie,
		CreatedAt: "2026-10-01T00:00:00Z", UpdatedAt: "2026-10-01T00:00:00Z",
	}
	if err := store.Create(ctx, lib); err != nil {
		t.Fatalf("Create() error = %v", err)
	}

	got, err := store.Get(ctx, lib.ID)
	if err != nil {
		t.Fatalf("Get() error = %v", err)
	}
	if got != lib {
		t.Errorf("Get() = %+v, want %+v", got, lib)
	}

	libs, err := store.List(ctx)
	if err != nil {
		t.Fatalf("List() error = %v", err)
	}
	if len(libs) != 1 || libs[0].ID != lib.ID {
		t.Errorf("List() = %+v, want 1 entry %s", libs, lib.ID)
	}

	ok, err := store.ExistsByPath(ctx, "/media/movies")
	if err != nil {
		t.Fatal(err)
	}
	if !ok {
		t.Error("ExistsByPath() = false, want true")
	}

	if err := store.Delete(ctx, lib.ID); err != nil {
		t.Fatalf("Delete() error = %v", err)
	}
	if _, err := store.Get(ctx, lib.ID); !errors.Is(err, ErrNotFound) {
		t.Errorf("Get() after delete error = %v, want ErrNotFound", err)
	}
	if err := store.Delete(ctx, lib.ID); !errors.Is(err, ErrNotFound) {
		t.Errorf("Delete() of missing id error = %v, want ErrNotFound", err)
	}
}

// canonical returns the canonical form of a path, mirroring what the service
// stores (on macOS /var/folders is itself a symlink to /private/var/folders).
func canonical(t *testing.T, path string) string {
	t.Helper()
	resolved, err := filepath.EvalSymlinks(path)
	if err != nil {
		t.Fatal(err)
	}
	return resolved
}

func TestServiceCreateCanonicalizesPath(t *testing.T) {
	svc := newTestService(t)
	ctx := context.Background()

	// A symlinked temporary directory must resolve to its target, and a
	// non-clean path form must be normalized on the way.
	target := t.TempDir()
	want := canonical(t, target)
	link := filepath.Join(t.TempDir(), "link")
	if err := os.Symlink(target, link); err != nil {
		t.Skipf("symlinks unavailable: %v", err)
	}
	messy := filepath.Dir(link) + "/./" + filepath.Base(link) + "/"

	lib, err := svc.Create(ctx, "Movies", messy, TypeMovie)
	if err != nil {
		t.Fatalf("Create() error = %v", err)
	}
	if lib.Path != want {
		t.Errorf("Create() path = %q, want canonical %q", lib.Path, want)
	}
	if lib.ID == "" || lib.CreatedAt == "" || lib.UpdatedAt != lib.CreatedAt {
		t.Errorf("Create() did not populate id/timestamps: %+v", lib)
	}
}

func TestServiceCreateValidations(t *testing.T) {
	svc := newTestService(t)
	ctx := context.Background()
	dir := t.TempDir()

	// Valid creation for the later duplicate check.
	if _, err := svc.Create(ctx, "Movies", dir, TypeMovie); err != nil {
		t.Fatalf("Create() error = %v", err)
	}

	cases := []struct {
		name string
		n    string
		p    string
		ty   Type
		want error
	}{
		{"empty name", "   ", dir, TypeMovie, ErrInvalidName},
		{"invalid type", "X", dir, "film", ErrInvalidType},
		{"nonexistent path", "X", filepath.Join(dir, "nope"), TypeMovie, ErrInvalidPath},
		{"empty path", "X", "  ", TypeMovie, ErrInvalidPath},
		{"duplicate path", "X", dir, TypeTV, ErrDuplicatePath},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := svc.Create(ctx, tc.n, tc.p, tc.ty)
			if !errors.Is(err, tc.want) {
				t.Errorf("Create() error = %v, want %v", err, tc.want)
			}
		})
	}
}

func TestServiceCreateFileNotDirectory(t *testing.T) {
	svc := newTestService(t)
	ctx := context.Background()
	file := filepath.Join(t.TempDir(), "file.txt")
	if err := os.WriteFile(file, []byte("x"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Create(ctx, "X", file, TypeMovie); !errors.Is(err, ErrPathNotDir) {
		t.Errorf("Create() on file error = %v, want ErrPathNotDir", err)
	}
}

func TestServiceCreateUnreadableDirectory(t *testing.T) {
	svc := newTestService(t)
	ctx := context.Background()
	restricted := filepath.Join(t.TempDir(), "secret")
	if err := os.Mkdir(restricted, 0o000); err != nil {
		t.Skipf("cannot create mode-000 directory: %v", err)
	}
	t.Cleanup(func() { _ = os.Chmod(restricted, 0o755) }) // allow TempDir cleanup

	if _, err := svc.Create(ctx, "X", restricted, TypeMovie); !errors.Is(err, ErrPathNotAccessible) {
		t.Errorf("Create() on unreadable dir error = %v, want ErrPathNotAccessible", err)
	}
}

func TestServiceDeleteUnknownID(t *testing.T) {
	svc := newTestService(t)
	if err := svc.Delete(context.Background(), "nope"); !errors.Is(err, ErrNotFound) {
		t.Errorf("Delete() error = %v, want ErrNotFound", err)
	}
}
