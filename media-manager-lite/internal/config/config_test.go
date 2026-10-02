package config

import (
	"log/slog"
	"os"
	"path/filepath"
	"testing"
)

func TestDefaults(t *testing.T) {
	cfg, err := Load(func(string) string { return "" })
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	want := Defaults()
	if cfg != want {
		t.Fatalf("Load() = %+v, want %+v", cfg, want)
	}
}

func TestEnvOverrides(t *testing.T) {
	env := map[string]string{
		EnvHTTPAddr: ":9090",
		EnvDBPath:   "/config/mml.db",
		EnvLogLevel: "DEBUG",
	}
	cfg, err := Load(func(k string) string { return env[k] })
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if cfg.HTTPAddr != ":9090" {
		t.Errorf("HTTPAddr = %q, want :9090", cfg.HTTPAddr)
	}
	if cfg.DBPath != "/config/mml.db" {
		t.Errorf("DBPath = %q, want /config/mml.db", cfg.DBPath)
	}
	if cfg.LogLevel != "debug" {
		t.Errorf("LogLevel = %q, want debug (lowercased)", cfg.LogLevel)
	}
	if cfg.SlogLevel() != slog.LevelDebug {
		t.Errorf("SlogLevel() = %v, want LevelDebug", cfg.SlogLevel())
	}
}

func TestYAMLFileAndEnvPrecedence(t *testing.T) {
	// File overrides defaults; env overrides file.
	path := filepath.Join(t.TempDir(), "config.yaml")
	content := "http_addr: ':7000'\ndb_path: /tmp/from-file.db\nlog_level: warn\n"
	if err := os.WriteFile(path, []byte(content), 0o600); err != nil {
		t.Fatal(err)
	}

	env := map[string]string{EnvConfigPath: path, EnvDBPath: "/tmp/from-env.db"}
	cfg, err := Load(func(k string) string { return env[k] })
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if cfg.HTTPAddr != ":7000" {
		t.Errorf("HTTPAddr = %q, want :7000 (from file)", cfg.HTTPAddr)
	}
	if cfg.DBPath != "/tmp/from-env.db" {
		t.Errorf("DBPath = %q, want /tmp/from-env.db (env beats file)", cfg.DBPath)
	}
	if cfg.LogLevel != "warn" {
		t.Errorf("LogLevel = %q, want warn (from file)", cfg.LogLevel)
	}
}

func TestMissingConfigFileIsNotAnError(t *testing.T) {
	env := map[string]string{EnvConfigPath: filepath.Join(t.TempDir(), "does-not-exist.yaml")}
	cfg, err := Load(func(k string) string { return env[k] })
	if err != nil {
		t.Fatalf("Load() error = %v, want nil for missing file", err)
	}
	if cfg != Defaults() {
		t.Fatalf("Load() = %+v, want defaults", cfg)
	}
}

func TestInvalidLogLevelRejected(t *testing.T) {
	env := map[string]string{EnvLogLevel: "verbose"}
	if _, err := Load(func(k string) string { return env[k] }); err == nil {
		t.Fatal("Load() with invalid log level should fail")
	}
}

func TestValidateEmptyValues(t *testing.T) {
	if err := (Config{DBPath: "x.db", LogLevel: "info"}).Validate(); err == nil {
		t.Error("empty HTTPAddr should fail validation")
	}
	if err := (Config{HTTPAddr: ":8080", LogLevel: "info"}).Validate(); err == nil {
		t.Error("empty DBPath should fail validation")
	}
}
