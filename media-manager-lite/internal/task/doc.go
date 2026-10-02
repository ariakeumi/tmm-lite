// Package task is reserved for the background task queue (scan, scrape,
// artwork, NFO, rename) that arrives in a later milestone. The tasks table
// and its state machine (pending → running → completed | failed) already
// exist in the Milestone-1 schema; this package will add the in-process
// worker pool and task persistence on top of it.
package task
