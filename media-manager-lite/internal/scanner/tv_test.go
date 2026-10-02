package scanner

import (
	"context"
	"path/filepath"
	"testing"

	"media-manager-lite/internal/library"
)

func TestScanTVLibraryPersistsHierarchy(t *testing.T) {
	store, tvStore, db := newTestMediaStore(t)
	seedLibrary(t, db, "lib-tv")
	svc := NewService(store, tvStore)
	ctx := context.Background()

	dir := t.TempDir()
	write(t, filepath.Join(dir, "Breaking Bad", "Season 1", "Breaking.Bad.S01E01.720p.mkv"), "v")
	write(t, filepath.Join(dir, "Breaking Bad", "Season 1", "Breaking.Bad.S01E02.720p.mkv"), "v")
	write(t, filepath.Join(dir, "Breaking Bad", "Season 2", "Breaking.Bad.S02E01.720p.mkv"), "v")
	write(t, filepath.Join(dir, "北斗", "S01", "北斗.S01E05.1080p.mkv"), "v")
	// Multi-episode file: one item, two episode rows.
	write(t, filepath.Join(dir, "北斗", "S01", "北斗.S01E06-E07.1080p.mkv"), "v")
	// Junk: no episode marker.
	write(t, filepath.Join(dir, "北斗", "poster.jpg"), "x")
	write(t, filepath.Join(dir, "北斗", "readme.txt"), "x")

	lib := library.Library{ID: "lib-tv", Name: "TV", Path: dir, Type: library.TypeTV}
	stats, err := svc.ScanLibrary(ctx, lib)
	if err != nil {
		t.Fatal(err)
	}
	if stats.Found != 5 {
		t.Errorf("found = %d, want 5 (videos only)", stats.Found)
	}
	if stats.Skipped != 0 {
		t.Errorf("skipped = %d, want 0 (jpg/txt are filtered by Walk)", stats.Skipped)
	}
	if stats.Episodes != 6 { // 3 BB + 1 北斗 + 2 multi-episode rows
		t.Errorf("episodes = %d, want 6", stats.Episodes)
	}
	if stats.New != 5 || stats.Updated != 0 {
		t.Errorf("new/updated = %d/%d, want 5/0", stats.New, stats.Updated)
	}

	// Shows: one per distinct show folder.
	shows, err := tvStore.ListShows(ctx, "lib-tv")
	if err != nil {
		t.Fatal(err)
	}
	if len(shows) != 2 {
		t.Fatalf("shows = %d, want 2", len(shows))
	}
	titles := map[string]string{}
	for _, sh := range shows {
		titles[sh.Title] = sh.ID
	}
	if _, ok := titles["Breaking Bad"]; !ok {
		t.Errorf("shows = %v, want Breaking Bad", titles)
	}
	if _, ok := titles["北斗"]; !ok {
		t.Errorf("shows = %v, want 北斗", titles)
	}

	// Seasons: BB has 2, 北斗 has 1.
	seasons, err := tvStore.ListSeasons(ctx, titles["Breaking Bad"])
	if err != nil {
		t.Fatal(err)
	}
	if len(seasons) != 2 || seasons[0].Number != 1 || seasons[1].Number != 2 {
		t.Errorf("BB seasons = %+v", seasons)
	}

	// Episodes: ordered, multi-episode expansion present.
	eps, err := tvStore.ListEpisodes(ctx, titles["北斗"])
	if err != nil {
		t.Fatal(err)
	}
	if len(eps) != 3 {
		t.Fatalf("北斗 episodes = %d, want 3", len(eps))
	}
	if eps[0].SeasonNumber != 1 || eps[0].EpisodeNumber != 5 {
		t.Errorf("first episode = S%dE%d", eps[0].SeasonNumber, eps[0].EpisodeNumber)
	}
	if eps[1].EpisodeNumber != 6 || eps[2].EpisodeNumber != 7 {
		t.Errorf("multi-episode expansion = %d/%d", eps[1].EpisodeNumber, eps[2].EpisodeNumber)
	}

	// Media items: 5 episode items linked to their first episode.
	items, err := store.ListByLibrary(ctx, "lib-tv")
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 5 {
		t.Fatalf("media items = %d, want 5", len(items))
	}
	linked := 0
	wantSeason := map[string]int{
		"Breaking.Bad.S01E01.720p.mkv": 1,
		"Breaking.Bad.S01E02.720p.mkv": 1,
		"Breaking.Bad.S02E01.720p.mkv": 2,
		"北斗.S01E05.1080p.mkv":          1,
		"北斗.S01E06-E07.1080p.mkv":      1,
	}
	for _, it := range items {
		if it.Kind != "episode" {
			t.Errorf("item %s kind = %s", it.Filename, it.Kind)
		}
		if it.EpisodeID != "" {
			linked++
		}
		if it.ParsedSeason == nil || *it.ParsedSeason != wantSeason[it.Filename] {
			t.Errorf("item %s parsed season = %v, want %d", it.Filename, it.ParsedSeason, wantSeason[it.Filename])
		}
	}
	if linked != 5 {
		t.Errorf("linked items = %d, want 5", linked)
	}

	// Rescanning is idempotent: no duplicate rows.
	stats, err = svc.ScanLibrary(ctx, lib)
	if err != nil {
		t.Fatal(err)
	}
	if stats.New != 0 || stats.Updated != 5 || stats.Episodes != 6 {
		t.Errorf("rescan stats = %+v, want new 0 updated 5 episodes 6", stats)
	}
	shows2, _ := tvStore.ListShows(ctx, "lib-tv")
	if len(shows2) != 2 {
		t.Errorf("shows after rescan = %d, want 2", len(shows2))
	}
	eps2, _ := tvStore.ListEpisodes(ctx, titles["北斗"])
	if len(eps2) != 3 {
		t.Errorf("episodes after rescan = %d, want 3", len(eps2))
	}
}

func TestScanTVLibraryPathGone(t *testing.T) {
	store, tvStore, _ := newTestMediaStore(t)
	svc := NewService(store, tvStore)
	if _, err := svc.ScanLibrary(context.Background(),
		library.Library{ID: "x", Path: filepath.Join(t.TempDir(), "gone"), Type: library.TypeTV}); err == nil {
		t.Error("scan of missing path should fail")
	}
}
