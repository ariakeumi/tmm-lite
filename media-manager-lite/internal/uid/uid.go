// Package uid generates RFC 4122 version 4 UUID strings using crypto/rand.
// Kept dependency-free: the format is fixed and tiny.
package uid

import (
	"crypto/rand"
	"encoding/hex"
)

// New returns a random UUIDv4 in the canonical 8-4-4-4-12 hexadecimal form.
func New() string {
	var b [16]byte
	// crypto/rand.Read is documented to never return an error and always
	// fill the buffer entirely.
	rand.Read(b[:])
	b[6] = (b[6] & 0x0f) | 0x40 // version 4
	b[8] = (b[8] & 0x3f) | 0x80 // variant 10xx

	dst := make([]byte, 36)
	hex.Encode(dst[0:8], b[0:4])
	dst[8] = '-'
	hex.Encode(dst[9:13], b[4:6])
	dst[13] = '-'
	hex.Encode(dst[14:18], b[6:8])
	dst[18] = '-'
	hex.Encode(dst[19:23], b[8:10])
	dst[23] = '-'
	hex.Encode(dst[24:36], b[10:16])
	return string(dst)
}
