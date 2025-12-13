-- Add phone number and privacy settings for phone discovery
-- Migration: 009_add_phone_and_privacy.sql

-- Add phone number field to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_users_phone_number ON users(phone_number);

-- Add privacy settings for phone
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_privacy VARCHAR(20) DEFAULT 'contacts';
-- phone_privacy values:
-- 'everyone' - anyone can find user by phone
-- 'contacts' - only contacts can see phone  
-- 'nobody' - phone is completely hidden

ALTER TABLE users ADD COLUMN IF NOT EXISTS allow_phone_discovery BOOLEAN DEFAULT TRUE;
-- allow_phone_discovery - can others search and find this user by phone number

-- Add sync contacts permission table (for storing phone-to-user mappings)
CREATE TABLE IF NOT EXISTS synced_contacts (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    phone_number VARCHAR(20) NOT NULL,
    contact_name VARCHAR(100),
    matched_user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, phone_number)
);

CREATE INDEX IF NOT EXISTS idx_synced_contacts_user_id ON synced_contacts(user_id);
CREATE INDEX IF NOT EXISTS idx_synced_contacts_phone_number ON synced_contacts(phone_number);
CREATE INDEX IF NOT EXISTS idx_synced_contacts_matched_user_id ON synced_contacts(matched_user_id);
