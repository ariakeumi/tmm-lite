// Package migrations embeds the SQL migration files applied at startup.
package migrations

import "embed"

// FS holds all *.sql migration files in this directory. The migration runner
// applies them in lexicographic filename order.
//
//go:embed *.sql
var FS embed.FS
