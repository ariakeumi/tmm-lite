package http

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// canonicalize mirrors the service's path canonicalization so expectations
// match on symlinked temp roots (macOS /var/folders → /private/var/folders).
func canonicalize(path string) string {
	resolved, err := filepath.EvalSymlinks(path)
	if err != nil {
		return filepath.Clean(path)
	}
	return resolved
}

func postJSON(t *testing.T, ts *httptest.Server, path string, v any) *http.Response {
	t.Helper()
	body, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}
	resp, err := http.Post(ts.URL+path, "application/json", bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	return resp
}

func TestHealth(t *testing.T) {
	ts := newTestServer(t)
	resp, err := http.Get(ts.URL + "/api/health")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		t.Fatalf("status = %d, want 200", resp.StatusCode)
	}
	var got map[string]string
	if err := json.NewDecoder(resp.Body).Decode(&got); err != nil {
		t.Fatal(err)
	}
	if len(got) != 1 || got["status"] != "ok" {
		t.Errorf("body = %v, want {\"status\":\"ok\"}", got)
	}
}

func TestVersion(t *testing.T) {
	ts := newTestServer(t)
	resp, err := http.Get(ts.URL + "/api/version")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()

	var got struct{ Version, Commit, BuildDate string }
	if err := json.NewDecoder(resp.Body).Decode(&got); err != nil {
		t.Fatal(err)
	}
	if got.Version == "" || got.Commit == "" || got.BuildDate == "" {
		t.Errorf("version response incomplete: %+v", got)
	}
}

