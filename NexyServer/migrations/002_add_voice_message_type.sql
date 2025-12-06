-- Add voice message type to messages table check constraint
ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_message_type_check;
ALTER TABLE messages ADD CONSTRAINT messages_message_type_check CHECK (message_type IN ('text', 'media', 'file', 'system', 'voice'));
