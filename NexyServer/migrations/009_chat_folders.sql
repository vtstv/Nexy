-- Chat Folders
CREATE TABLE IF NOT EXISTS chat_folders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    icon VARCHAR(50) DEFAULT 'folder',
    color VARCHAR(20) DEFAULT 'default',
    position INTEGER DEFAULT 0,
    
    -- Filter options (types of chats to include)
    include_contacts BOOLEAN DEFAULT FALSE,
    include_non_contacts BOOLEAN DEFAULT FALSE,
    include_groups BOOLEAN DEFAULT FALSE,
    include_channels BOOLEAN DEFAULT FALSE,
    include_bots BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Individual chats included in folder
CREATE TABLE IF NOT EXISTS chat_folder_included (
    id SERIAL PRIMARY KEY,
    folder_id INTEGER NOT NULL REFERENCES chat_folders(id) ON DELETE CASCADE,
    chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    UNIQUE(folder_id, chat_id)
);

-- Individual chats excluded from folder
CREATE TABLE IF NOT EXISTS chat_folder_excluded (
    id SERIAL PRIMARY KEY,
    folder_id INTEGER NOT NULL REFERENCES chat_folders(id) ON DELETE CASCADE,
    chat_id INTEGER NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    UNIQUE(folder_id, chat_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_chat_folders_user_id ON chat_folders(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_folder_included_folder_id ON chat_folder_included(folder_id);
CREATE INDEX IF NOT EXISTS idx_chat_folder_excluded_folder_id ON chat_folder_excluded(folder_id);
