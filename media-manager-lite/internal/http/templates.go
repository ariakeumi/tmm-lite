package http

import (
	"fmt"
	"html/template"
	"net/http"
	"time"

	"media-manager-lite/web"
)

var templateFuncs = template.FuncMap{
	"prettyDate": func(s string) string {
		t, err := time.Parse(time.RFC3339, s)
		if err != nil {
			return s
		}
		return t.Format("2006-01-02 15:04")
	},
	"truncate": func(s string, n int) string {
		r := []rune(s)
		if len(r) <= n {
			return s
		}
		return string(r[:n]) + "…"
	},
}

// templates holds one parsed template set per page. Each set is
// layout.html + the page file; the libraries set additionally provides the
// "libraries_panel" fragment used as the HTMX swap target.
type templates struct {
	pages map[string]*template.Template
}

func newTemplates() (*templates, error) {
	t := &templates{pages: map[string]*template.Template{}}
	for _, name := range []string{"dashboard", "libraries", "settings", "match", "movie", "movies", "tv", "tvshow"} {
		set, err := template.New("layout.html").Funcs(templateFuncs).
			ParseFS(web.Pages(), "layout.html", name+".html")
		if err != nil {
			return nil, fmt.Errorf("parse template %s: %w", name, err)
		}
		t.pages[name] = set
	}
	return t, nil
}

// renderPage writes a full HTML page through the layout.
func (s *Server) renderPage(w http.ResponseWriter, status int, name string, data any) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.tpl.pages[name].ExecuteTemplate(w, "layout", data); err != nil {
		s.log.Error("render page", "page", name, "error", err)
	}
}

// renderLibrariesPanel writes the libraries panel fragment (table + error
// area) used as the HTMX swap target for create/delete actions.
func (s *Server) renderLibrariesPanel(w http.ResponseWriter, status int, data librariesData) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(status)
	if err := s.tpl.pages["libraries"].ExecuteTemplate(w, "libraries_panel", data); err != nil {
		s.log.Error("render libraries panel", "error", err)
	}
}
