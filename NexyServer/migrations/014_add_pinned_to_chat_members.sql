-- Add pin support to chat_members
ALTER TABLE chat_members ADD COLUMN IF NOT EXISTS is_pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_members ADD COLUMN IF NOT EXISTS pinned_at TIMESTAMP;

-- Index for efficiently ordering pinned chats
CREATE INDEX IF NOT EXISTS idx_chat_members_pinned ON chat_members(user_id, is_pinned, pinned_at DESC);
