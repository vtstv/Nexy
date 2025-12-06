-- Add group bans table
CREATE TABLE IF NOT EXISTS group_bans (
    id SERIAL PRIMARY KEY,
    chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    banned_by INTEGER NOT NULL REFERENCES users(id) ON DELETE SET NULL,
    reason TEXT,
    banned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(chat_id, user_id)
);

CREATE INDEX idx_group_bans_chat_id ON group_bans(chat_id);
CREATE INDEX idx_group_bans_user_id ON group_bans(user_id);
