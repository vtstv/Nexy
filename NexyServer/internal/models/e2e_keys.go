package models

import "time"

// PreKey represents a one-time prekey for X3DH key exchange
type PreKey struct {
	ID        int       `json:"id"`
	UserID    int       `json:"user_id"`
	KeyID     int       `json:"key_id"`
	PublicKey string    `json:"public_key"` // Base64 encoded
	Used      bool      `json:"used"`
	CreatedAt time.Time `json:"created_at"`
}

// IdentityKey represents a long-term identity key for a user
type IdentityKey struct {
	ID        int       `json:"id"`
	UserID    int       `json:"user_id"`
	PublicKey string    `json:"public_key"` // Base64 encoded
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

// SignedPreKey represents a signed prekey for X3DH
type SignedPreKey struct {
	ID        int       `json:"id"`
	UserID    int       `json:"user_id"`
	KeyID     int       `json:"key_id"`
	PublicKey string    `json:"public_key"` // Base64 encoded
	Signature string    `json:"signature"`  // Base64 encoded
	CreatedAt time.Time `json:"created_at"`
}

// KeyBundleSignedPreKey represents signed prekey in the bundle
type KeyBundleSignedPreKey struct {
	KeyID     int    `json:"key_id"`
	PublicKey string `json:"public_key"`
	Signature string `json:"signature"`
}

// KeyBundlePreKey represents one-time prekey in the bundle
type KeyBundlePreKey struct {
	KeyID     int    `json:"key_id"`
	PublicKey string `json:"public_key"`
}

// KeyBundle contains all public keys needed to establish a session
type KeyBundle struct {
	UserID       int                    `json:"user_id"`
	IdentityKey  string                 `json:"identity_key"`
	SignedPreKey *KeyBundleSignedPreKey `json:"signed_pre_key"`
	PreKey       *KeyBundlePreKey       `json:"pre_key,omitempty"`
	DeviceID     int                    `json:"device_id"` // For multi-device support
}
