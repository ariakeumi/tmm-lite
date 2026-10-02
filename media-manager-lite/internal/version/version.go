// Package version holds build metadata injected via -ldflags -X at compile
// time (see Makefile / Dockerfile). Development builds default to "dev".
package version

// Build information, overridable with:
//
//	-ldflags "-X media-manager-lite/internal/version.Version=v0.1.0 ..."
var (
	Version   = "dev"
	Commit    = "unknown"
	BuildDate = "unknown"
)