func TestLibraryCRUDFlow(t *testing.T) {
	ts := newTestServer(t)
	mediaDir := canonicalize(t.TempDir())

	// Create (JSON).
	resp := postJSON(t, ts, "/api/libraries", map[string]string{
		"name": "Movies", "path": mediaDir, "type": "movie",
	})
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create status = %d, want 201", resp.StatusCode)
	}
	var lib struct {
		ID   string `json:"id"`
		Name string `json:"name"`
		Path string `json:"path"`
		Type string `json:"type"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&lib); err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if lib.ID == "" || lib.Name != "Movies" || lib.Path != mediaDir || lib.Type != "movie" {
		t.Fatalf("created library = %+v", lib)
	}

	// Get by id.
	resp2, err := http.Get(ts.URL + "/api/libraries/" + lib.ID)
	if err != nil {
		t.Fatal(err)
	}
	var got2 struct {
		ID string `json:"id"`
	}
	if err := json.NewDecoder(resp2.Body).Decode(&got2); err != nil {
		t.Fatal(err)
	}
	resp2.Body.Close()
	if got2.ID != lib.ID {
		t.Errorf("get by id = %q, want %q", got2.ID, lib.ID)
	}

	// List.
	resp3, err := http.Get(ts.URL + "/api/libraries")
	if err != nil {
		t.Fatal(err)
	}
	var list struct {
		Libraries []struct {
			ID string `json:"id"`
		} `json:"libraries"`
	}
	if err := json.NewDecoder(resp3.Body).Decode(&list); err != nil {
		t.Fatal(err)
	}
	resp3.Body.Close()
	if len(list.Libraries) != 1 || list.Libraries[0].ID != lib.ID {
		t.Errorf("list = %+v, want one entry %q", list.Libraries, lib.ID)
	}

	// Delete.
	req, err := http.NewRequest(http.MethodDelete, ts.URL+"/api/libraries/"+lib.ID, nil)
	if err != nil {
		t.Fatal(err)
	}
	resp4, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	resp4.Body.Close()
	if resp4.StatusCode != http.StatusNoContent {
		t.Fatalf("delete status = %d, want 204", resp4.StatusCode)
	}

	// Get after delete.
	resp5, err := http.Get(ts.URL + "/api/libraries/" + lib.ID)
	if err != nil {
		t.Fatal(err)
	}
	defer resp5.Body.Close()
	if resp5.StatusCode != http.StatusNotFound {
		t.Errorf("get after delete status = %d, want 404", resp5.StatusCode)
	}
}

func TestCreateLibraryValidationJSON(t *testing.T) {
	ts := newTestServer(t)
	mediaDir := t.TempDir()

	if resp := postJSON(t, ts, "/api/libraries", map[string]string{
		"name": "X", "path": mediaDir, "type": "film",
	}); resp.StatusCode != http.StatusBadRequest {
		t.Errorf("invalid type status = %d, want 400", resp.StatusCode)
	} else {
		resp.Body.Close()
	}

	if resp := postJSON(t, ts, "/api/libraries", map[string]string{
		"name": "X", "path": filepath.Join(mediaDir, "missing"), "type": "movie",
	}); resp.StatusCode != http.StatusBadRequest {
		t.Errorf("missing path status = %d, want 400", resp.StatusCode)
	} else {
		resp.Body.Close()
	}

	if resp := postJSON(t, ts, "/api/libraries", map[string]string{
		"name": "X", "path": mediaDir, "type": "movie",
	}); resp.StatusCode != http.StatusCreated {
		t.Errorf("first create status = %d, want 201", resp.StatusCode)
	} else {
		resp.Body.Close()
	}

	if resp := postJSON(t, ts, "/api/libraries", map[string]string{
		"name": "Y", "path": mediaDir, "type": "tv",
	}); resp.StatusCode != http.StatusConflict {
		t.Errorf("duplicate path status = %d, want 409", resp.StatusCode)
	} else {
		resp.Body.Close()
	}
}

func TestCreateLibraryHTMXFragment(t *testing.T) {
	ts := newTestServer(t)
	mediaDir := canonicalize(t.TempDir())

	form := url.Values{"name": {"TV Shows"}, "path": {mediaDir}, "type": {"tv"}}
	req, err := http.NewRequest(http.MethodPost, ts.URL+"/api/libraries", strings.NewReader(form.Encode()))
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("HX-Request", "true")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	body := new(bytes.Buffer)
	if _, err := body.ReadFrom(resp.Body); err != nil {
		t.Fatal(err)
	}

	if resp.StatusCode != http.StatusCreated {
		t.Errorf("status = %d, want 201", resp.StatusCode)
	}
	if ct := resp.Header.Get("Content-Type"); ct != "text/html; charset=utf-8" {
		t.Errorf("Content-Type = %q, want fragment", ct)
	}
	for _, want := range []string{`id="library-panel"`, "TV Shows", mediaDir} {
		if !strings.Contains(body.String(), want) {
			t.Errorf("fragment missing %q", want)
		}
	}
}

func TestCreateLibraryHTMXErrorShowsMessage(t *testing.T) {
	ts := newTestServer(t)

	form := url.Values{"name": {"X"}, "path": {"/definitely/not/here"}, "type": {"movie"}}
	req, err := http.NewRequest(http.MethodPost, ts.URL+"/api/libraries", strings.NewReader(form.Encode()))
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("HX-Request", "true")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	body := new(bytes.Buffer)
	if _, err := body.ReadFrom(resp.Body); err != nil {
		t.Fatal(err)
	}

	if resp.StatusCode != http.StatusOK {
		t.Errorf("fragment error mode status = %d, want 200 (see docs/decisions.md D7)", resp.StatusCode)
	}
	for _, want := range []string{`id="library-error"`, "path must be an existing directory"} {
		if !strings.Contains(body.String(), want) {
			t.Errorf("fragment missing %q; got: %s", want, body.String())
		}
	}
}

func TestDeleteLibraryHTMXFragment(t *testing.T) {
	ts := newTestServer(t)
	mediaDir := t.TempDir()

	resp := postJSON(t, ts, "/api/libraries", map[string]string{
		"name": "Movies", "path": mediaDir, "type": "movie",
	})
	var lib struct {
		ID string `json:"id"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&lib); err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()

	req, err := http.NewRequest(http.MethodDelete, ts.URL+"/api/libraries/"+lib.ID, nil)
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("HX-Request", "true")
	resp2, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp2.Body.Close()
	body := new(bytes.Buffer)
	if _, err := body.ReadFrom(resp2.Body); err != nil {
		t.Fatal(err)
	}

	if resp2.StatusCode != http.StatusOK {
		t.Errorf("status = %d, want 200", resp2.StatusCode)
	}
	if !strings.Contains(body.String(), "还没有媒体库") {
		t.Errorf("fragment should show empty list, got: %s", body.String())
	}
}

func TestCreateLibraryBodyTooLarge(t *testing.T) {
	ts := newTestServer(t)
	big := fmt.Sprintf(`{"name":"%s","path":"/tmp/x","type":"movie"}`, strings.Repeat("x", 2<<20))
	resp, err := http.Post(ts.URL+"/api/libraries", "application/json", strings.NewReader(big))
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusBadRequest {
		t.Errorf("oversized body status = %d, want 400", resp.StatusCode)
	}
}

func TestDeleteLibraryNotFound(t *testing.T) {
	ts := newTestServer(t)
	req, err := http.NewRequest(http.MethodDelete, ts.URL+"/api/libraries/no-such-id", nil)
	if err != nil {
		t.Fatal(err)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("status = %d, want 404", resp.StatusCode)
	}
}

func TestMediaDirStillExistsAfterDelete(t *testing.T) {
	ts := newTestServer(t)
	mediaDir := t.TempDir()

	resp := postJSON(t, ts, "/api/libraries", map[string]string{
		"name": "Movies", "path": mediaDir, "type": "movie",
	})
	var lib struct {
		ID string `json:"id"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&lib); err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()

	req, _ := http.NewRequest(http.MethodDelete, ts.URL+"/api/libraries/"+lib.ID, nil)
	resp2, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	resp2.Body.Close()

	if _, err := os.Stat(mediaDir); err != nil {
		t.Fatalf("media directory must survive library deletion: %v", err)
	}
}
