package http

import (
	"encoding/json"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// TestAPIKeyNeverLeaksAnywhere configures a distinctive API key and sweeps
// every GET endpoint (plus the JSON error surface) asserting the key never
// appears in any response body.
func TestAPIKeyNeverLeaksAnywhere(t *testing.T) {
	const secretKey = "secret-live-key-abc123xyz"
	ts, deps := newTestServerWithDeps(t, nil, "")
	if err := deps.Settings.Set(t.Context(), "tmdb_api_key", secretKey); err != nil {
		t.Fatal(err)
	}

	// Build a media library + scan it so more surface area is covered.
	mediaDir := canonicalize(t.TempDir())
	if err := os.WriteFile(filepath.Join(mediaDir, "A.2019.1080p.BluRay.x264.mkv"), []byte("x"), 0o600); err != nil {
		t.Fatal(err)
	}
	libID := createLibraryViaAPI(t, ts, "Leak Probe", mediaDir)
	scanLibraryViaAPI(t, ts, libID)
	for i := 0; i < 100; i++ {
		resp, err := http.Get(ts.URL + "/api/tasks")
		if err != nil {
			t.Fatal(err)
		}
		b, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		if strings.Contains(string(b), `"state":"completed"`) {
			break
		}
	}

	paths := []string{
		"/", "/libraries", "/settings",
		"/api/health", "/api/version",
		"/api/libraries", "/api/libraries/" + libID,
		"/api/libraries/" + libID + "/media",
		"/api/libraries/" + libID + "/skipped",
		"/api/tasks",
		"/api/settings/tmdb-api-key",
		"/api/tv/shows", "/api/nope", // 404 JSON surface too
	}
	for _, p := range paths {
		t.Run("GET "+p, func(t *testing.T) {
			resp, err := http.Get(ts.URL + p)
			if err != nil {
				t.Fatal(err)
			}
			defer resp.Body.Close()
			body, _ := io.ReadAll(resp.Body)
			if strings.Contains(string(body), secretKey) {
				t.Errorf("API key leaked in response of %s", p)
			}
		})
	}

	// POST error responses (bad TMDB id on unmatched flow) must not leak.
	resp := postJSON(t, ts, "/api/libraries", map[string]string{
		"name": "X", "path": "/definitely/missing", "type": "movie",
	})
	body, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	if strings.Contains(string(body), secretKey) {
		t.Error("API key leaked in error response")
	}

	// The key settings endpoint reports configured-ness, never the value.
	resp2, err := http.Get(ts.URL + "/api/settings/tmdb-api-key")
	if err != nil {
		t.Fatal(err)
	}
	defer resp2.Body.Close()
	var out struct {
		Configured bool `json:"configured"`
	}
	if err := json.NewDecoder(resp2.Body).Decode(&out); err != nil {
		t.Fatal(err)
	}
	if !out.Configured {
		t.Error("key should report as configured")
	}
}
