// Package task implements the application task model.
//
// Store persists task rows; Runner executes them on a single background
// worker (Milestone 3): Submit inserts a pending row and hands the task to
// the worker, returning immediately — HTTP requests never block on jobs.
//
// Restart policy (docs/decisions.md D15): on startup, tasks stuck in
// "running" from a previous process are marked failed; "pending" tasks are
// re-enqueued, because every current job (library scan) is idempotent.
// There is deliberately no retry, scheduling or worker pool yet.
package task

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"media-manager-lite/internal/uid"
)

// Task lifecycle states (mirrored by the tasks table CHECK constraint).
const (
	StatePending   = "pending"
	StateRunning   = "running"
	StateCompleted = "completed"
	StateFailed    = "failed"
)

// Task is one unit of background work recorded in the tasks table.
type Task struct {
	ID         string `json:"id"`
	Type       string `json:"type"`
	TargetType string `json:"targetType,omitempty"`
	TargetID   string `json:"targetId,omitempty"`
	State      string `json:"state"`
	Detail     string `json:"detail,omitempty"`
	Error      string `json:"error,omitempty"`
	CreatedAt  string `json:"createdAt"`
	StartedAt  string `json:"startedAt,omitempty"`
	FinishedAt string `json:"finishedAt,omitempty"`
}

// Handler executes a job for a registered task type. A non-nil error fails
// the task; the returned string is stored as the task detail.
type Handler func(ctx context.Context, targetType, targetID string) (string, error)

// ErrUnknownType is returned by Submit for unregistered task types.
var ErrUnknownType = errors.New("task: unknown task type")

// Store persists tasks in SQLite.
type Store struct {
	db *sql.DB
}

// NewStore returns a task store on the given database handle.
func NewStore(db *sql.DB) *Store {
	return &Store{db: db}
}

func (s *Store) insert(ctx context.Context, t Task) error {
	const q = `INSERT INTO tasks (id, type, target_type, target_id, state, created_at)
	           VALUES (?, ?, ?, ?, ?, ?)`
	_, err := s.db.ExecContext(ctx, q, t.ID, t.Type, t.TargetType, t.TargetID, t.State, t.CreatedAt)
	if err != nil {
		return fmt.Errorf("task store: insert: %w", err)
	}
	return nil
}

func (s *Store) setState(ctx context.Context, id, state string, stampColumns map[string]string, detail, errCol string) error {
	q := "UPDATE tasks SET state = ?"
	args := []any{state}
	for col, val := range stampColumns {
		q += ", " + col + " = ?"
		args = append(args, val)
	}
	if detail != "" {
		q += ", detail = ?"
		args = append(args, detail)
	}
	if errCol != "" {
		q += ", error = ?"
		args = append(args, errCol)
	}
	q += " WHERE id = ?"
	args = append(args, id)
	if _, err := s.db.ExecContext(ctx, q, args...); err != nil {
		return fmt.Errorf("task store: set %s: %w", state, err)
	}
	return nil
}

// List returns the most recent tasks, newest first.
func (s *Store) List(ctx context.Context, limit int) ([]Task, error) {
	const q = `SELECT id, type, target_type, target_id, state,
	                  COALESCE(detail, ''), COALESCE(error, ''), created_at,
	                  COALESCE(started_at, ''), COALESCE(finished_at, '')
	           FROM tasks ORDER BY created_at DESC, id DESC LIMIT ?`
	rows, err := s.db.QueryContext(ctx, q, limit)
	if err != nil {
		return nil, fmt.Errorf("task store: list: %w", err)
	}
	defer rows.Close()

	out := []Task{}
	for rows.Next() {
		var t Task
		if err := rows.Scan(&t.ID, &t.Type, &t.TargetType, &t.TargetID, &t.State,
			&t.Detail, &t.Error, &t.CreatedAt, &t.StartedAt, &t.FinishedAt); err != nil {
			return nil, fmt.Errorf("task store: scan: %w", err)
		}
		out = append(out, t)
	}
	return out, rows.Err()
}

// Get returns one task by id.
func (s *Store) Get(ctx context.Context, id string) (Task, error) {
	const q = `SELECT id, type, target_type, target_id, state,
	                  COALESCE(detail, ''), COALESCE(error, ''), created_at,
	                  COALESCE(started_at, ''), COALESCE(finished_at, '')
	           FROM tasks WHERE id = ?`
	var t Task
	err := s.db.QueryRowContext(ctx, q, id).Scan(&t.ID, &t.Type, &t.TargetType, &t.TargetID,
		&t.State, &t.Detail, &t.Error, &t.CreatedAt, &t.StartedAt, &t.FinishedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return Task{}, fmt.Errorf("task store: %w: %s", ErrUnknownType, "task not found")
	}
	if err != nil {
		return Task{}, fmt.Errorf("task store: get: %w", err)
	}
	return t, nil
}

