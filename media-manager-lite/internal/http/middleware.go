package http

import (
	"log/slog"
	"net/http"
	"runtime/debug"
	"time"
)

// statusWriter remembers the status code for logging and recovery.
type statusWriter struct {
	http.ResponseWriter
	status int
}

func (w *statusWriter) WriteHeader(code int) {
	if w.status == 0 {
		w.status = code
	}
	w.ResponseWriter.WriteHeader(code)
}

func (w *statusWriter) Write(b []byte) (int, error) {
	if w.status == 0 {
		w.status = http.StatusOK
	}
	return w.ResponseWriter.Write(b)
}

// withRecover converts handler panics into JSON 500 responses.
func withRecover(log *slog.Logger, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			if rec := recover(); rec != nil {
				log.Error("panic recovered",
					"method", r.Method, "path", r.URL.Path,
					"panic", rec, "stack", string(debug.Stack()),
				)
				sw, ok := w.(*statusWriter)
				if ok && sw.status == 0 {
					writeAPIError(w, http.StatusInternalServerError, "internal server error")
				}
			}
		}()
		next.ServeHTTP(w, r)
	})
}

// withLogging emits one structured log line per request. Request bodies are
// never logged.
func withLogging(log *slog.Logger, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		sw := &statusWriter{ResponseWriter: w}
		next.ServeHTTP(sw, r)

		status := sw.status
		if status == 0 {
			status = http.StatusOK
		}
		log.LogAttrs(r.Context(), slog.LevelInfo, "http request",
			slog.String("method", r.Method),
			slog.String("path", r.URL.Path),
			slog.Int("status", status),
			slog.Int64("duration_ms", time.Since(start).Milliseconds()),
		)
	})
}
