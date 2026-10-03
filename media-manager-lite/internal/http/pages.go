package http

import "media-manager-lite/internal/library"

// base carries the fields every page template needs.
type base struct {
	Title        string
	Nav          string
	BuildVersion string
}

// librariesData is the data shape of the libraries_panel fragment
// (defined in partials.html); the settings page embeds the same fields.
type librariesData struct {
	base
	Libraries []library.Library
	Error     string
}
