package uid

import (
	"regexp"
	"testing"
)

var uuidV4 = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`)

func TestNewFormat(t *testing.T) {
	for i := 0; i < 100; i++ {
		id := New()
		if !uuidV4.MatchString(id) {
			t.Fatalf("New() = %q, not a UUIDv4", id)
		}
	}
}

func TestNewUnique(t *testing.T) {
	seen := make(map[string]struct{})
	for i := 0; i < 1000; i++ {
		id := New()
		if _, dup := seen[id]; dup {
			t.Fatalf("duplicate id generated: %s", id)
		}
		seen[id] = struct{}{}
	}
}
