-- Identity keys (long-term identity key per user)
CREATE TABLE IF NOT EXISTS identity_keys (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    public_key TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id)
);

CREATE INDEX idx_identity_keys_user_id ON identity_keys(user_id);

-- Signed prekeys (medium-term keys, rotated periodically)
CREATE TABLE IF NOT EXISTS signed_pre_keys (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_id INTEGER NOT NULL,
    public_key TEXT NOT NULL,
    signature TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, key_id)
);

CREATE INDEX idx_signed_pre_keys_user_id ON signed_pre_keys(user_id);

-- One-time prekeys (consumed after use)
CREATE TABLE IF NOT EXISTS pre_keys (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_id INTEGER NOT NULL,
    public_key TEXT NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, key_id)
);

CREATE INDEX idx_pre_keys_user_id ON pre_keys(user_id);
CREATE INDEX idx_pre_keys_used ON pre_keys(user_id, used);

-- Add encryption metadata to messages table
ALTER TABLE messages 
ADD COLUMN IF NOT EXISTS encrypted BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS encryption_algorithm VARCHAR(50),
ADD COLUMN IF NOT EXISTS sender_ratchet_key TEXT;

-- Comments
COMMENT ON TABLE identity_keys IS 'Long-term identity keys for E2E encryption (X3DH protocol)';
COMMENT ON TABLE signed_pre_keys IS 'Signed prekeys for initial key exchange';
COMMENT ON TABLE pre_keys IS 'One-time prekeys consumed during session establishment';
COMMENT ON COLUMN messages.encrypted IS 'Whether message content is end-to-end encrypted';
COMMENT ON COLUMN messages.encryption_algorithm IS 'Encryption algorithm used (e.g., AES-256-GCM)';
COMMENT ON COLUMN messages.sender_ratchet_key IS 'Sender ratchet public key for Double Ratchet algorithm';
