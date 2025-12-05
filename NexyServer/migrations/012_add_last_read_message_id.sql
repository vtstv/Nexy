-- Add last_read_message_id to track read position per user per chat
ALTER TABLE chat_members ADD COLUMN IF NOT EXISTS last_read_message_id INTEGER DEFAULT 0;

-- Create index for efficient unread count queries
CREATE INDEX IF NOT EXISTS idx_chat_members_last_read ON chat_members(chat_id, user_id, last_read_message_id);

-- Initialize last_read_message_id from existing message_status data
-- This sets it to the max message_id that has status='read' for each user in each chat
UPDATE chat_members cm
SET last_read_message_id = COALESCE(
    (SELECT MAX(m.id) 
     FROM messages m 
     INNER JOIN message_status ms ON ms.message_id = m.id 
     WHERE m.chat_id = cm.chat_id 
       AND ms.user_id = cm.user_id 
       AND ms.status = 'read'),
    0
);
