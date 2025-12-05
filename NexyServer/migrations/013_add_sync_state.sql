-- Migration 013: Add pts synchronization
-- Each user has their own pts (points) sequence for tracking updates

-- User sync state: tracks what updates each user has received
CREATE TABLE IF NOT EXISTS user_sync_state (
    user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    pts INTEGER NOT NULL DEFAULT 0,           -- Last processed common pts (for private chats/groups)
    date TIMESTAMP NOT NULL DEFAULT NOW(),    -- Date of last update
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Channel/supergroup sync state per user
CREATE TABLE IF NOT EXISTS channel_sync_state (
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    pts INTEGER NOT NULL DEFAULT 0,           -- Last processed pts for this channel
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, chat_id)
);

-- Global update sequence for all events
-- Each event (message, edit, delete, etc) gets a unique pts
ALTER TABLE messages ADD COLUMN IF NOT EXISTS pts INTEGER;

-- Create sequence for common pts (private chats, basic groups)
CREATE SEQUENCE IF NOT EXISTS common_pts_seq START WITH 1 INCREMENT BY 1;

-- Create sequence per chat for channel pts (will be managed differently)
-- For channels/supergroups, pts is per-channel

-- Updates log for getDifference API
-- Stores recent updates for gap recovery
CREATE TABLE IF NOT EXISTS updates_log (
    id SERIAL PRIMARY KEY,
    pts INTEGER NOT NULL,
    chat_id INTEGER REFERENCES chats(id) ON DELETE CASCADE,
    update_type VARCHAR(50) NOT NULL,  -- 'new_message', 'edit_message', 'delete_message', 'read'
    update_data JSONB NOT NULL,        -- The actual update payload
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index for efficient getDifference queries
CREATE INDEX IF NOT EXISTS idx_updates_log_pts ON updates_log(pts);
CREATE INDEX IF NOT EXISTS idx_updates_log_chat_pts ON updates_log(chat_id, pts);
CREATE INDEX IF NOT EXISTS idx_updates_log_created ON updates_log(created_at);

-- Cleanup old updates (keep last 7 days)
-- This will be called periodically
CREATE OR REPLACE FUNCTION cleanup_old_updates() RETURNS void AS $$
BEGIN
    DELETE FROM updates_log WHERE created_at < NOW() - INTERVAL '7 days';
END;
$$ LANGUAGE plpgsql;

-- Function to get next common pts
CREATE OR REPLACE FUNCTION get_next_pts() RETURNS INTEGER AS $$
DECLARE
    next_val INTEGER;
BEGIN
    SELECT nextval('common_pts_seq') INTO next_val;
    RETURN next_val;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-assign pts to new messages
CREATE OR REPLACE FUNCTION assign_message_pts() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.pts IS NULL THEN
        NEW.pts := get_next_pts();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS message_pts_trigger ON messages;
CREATE TRIGGER message_pts_trigger
    BEFORE INSERT ON messages
    FOR EACH ROW
    EXECUTE FUNCTION assign_message_pts();

-- Initialize pts for existing messages
UPDATE messages SET pts = id WHERE pts IS NULL;

-- Set sequence to continue from max existing pts
SELECT setval('common_pts_seq', COALESCE((SELECT MAX(pts) FROM messages), 0) + 1);
