package media

import (
	"context"
	"database/sql"
	"path/filepath"
	"testing"

	"media-manager-lite/internal/database"
	"media-manager-lite/internal/parser"
)

func newTestStore(t *testing.T) (*Store, *sql.DB) {
	t.Helper()
	db, err := database.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	if err := database.Migrate(context.Background(), db.DB); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })
	return NewStore(db.DB), db.DB
}

// seedLibrary inserts a libraries row so media_items satisfy the foreign key.
func seedLibrary(t *testing.T, db *sql.DB, id string) {
	t.Helper()
	_, err := db.ExecContext(context.Background(),
		`INSERT INTO libraries (id, name, path, type, created_at, updated_at)
		 VALUES (?, 'Test', ?, 'movie', '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')`, id, "/tmp/"+id)
	if err != nil {
		t.Fatal(err)
	}
}

func sampleItem(path, title string, year int) Item {
	return Item{
		LibraryID:   "lib-1",
		Kind:        KindMovie,
		Path:        path,
		Filename:    filepath.Base(path),
		Ext:         filepath.Ext(path),
		Size:        123,
		ModTime:     "2026-10-01T00:00:00Z",
		ParsedTitle: title,
		ParsedYear:  year,
		Parsed:      &parser.Parsed{Title: title, Year: year, Resolution: "1080p"},
		Status:      StatusNew,
	}
}

func TestUpsertBatchInsertAndUpdate(t *testing.T) {
	store, db := newTestStore(t)
	seedLibrary(t, db, "lib-1")
	ctx := context.Background()

	ins, upd, err := store.UpsertBatch(ctx, []Item{
		sampleItem("/media/movies/A.2019.mkv", "A", 2019),
		sampleItem("/media/movies/B.2020.mkv", "B", 2020),
	})
	if err != nil {
		t.Fatal(err)
	}
	if ins != 2 || upd != 0 {
		t.Errorf("first batch = inserted %d updated %d, want 2/0", ins, upd)
	}

	// Same paths, changed size: updates only.
	items := []Item{
		sampleItem("/media/movies/A.2019.mkv", "A", 2019),
		sampleItem("/media/movies/B.2020.mkv", "B", 2020),
	}
	items[0].Size = 999
	ins, upd, err = store.UpsertBatch(ctx, items)
	if err != nil {
		t.Fatal(err)
	}
	if ins != 0 || upd != 2 {
		t.Errorf("second batch = inserted %d updated %d, want 0/2", ins, upd)
	}

	list, err := store.ListByLibrary(ctx, "lib-1")
	if err != nil {
		t.Fatal(err)
	}
	if len(list) != 2 {
		t.Fatalf("list = %d items, want 2 (no duplicates)", len(list))
	}
	for _, it := range list {
		if it.Filename == "A.2019.mkv" && it.Size != 999 {
			t.Errorf("updated size = %d, want 999", it.Size)
		}
		if it.Parsed == nil || it.Parsed.Resolution != "1080p" {
			t.Errorf("parsed JSON not persisted/restored: %+v", it.Parsed)
		}
		if it.CreatedAt == "" || it.UpdatedAt == "" {
			t.Errorf("timestamps missing: %+v", it)
		}
	}
}

func TestUpsertBatchEmpty(t *testing.T) {
	store, _ := newTestStore(t)
	ins, upd, err := store.UpsertBatch(context.Background(), nil)
	if err != nil {
		t.Fatal(err)
	}
	if ins != 0 || upd != 0 {
		t.Errorf("empty batch = %d/%d, want 0/0", ins, upd)
	}
}

func TestListByLibraryIsolation(t *testing.T) {
	store, db := newTestStore(t)
	seedLibrary(t, db, "lib-1")
	seedLibrary(t, db, "lib-2")
	ctx := context.Background()
	if _, _, err := store.UpsertBatch(ctx, []Item{sampleItem("/m/A.2019.mkv", "A", 2019)}); err != nil {
		t.Fatal(err)
	}
	if _, _, err := store.UpsertBatch(ctx, []Item{func() Item {
		it := sampleItem("/m/C.2021.mkv", "C", 2021)
		it.LibraryID = "lib-2"
		return it
	}()}); err != nil {
		t.Fatal(err)
	}

	list, err := store.ListByLibrary(ctx, "lib-1")
	if err != nil {
		t.Fatal(err)
	}
	if len(list) != 1 || list[0].Filename != "A.2019.mkv" {
		t.Errorf("lib-1 items = %+v, want only A", list)
	}
}
