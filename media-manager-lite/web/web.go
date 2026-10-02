// Package web embeds the server-rendered UI assets: HTML templates and
// static files. Embedding keeps the distroless image a single binary with
// no external data files.
package web

import (
	"embed"
	"io/fs"
)

//go:embed templates static
var content embed.FS

// Pages returns the embedded HTML templates directory.
func Pages() fs.FS {
	sub, err := fs.Sub(content, "templates")
	if err != nil {
		// Cannot happen for a build-time verified embed path.
		panic(err)
	}
	return sub
}

// Static returns the embedded static assets directory.
func Static() fs.FS {
	sub, err := fs.Sub(content, "static")
	if err != nil {
		panic(err)
	}
	return sub
}
