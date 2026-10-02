package http

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"
)

// mediaDirWithFiles creates a movie library via the API and populates it
// with media and junk files, returning the library id.
func mediaDirWithFiles(t *testing.T, ts *httptest.Server) string {
	t.Helper()
	dir := canonicalize(t.TempDir())
	for _, f := range []string{
		"Movie.One.2019.1080p.BluRay.x264.mkv",
		"sub/Movie.Two.2020.2160p.WEB-DL.x265.mkv",
		"notes.nfo", "poster.jpg", "subs.eng.srt", "sample.mkv",
	} {
		p := filepath.Join(dir, f)
		if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(p, []byte("x"), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	return createLibraryViaAPI(t, ts, "Scan Movies", dir)
}

func createLibraryViaAPI(t *testing.T, ts *httptest.Server, name, path string) string {
	t.Helper()
	resp := postJSON(t, ts, "/api/libraries", map[string]string{
		"name": name, "path": path, "type": "movie",
	})
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusCreated {
		t.Fatalf("create library status = %d, want 201", resp.StatusCode)
	}
	var lib struct {
		ID string `json:"id"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&lib); err != nil {
		t.Fatal(err)
	}
	return lib.ID
}

func scanLibraryViaAPI(t *testing.T, ts *httptest.Server, id string) map[string]any {
	t.Helper()
	resp, err := http.Post(ts.URL+"/api/libraries/"+id+"/scan", "application/json", nil)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusAccepted {
		t.Fatalf("scan submit status = %d, want 202 (async)", resp.StatusCode)
	}
	var task map[string]any
	if err := json.NewDecoder(resp.Body).Decode(&task); err != nil {
		t.Fatal(err)
	}
	return task
}

// waitForTask polls /api/tasks until a scan_library task for the library is
// in the wanted state and returns its detail.
func waitForTask(t *testing.T, ts *httptest.Server, taskID, wantDetail string) string {
	t.Helper()
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		resp, err := http.Get(ts.URL + "/api/tasks")
		if err != nil {
			t.Fatal(err)
		}
		var out struct {
			Tasks []struct {
				ID     string `json:"id"`
				State  string `json:"state"`
				Detail string `json:"detail"`
				Error  string `json:"error"`
			} `json:"tasks"`
		}
		err = json.NewDecoder(resp.Body).Decode(&out)
		resp.Body.Close()
		if err != nil {
			t.Fatal(err)
		}
		for _, tk := range out.Tasks {
			if tk.ID != taskID {
				continue
			}
			switch tk.State {
			case "completed":
				if wantDetail != "" && tk.Detail != wantDetail {
					t.Fatalf("task detail = %q, want %q", tk.Detail, wantDetail)
				}
				return tk.Detail
			case "failed":
				t.Fatalf("task failed: %s", tk.Error)
			}
			time.Sleep(20 * time.Millisecond)
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatal("task did not complete within deadline")
	return ""
}

func TestScanLibraryFlow(t *testing.T) {
	ts := newTestServer(t)
	id := mediaDirWithFiles(t, ts)

	// Submit: 202 + pending task, HTTP returns immediately.
	task := scanLibraryViaAPI(t, ts, id)
	if task["state"] != "pending" {
		t.Fatalf("submitted task state = %v, want pending", task["state"])
	}
	taskID := task["id"].(string)

	waitForTask(t, ts, taskID, "found 2 files: 2 new, 0 updated")

	// Media listing reflects the scan; junk files are absent.
	resp, err := http.Get(ts.URL + "/api/libraries/" + id + "/media")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	var list struct {
		Count      int `json:"count"`
		MediaItems []struct {
			Filename    string `json:"filename"`
			Size        int64  `json:"size"`
			ModTime     string `json:"modTime"`
			ParsedTitle string `json:"parsedTitle"`
			ParsedYear  int    `json:"parsedYear"`
			Parsed      *struct {
				Resolution string `json:"resolution"`
				Source     string `json:"source"`
			} `json:"parsed"`
		} `json:"mediaItems"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&list); err != nil {
		t.Fatal(err)
	}
	if list.Count != 2 || len(list.MediaItems) != 2 {
		t.Fatalf("media list = %+v, want 2 items", list)
	}
	for _, it := range list.MediaItems {
		if it.ParsedTitle == "" || it.ParsedYear == 0 || it.Parsed == nil {
			t.Errorf("item missing parsed data: %+v", it)
		}
		if it.Size <= 0 || it.ModTime == "" {
			t.Errorf("item missing file statistics: %+v", it)
		}
	}

	// Rescanning is idempotent.
	task = scanLibraryViaAPI(t, ts, id)
	waitForTask(t, ts, task["id"].(string), "found 2 files: 0 new, 2 updated")
}

func TestScanLibraryUnknownID(t *testing.T) {
	ts := newTestServer(t)
	resp, err := http.Post(ts.URL+"/api/libraries/no-such-id/scan", "application/json", nil)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("status = %d, want 404", resp.StatusCode)
	}
}

func TestScanTVLibraryAccepted(t *testing.T) {
	ts := newTestServer(t)
	dir := canonicalize(t.TempDir())
	resp := postJSON(t, ts, "/api/libraries", map[string]string{
		"name": "TV", "path": dir, "type": "tv",
	})
	var lib struct {
		ID string `json:"id"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&lib); err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()

	// TV libraries submit like movie libraries since Milestone 6.
	resp2, err := http.Post(ts.URL+"/api/libraries/"+lib.ID+"/scan", "application/json", nil)
	if err != nil {
		t.Fatal(err)
	}
	defer resp2.Body.Close()
	if resp2.StatusCode != http.StatusAccepted {
		t.Errorf("tv scan status = %d, want 202", resp2.StatusCode)
	}
}

func TestMediaListUnknownLibrary(t *testing.T) {
	ts := newTestServer(t)
	resp, err := http.Get(ts.URL + "/api/libraries/no-such-id/media")
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusNotFound {
		t.Errorf("status = %d, want 404", resp.StatusCode)
	}
}

func TestScanLibraryHTMXFragment(t *testing.T) {
	ts := newTestServer(t)
	id := mediaDirWithFiles(t, ts)

	req, err := http.NewRequest(http.MethodPost, ts.URL+"/api/libraries/"+id+"/scan", nil)
	if err != nil {
		t.Fatal(err)
	}
	req.Header.Set("HX-Request", "true")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		t.Fatal(err)
	}

	if resp.StatusCode != http.StatusAccepted {
		t.Errorf("status = %d, want 202", resp.StatusCode)
	}
	if ct := resp.Header.Get("Content-Type"); ct != "text/html; charset=utf-8" {
		t.Errorf("Content-Type = %q, want fragment", ct)
	}
	if !bytes.Contains(body, []byte(`id="library-panel"`)) {
		t.Error("fragment missing library panel")
	}
}
