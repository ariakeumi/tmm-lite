// Package config loads the application configuration. Precedence:
// built-in defaults < optional YAML file < environment variables.
//
// A missing config file is not an error (defaults are used); an unreadable or
// malformed one is. The struct is intentionally minimal — a full settings
// framework (TMDB keys, renamer profiles, …) arrives with later milestones
// and lives in the database, not here.
package config

import (
	"errors"
	"fmt"
	"io/fs"
	"log/slog"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
)

// Environment variable names.
const (
	EnvConfigPath  = "MEDIA_MANAGER_CONFIG"
	EnvHTTPAddr    = "MEDIA_MANAGER_HTTP_ADDR"
	EnvDBPath      = "MEDIA_MANAGER_DB"
	EnvLogLevel    = "MEDIA_MANAGER_LOG_LEVEL"
	EnvTMDBAPIKey  = "MEDIA_MANAGER_TMDB_API_KEY"
	EnvTMDBBaseURL = "MEDIA_MANAGER_TMDB_BASE_URL"
)

// Config holds the runtime configuration.
type Config struct {
	HTTPAddr string `yaml:"http_addr"`
	DBPath   string `yaml:"db_path"`
	LogLevel string `yaml:"log_level"`
	// TMDBAPIKey is a bootstrap fallback; the settings-table value wins.
	// It is never logged and never returned by the API.
	TMDBAPIKey string `yaml:"tmdb_api_key"`
	// TMDBBaseURL overrides the TMDB API endpoint (tests, self-hosted proxies).
	TMDBBaseURL string `yaml:"tmdb_base_url"`
}

// Defaults returns the built-in configuration used when nothing is set.
func Defaults() Config {
	return Config{
		HTTPAddr:    ":8080",
		DBPath:      filepath.Join("data", "media-manager.db"),
		LogLevel:    "info",
		TMDBBaseURL: "https://api.themoviedb.org/3",
	}
}

// Load builds the configuration from defaults, the YAML file named by
// MEDIA_MANAGER_CONFIG (when it exists) and environment overrides. getenv is
// injectable for tests; production callers pass os.Getenv.
func Load(getenv func(string) string) (Config, error) {
	cfg := Defaults()

	if p := getenv(EnvConfigPath); p != "" {
		if err := applyFile(&cfg, p); err != nil {
			return Config{}, err
		}
	}

	if v := getenv(EnvHTTPAddr); v != "" {
		cfg.HTTPAddr = v
	}
	if v := getenv(EnvDBPath); v != "" {
		cfg.DBPath = v
	}
	if v := getenv(EnvLogLevel); v != "" {
		cfg.LogLevel = v
	}
	if v := getenv(EnvTMDBAPIKey); v != "" {
		cfg.TMDBAPIKey = v
	}
	if v := getenv(EnvTMDBBaseURL); v != "" {
		cfg.TMDBBaseURL = v
	}

	cfg.HTTPAddr = strings.TrimSpace(cfg.HTTPAddr)
	cfg.DBPath = filepath.Clean(cfg.DBPath)
	cfg.LogLevel = strings.ToLower(strings.TrimSpace(cfg.LogLevel))

	if err := cfg.Validate(); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

func applyFile(cfg *Config, path string) error {
	data, err := os.ReadFile(path)
	if err != nil {
		if errors.Is(err, fs.ErrNotExist) {
			// Missing config file: fall back to defaults silently.
			return nil
		}
		return fmt.Errorf("config: read %s: %w", path, err)
	}
	if err := yaml.Unmarshal(data, cfg); err != nil {
		return fmt.Errorf("config: parse %s: %w", path, err)
	}
	return nil
}

// Validate rejects structurally invalid configurations early.
func (c Config) Validate() error {
	if c.HTTPAddr == "" {
		return errors.New("config: http address must not be empty")
	}
	if c.DBPath == "" {
		return errors.New("config: database path must not be empty")
	}
	switch c.LogLevel {
	case "debug", "info", "warn", "error":
	default:
		return fmt.Errorf("config: invalid log level %q (want debug, info, warn or error)", c.LogLevel)
	}
	return nil
}

// SlogLevel converts the configured level string into a slog.Level.
func (c Config) SlogLevel() slog.Level {
	switch c.LogLevel {
	case "debug":
		return slog.LevelDebug
	case "warn":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return slog.LevelInfo
	}
}
