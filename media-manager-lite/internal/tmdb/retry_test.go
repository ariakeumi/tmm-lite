package tmdb

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"
)

func TestRetryOn429WithRetryAfter(t *testing.T) {
	var calls atomic.Int32
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if calls.Add(1) < 3 {
			w.Header().Set("Retry-After", "0")
			w.WriteHeader(http.StatusTooManyRequests)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"page":1,"total_pages":1,"results":[{"id":1,"title":"A"}]}`))
	}))
	defer ts.Close()

	c := NewClient("fixture-key-0001", ts.URL)
	results, err := c.SearchMovie(context.Background(), "A", "en-US", 0)
	if err != nil {
		t.Fatalf("search after retries: %v", err)
	}
	if len(results) != 1 || results[0].TMDBID != 1 {
		t.Errorf("results = %+v", results)
	}
	if calls.Load() != 3 {
		t.Errorf("calls = %d, want 3 (2 retries)", calls.Load())
	}
}

func Test429ExhaustsRetriesThenRateLimited(t *testing.T) {
	var calls atomic.Int32
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		w.Header().Set("Retry-After", "0")
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer ts.Close()

	c := NewClient("fixture-key-0001", ts.URL)
	_, err := c.SearchMovie(context.Background(), "A", "en-US", 0)
	if !errors.Is(err, ErrRateLimited) {
		t.Errorf("error = %v, want ErrRateLimited (mapping preserved)", err)
	}
	if calls.Load() != maxRetries {
		t.Errorf("calls = %d, want %d", calls.Load(), maxRetries)
	}
}

func Test429LargeRetryAfterAborts(t *testing.T) {
	var calls atomic.Int32
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		w.Header().Set("Retry-After", "3600")
		w.WriteHeader(http.StatusTooManyRequests)
	}))
	defer ts.Close()

	c := NewClient("fixture-key-0001", ts.URL)
	start := time.Now()
	_, err := c.SearchMovie(context.Background(), "A", "en-US", 0)
	if !errors.Is(err, ErrRateLimited) {
		t.Errorf("error = %v, want ErrRateLimited", err)
	}
	if calls.Load() != 1 {
		t.Errorf("calls = %d, want 1 (Retry-After beyond cap must not wait)", calls.Load())
	}
	if time.Since(start) > 2*time.Second {
		t.Error("large Retry-After blocked the caller")
	}
}

func TestRetryOn5xxExponential(t *testing.T) {
	var calls atomic.Int32
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if calls.Add(1) == 1 {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"id":111,"title":"A","translations":{"translations":[]}}`))
	}))
	defer ts.Close()

	c := NewClient("fixture-key-0001", ts.URL)
	d, err := c.MovieDetail(context.Background(), 111, "en-US")
	if err != nil {
		t.Fatalf("detail after 5xx retry: %v", err)
	}
	if d.TMDBID != 111 {
		t.Errorf("detail = %+v", d)
	}
	if calls.Load() != 2 {
		t.Errorf("calls = %d, want 2", calls.Load())
	}
}

func TestNoRetryOnUnrecoverable4xx(t *testing.T) {
	var calls atomic.Int32
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		w.WriteHeader(http.StatusBadRequest)
	}))
	defer ts.Close()

	c := NewClient("fixture-key-0001", ts.URL)
	_, err := c.SearchMovie(context.Background(), "A", "en-US", 0)
	if err == nil {
		t.Fatal("expected an error")
	}
	if calls.Load() != 1 {
		t.Errorf("calls = %d, want 1 (client errors are not retried)", calls.Load())
	}
}

func TestNoRetryOnUnauthorized(t *testing.T) {
	var calls atomic.Int32
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer ts.Close()

	c := NewClient("fixture-key-0001", ts.URL)
	_, err := c.SearchMovie(context.Background(), "A", "en-US", 0)
	if !errors.Is(err, ErrUnauthorized) {
		t.Errorf("error = %v, want ErrUnauthorized", err)
	}
	if calls.Load() != 1 {
		t.Errorf("calls = %d, want 1", calls.Load())
	}
}

func TestResponseCacheTTL(t *testing.T) {
	var calls atomic.Int32
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"page":1,"total_pages":1,"results":[{"id":1,"title":"A"}]}`))
	}))
	defer ts.Close()

	c := NewClient("fixture-key-0001", ts.URL)
	ctx := context.Background()
	if _, err := c.SearchMovie(ctx, "A", "en-US", 0); err != nil {
		t.Fatal(err)
	}
	if _, err := c.SearchMovie(ctx, "A", "en-US", 0); err != nil {
		t.Fatal(err)
	}
	if calls.Load() != 1 {
		t.Errorf("calls = %d, want 1 (second hit served from cache)", calls.Load())
	}

	// TTL expiry: advance the clock.
	base := time.Now()
	c.now = func() time.Time { return base.Add(responseCacheTTL + time.Second) }
	if _, err := c.SearchMovie(ctx, "A", "en-US", 0); err != nil {
		t.Fatal(err)
	}
	if calls.Load() != 2 {
		t.Errorf("calls after TTL = %d, want 2", calls.Load())
	}
}

func TestCacheKeyIncludesQuery(t *testing.T) {
	var calls atomic.Int32
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		w.Header().Set("Content-Type", "application/json")
		w.Write([]byte(`{"page":1,"total_pages":1,"results":[]}`))
	}))
	defer ts.Close()

	c := NewClient("fixture-key-0001", ts.URL)
	ctx := context.Background()
	_, _ = c.SearchMovie(ctx, "A", "en-US", 0)
	_, _ = c.SearchMovie(ctx, "B", "en-US", 0)
	if calls.Load() != 2 {
		t.Errorf("calls = %d, want 2 (different queries must not share cache)", calls.Load())
	}
}
