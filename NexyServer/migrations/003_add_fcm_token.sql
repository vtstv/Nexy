-- Add FCM token column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS fcm_token VARCHAR(255) DEFAULT '';
