package task

import (
	"context"
	"errors"
	"log/slog"
	"path/filepath"
	"sync/atomic"
	"testing"
	"time"

	"media-manager-lite/internal/database"
)

func newTestRunner(t *testing.T, queueSize int) (*Store, *Runner) {
	t.Helper()
	db, err := database.Open(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatal(err)
	}
	if err := database.Migrate(context.Background(), db.DB); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { db.Close() })

	store := NewStore(db.DB)
	runner := NewRunner(store, slog.New(slog.NewTextHandler(testWriter{t}, nil)), queueSize)
	return store, runner
}

type testWriter struct{ t *testing.T }

func (w testWriter) Write(p []byte) (int, error) { return len(p), nil }

// waitFor polls until cond returns true or the deadline passes.
func waitFor(t *testing.T, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("condition not met within deadline")
}

func TestSubmitRunsAsynchronously(t *testing.T) {
	store, runner := newTestRunner(t, 16)

	var executed atomic.Int32
	runner.Register("scan_library", func(ctx context.Context, targetType, targetID string) (string, error) {
		executed.Add(1)
		return "found 7 files: 7 new, 0 updated", nil
	})
	if err := runner.Start(t.Context()); err != nil {
		t.Fatal(err)
	}

	tk, err := runner.Submit(context.Background(), "scan_library", "library", "lib-1")
	if err != nil {
		t.Fatal(err)
	}
	if tk.State != StatePending {
		t.Errorf("state after submit = %s, want pending (async)", tk.State)
	}

	waitFor(t, func() bool {
		t2, err := store.Get(context.Background(), tk.ID)
		return err == nil && t2.State == StateCompleted
	})
	got, err := store.Get(context.Background(), tk.ID)
	if err != nil {
		t.Fatal(err)
	}
	if got.Detail != "found 7 files: 7 new, 0 updated" {
		t.Errorf("detail = %q", got.Detail)
	}
	if got.StartedAt == "" || got.FinishedAt == "" {
		t.Errorf("timestamps missing: %+v", got)
	}
	if executed.Load() != 1 {
		t.Errorf("handler executed %d times, want 1", executed.Load())
	}
}

func TestSubmitFailureRecordedOnTask(t *testing.T) {
	store, runner := newTestRunner(t, 16)
	runner.Register("boom", func(ctx context.Context, targetType, targetID string) (string, error) {
		return "", errors.New("disk on fire")
	})
	if err := runner.Start(t.Context()); err != nil {
		t.Fatal(err)
	}

	tk, err := runner.Submit(context.Background(), "boom", "library", "lib-1")
	if err != nil {
		t.Fatal(err)
	}
	waitFor(t, func() bool {
		t2, err := store.Get(context.Background(), tk.ID)
		return err == nil && t2.State == StateFailed
	})
	got, _ := store.Get(context.Background(), tk.ID)
	if got.Error != "disk on fire" {
		t.Errorf("error = %q, want disk on fire", got.Error)
	}
}

func TestSubmitUnknownType(t *testing.T) {
	_, runner := newTestRunner(t, 16)
	if _, err := runner.Submit(context.Background(), "nope", "library", "x"); !errors.Is(err, ErrUnknownType) {
		t.Errorf("error = %v, want ErrUnknownType", err)
	}
}

func TestQueueFull(t *testing.T) {
	_, runner := newTestRunner(t, 1)

	block := make(chan struct{})
	runner.Register("block", func(ctx context.Context, targetType, targetID string) (string, error) {
		<-block
		return "ok", nil
	})
	if err := runner.Start(t.Context()); err != nil {
		t.Fatal(err)
	}

	// Worker picks up the first task and blocks; the queue holds the second.
	if _, err := runner.Submit(context.Background(), "block", "library", "a"); err != nil {
		t.Fatal(err)
	}
	var queued Task
	for i := 0; i < 100; i++ {
		tk, err := runner.Submit(context.Background(), "block", "library", "b")
		if err == nil {
			queued = tk
			break
		}
		time.Sleep(10 * time.Millisecond)
	}
	if queued.ID == "" {
		t.Fatal("second submit never succeeded")
	}
	// Queue is now full (worker blocked, buffer slot taken): third submit must fail.
	if _, err := runner.Submit(context.Background(), "block", "library", "c"); err == nil {
		t.Error("submit on full queue should fail")
	}
	close(block)
}

func TestRestartPolicy(t *testing.T) {
	store, runner := newTestRunner(t, 16)
	ctx := context.Background()

	// Simulate a crash: one task was mid-flight, one was still pending.
	seed := func(state string) string {
		id := filepath.Base(t.TempDir()) // unique enough
		const q = `INSERT INTO tasks (id, type, target_type, target_id, state, created_at)
		           VALUES (?, 'scan_library', 'library', 'lib-1', ?, '2026-10-01T00:00:00Z')`
		if _, err := store.db.ExecContext(ctx, q, id, state); err != nil {
			t.Fatal(err)
		}
		return id
	}
	runningID := seed(StateRunning)
	pendingID := seed(StatePending)

	var executed atomic.Int32
	runner.Register("scan_library", func(ctx context.Context, targetType, targetID string) (string, error) {
		executed.Add(1)
		return "recovered", nil
	})
	if err := runner.Start(ctx); err != nil {
		t.Fatal(err)
	}

	// The stale "running" task must be failed with the documented reason.
	waitFor(t, func() bool {
		tk, err := store.Get(ctx, runningID)
		return err == nil && tk.State == StateFailed
	})
	got, _ := store.Get(ctx, runningID)
	if got.Error != "interrupted by restart" {
		t.Errorf("stale running task error = %q", got.Error)
	}

	// The pending task must be re-enqueued and executed.
	waitFor(t, func() bool {
		tk, err := store.Get(ctx, pendingID)
		return err == nil && tk.State == StateCompleted
	})
	if executed.Load() != 1 {
		t.Errorf("pending task executed %d times, want 1", executed.Load())
	}
}
