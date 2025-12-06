-- Consolidated database schema
-- Combines all migrations (001-015) into single initialization script

-- ============================================
-- CORE TABLES
-- ============================================

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    bio TEXT,
    read_receipts_enabled BOOLEAN DEFAULT TRUE,
    typing_indicators_enabled BOOLEAN DEFAULT TRUE,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    show_online_status BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_last_seen ON users(last_seen);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(500) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);

CREATE TABLE IF NOT EXISTS user_sessions (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_id INTEGER REFERENCES refresh_tokens(id) ON DELETE CASCADE,
    device_name VARCHAR(255),
    device_type VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent TEXT,
    last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_current BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_refresh_token_id ON user_sessions(refresh_token_id);

CREATE TABLE IF NOT EXISTS invite_links (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    creator_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    max_uses INTEGER DEFAULT 1,
    uses_count INTEGER DEFAULT 0,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_invite_links_code ON invite_links(code);
CREATE INDEX idx_invite_links_creator_id ON invite_links(creator_id);

-- ============================================
-- CHATS AND GROUPS
-- ============================================

CREATE TABLE IF NOT EXISTS chats (
    id SERIAL PRIMARY KEY,
    type VARCHAR(20) NOT NULL CHECK (type IN ('private', 'group', 'notepad')),
    name VARCHAR(100),
    avatar_url VARCHAR(500),
    group_type VARCHAR(20) CHECK (group_type IN ('private_group', 'public_group')),
    username VARCHAR(50) UNIQUE,
    description TEXT,
    default_permissions JSONB DEFAULT '{"send_messages": true, "send_media": true, "add_users": true, "pin_messages": false, "change_info": false}',
    created_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chats_type ON chats(type);
CREATE INDEX idx_chats_username ON chats(username);

CREATE TABLE IF NOT EXISTS chat_members (
    id SERIAL PRIMARY KEY,
    chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) DEFAULT 'member' CHECK (role IN ('owner', 'admin', 'member')),
    permissions JSONB,
    muted_until TIMESTAMP,
    last_read_message_id INTEGER DEFAULT 0,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    pinned_at TIMESTAMP,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(chat_id, user_id)
);

CREATE INDEX idx_chat_members_chat_id ON chat_members(chat_id);
CREATE INDEX idx_chat_members_user_id ON chat_members(user_id);
CREATE INDEX idx_chat_members_last_read ON chat_members(chat_id, user_id, last_read_message_id);
CREATE INDEX idx_chat_members_pinned ON chat_members(user_id, is_pinned, pinned_at DESC);

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

-- ============================================
-- MESSAGES
-- ============================================

CREATE TABLE IF NOT EXISTS messages (
    id SERIAL PRIMARY KEY,
    message_id VARCHAR(100) UNIQUE NOT NULL,
    chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_type VARCHAR(20) NOT NULL CHECK (message_type IN ('text', 'media', 'file', 'system')),
    content TEXT,
    media_url VARCHAR(500),
    media_type VARCHAR(50),
    file_size BIGINT,
    reply_to_id INTEGER REFERENCES messages(id) ON DELETE SET NULL,
    is_edited BOOLEAN DEFAULT FALSE,
    is_deleted BOOLEAN DEFAULT FALSE,
    encrypted BOOLEAN DEFAULT FALSE,
    encryption_algorithm VARCHAR(50),
    sender_ratchet_key TEXT,
    pts INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_chat_id ON messages(chat_id);
CREATE INDEX idx_messages_sender_id ON messages(sender_id);
CREATE INDEX idx_messages_message_id ON messages(message_id);
CREATE INDEX idx_messages_created_at ON messages(created_at DESC);

CREATE TABLE IF NOT EXISTS message_status (
    id SERIAL PRIMARY KEY,
    message_id INTEGER NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('sent', 'delivered', 'read')),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(message_id, user_id)
);

CREATE INDEX idx_message_status_message_id ON message_status(message_id);
CREATE INDEX idx_message_status_user_id ON message_status(user_id);

-- ============================================
-- FILES
-- ============================================

CREATE TABLE IF NOT EXISTS files (
    id SERIAL PRIMARY KEY,
    file_id VARCHAR(100) UNIQUE NOT NULL,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_type VARCHAR(20) DEFAULT 'local' CHECK (storage_type IN ('local', 's3')),
    storage_path VARCHAR(500) NOT NULL,
    url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_files_file_id ON files(file_id);
CREATE INDEX idx_files_user_id ON files(user_id);

-- ============================================
-- END-TO-END ENCRYPTION (E2E)
-- ============================================

CREATE TABLE IF NOT EXISTS identity_keys (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    public_key TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id)
);

CREATE INDEX idx_identity_keys_user_id ON identity_keys(user_id);

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

-- ============================================
-- CONTACTS
-- ============================================

CREATE TABLE IF NOT EXISTS contacts (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    contact_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'accepted',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, contact_user_id),
    CHECK (user_id != contact_user_id)
);

CREATE INDEX idx_contacts_user_id ON contacts(user_id);
CREATE INDEX idx_contacts_contact_user_id ON contacts(contact_user_id);
CREATE INDEX idx_contacts_status ON contacts(status);

-- ============================================
-- CHAT FOLDERS
-- ============================================

CREATE TABLE IF NOT EXISTS chat_folders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    icon VARCHAR(50) DEFAULT 'folder',
    color VARCHAR(20) DEFAULT 'default',
    position INTEGER DEFAULT 0,
    include_contacts BOOLEAN DEFAULT FALSE,
    include_non_contacts BOOLEAN DEFAULT FALSE,
    include_groups BOOLEAN DEFAULT FALSE,
    include_channels BOOLEAN DEFAULT FALSE,
    include_bots BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_folder_included (
    id SERIAL PRIMARY KEY,
    folder_id INTEGER NOT NULL REFERENCES chat_folders(id) ON DELETE CASCADE,
    chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    UNIQUE(folder_id, chat_id)
);

CREATE TABLE IF NOT EXISTS chat_folder_excluded (
    id SERIAL PRIMARY KEY,
    folder_id INTEGER NOT NULL REFERENCES chat_folders(id) ON DELETE CASCADE,
    chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    UNIQUE(folder_id, chat_id)
);

CREATE INDEX idx_chat_folders_user_id ON chat_folders(user_id);
CREATE INDEX idx_chat_folder_included_folder_id ON chat_folder_included(folder_id);
CREATE INDEX idx_chat_folder_excluded_folder_id ON chat_folder_excluded(folder_id);

-- ============================================
-- SYNCHRONIZATION (PTS)
-- ============================================

CREATE TABLE IF NOT EXISTS user_sync_state (
    user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    pts INTEGER NOT NULL DEFAULT 0,
    date TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS channel_sync_state (
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    pts INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, chat_id)
);

CREATE TABLE IF NOT EXISTS updates_log (
    id SERIAL PRIMARY KEY,
    pts INTEGER NOT NULL,
    chat_id INTEGER REFERENCES chats(id) ON DELETE CASCADE,
    update_type VARCHAR(50) NOT NULL,
    update_data JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_updates_log_pts ON updates_log(pts);
CREATE INDEX idx_updates_log_chat_pts ON updates_log(chat_id, pts);
CREATE INDEX idx_updates_log_created ON updates_log(created_at);

CREATE SEQUENCE IF NOT EXISTS common_pts_seq START WITH 1 INCREMENT BY 1;

-- ============================================
-- TRIGGERS AND FUNCTIONS
-- ============================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_chats_updated_at BEFORE UPDATE ON chats
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_messages_updated_at BEFORE UPDATE ON messages
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE FUNCTION cleanup_old_updates() RETURNS void AS $$
BEGIN
    DELETE FROM updates_log WHERE created_at < NOW() - INTERVAL '7 days';
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION get_next_pts() RETURNS INTEGER AS $$
DECLARE
    next_val INTEGER;
BEGIN
    SELECT nextval('common_pts_seq') INTO next_val;
    RETURN next_val;
END;
$$ LANGUAGE plpgsql;

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

-- ============================================
-- COMMENTS (DOCUMENTATION)
-- ============================================

COMMENT ON TABLE identity_keys IS 'Long-term identity keys for E2E encryption (X3DH protocol)';
COMMENT ON TABLE signed_pre_keys IS 'Signed prekeys for initial key exchange';
COMMENT ON TABLE pre_keys IS 'One-time prekeys consumed during session establishment';
COMMENT ON COLUMN messages.encrypted IS 'Whether message content is end-to-end encrypted';