// pendingIDs returns the ids of tasks still in "pending" state.
func (s *Store) pendingIDs(ctx context.Context) ([]Task, error) {
	const q = `SELECT id, type, target_type, target_id, state, created_at
	           FROM tasks WHERE state = 'pending' ORDER BY created_at, id`
	rows, err := s.db.QueryContext(ctx, q)
	if err != nil {
		return nil, fmt.Errorf("task store: pending: %w", err)
	}
	defer rows.Close()

	var out []Task
	for rows.Next() {
		var t Task
		if err := rows.Scan(&t.ID, &t.Type, &t.TargetType, &t.TargetID, &t.State, &t.CreatedAt); err != nil {
			return nil, fmt.Errorf("task store: pending scan: %w", err)
		}
		out = append(out, t)
	}
	return out, rows.Err()
}

// failRunning marks tasks left in "running" by a previous process as failed.
func (s *Store) failRunning(ctx context.Context) error {
	now := time.Now().UTC().Format(time.RFC3339)
	const q = `UPDATE tasks SET state = 'failed', error = 'interrupted by restart', finished_at = ?
	           WHERE state = 'running'`
	if _, err := s.db.ExecContext(ctx, q, now); err != nil {
		return fmt.Errorf("task store: fail running: %w", err)
	}
	return nil
}

// Runner executes tasks on a single background worker.
type Runner struct {
	store    *Store
	handlers map[string]Handler
	ch       chan Task
	log      *slog.Logger
}

// NewRunner returns a runner persisting into the given store. queueSize
// bounds how many submissions may wait while the worker is busy.
func NewRunner(store *Store, log *slog.Logger, queueSize int) *Runner {
	return &Runner{
		store:    store,
		handlers: map[string]Handler{},
		ch:       make(chan Task, queueSize),
		log:      log,
	}
}

// Register wires a task type to its handler. Register before Start.
func (r *Runner) Register(typ string, h Handler) {
	r.handlers[typ] = h
}

// Submit records a pending task and enqueues it. It returns immediately.
func (r *Runner) Submit(ctx context.Context, typ, targetType, targetID string) (Task, error) {
	if _, ok := r.handlers[typ]; !ok {
		return Task{}, fmt.Errorf("%w: %q", ErrUnknownType, typ)
	}
	t := Task{
		ID:         uid.New(),
		Type:       typ,
		TargetType: targetType,
		TargetID:   targetID,
		State:      StatePending,
		CreatedAt:  time.Now().UTC().Format(time.RFC3339),
	}
	if err := r.store.insert(ctx, t); err != nil {
		return Task{}, err
	}
	select {
	case r.ch <- t:
		return t, nil
	default:
		_ = r.store.setState(ctx, t.ID, StateFailed, map[string]string{"finished_at": time.Now().UTC().Format(time.RFC3339)}, "", "task queue full")
		return t, fmt.Errorf("task: queue full")
	}
}

// Start launches the single worker and applies the restart policy: stale
// "running" tasks fail, "pending" tasks are re-enqueued. It blocks only for
// that recovery, then runs the worker in the background.
func (r *Runner) Start(ctx context.Context) error {
	if err := r.store.failRunning(ctx); err != nil {
		return err
	}
	pending, err := r.store.pendingIDs(ctx)
	if err != nil {
		return err
	}
	for _, t := range pending {
		select {
		case r.ch <- t:
		default:
			r.log.Warn("task queue full during recovery, leaving pending", "task_id", t.ID)
		}
	}
	r.log.Info("task worker started", "recovered_pending", len(pending))

	go func() {
		for {
			select {
			case <-ctx.Done():
				r.log.Info("task worker stopped")
				return
			case t := <-r.ch:
				r.execute(ctx, t)
			}
		}
	}()
	return nil
}

func (r *Runner) execute(ctx context.Context, t Task) {
	h, ok := r.handlers[t.Type]
	if !ok {
		_ = r.store.setState(ctx, t.ID, StateFailed,
			map[string]string{"finished_at": time.Now().UTC().Format(time.RFC3339)},
			"", "no handler registered for "+t.Type)
		return
	}
	now := time.Now().UTC().Format(time.RFC3339)
	if err := r.store.setState(ctx, t.ID, StateRunning, map[string]string{"started_at": now}, "", ""); err != nil {
		r.log.Error("task: mark running", "error", err)
		return
	}

	detail, jobErr := h(ctx, t.TargetType, t.TargetID)
	now = time.Now().UTC().Format(time.RFC3339)
	if jobErr != nil {
		if err := r.store.setState(ctx, t.ID, StateFailed, map[string]string{"finished_at": now}, "", jobErr.Error()); err != nil {
			r.log.Error("task: record failure", "error", err)
		}
		r.log.Error("task failed", "type", t.Type, "target", t.TargetID, "error", jobErr.Error())
		return
	}
	if err := r.store.setState(ctx, t.ID, StateCompleted, map[string]string{"finished_at": now}, detail, ""); err != nil {
		r.log.Error("task: record completion", "error", err)
	}
}
