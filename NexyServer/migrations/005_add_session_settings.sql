-- Add session settings columns for per-device control
ALTER TABLE user_sessions ADD COLUMN accept_secret_chats BOOLEAN DEFAULT TRUE;
ALTER TABLE user_sessions ADD COLUMN accept_calls BOOLEAN DEFAULT TRUE;
