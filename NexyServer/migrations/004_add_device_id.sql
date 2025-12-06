-- Add device_id column to user_sessions table for unique device identification
ALTER TABLE user_sessions ADD COLUMN device_id VARCHAR(255);

-- Create index for faster lookups by device_id
CREATE INDEX idx_user_sessions_device_id ON user_sessions(device_id);

-- Update existing sessions to use IP+port as temporary device_id
UPDATE user_sessions SET device_id = ip_address WHERE device_id IS NULL;
