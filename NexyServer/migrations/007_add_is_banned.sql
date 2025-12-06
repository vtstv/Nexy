-- Add is_banned column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_banned BOOLEAN DEFAULT false;

-- Add index for faster queries
CREATE INDEX IF NOT EXISTS idx_users_is_banned ON users(is_banned);

-- Add banned_at and banned_reason columns for tracking
ALTER TABLE users ADD COLUMN IF NOT EXISTS banned_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS banned_reason TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS banned_by INTEGER REFERENCES users(id);
