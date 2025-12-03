-- Add columns to chats table
ALTER TABLE chats 
ADD COLUMN group_type VARCHAR(20) CHECK (group_type IN ('private_group', 'public_group')),
ADD COLUMN username VARCHAR(50) UNIQUE,
ADD COLUMN description TEXT,
ADD COLUMN default_permissions JSONB DEFAULT '{"send_messages": true, "send_media": true, "add_users": true, "pin_messages": false, "change_info": false}';

-- Add index for username search
CREATE INDEX idx_chats_username ON chats(username);

-- Add permissions to chat_members
ALTER TABLE chat_members
ADD COLUMN permissions JSONB;

-- Update chat_members role to include owner
ALTER TABLE chat_members
DROP CONSTRAINT IF EXISTS chat_members_role_check;

ALTER TABLE chat_members
ADD CONSTRAINT chat_members_role_check CHECK (role IN ('owner', 'admin', 'member'));

-- Create chat_invite_links table
CREATE TABLE IF NOT EXISTS chat_invite_links (
    id SERIAL PRIMARY KEY,
    chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    creator_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code VARCHAR(50) UNIQUE NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP,
    usage_limit INTEGER,
    usage_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_invite_links_code ON chat_invite_links(code);
CREATE INDEX idx_chat_invite_links_chat_id ON chat_invite_links(chat_id);
