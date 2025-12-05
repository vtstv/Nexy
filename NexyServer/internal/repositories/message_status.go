package repositories

import (
	"context"

	"github.com/vtstv/nexy/internal/models"
)

// UpdateStatus updates or creates a message status
func (r *MessageRepository) UpdateStatus(ctx context.Context, status *models.MessageStatus) error {
	query := `
		INSERT INTO message_status (message_id, user_id, status)
		VALUES ($1, $2, $3)
		ON CONFLICT (message_id, user_id) DO UPDATE SET status = $3, timestamp = CURRENT_TIMESTAMP`

	_, err := r.db.ExecContext(ctx, query, status.MessageID, status.UserID, status.Status)
	return err
}

// MarkMessagesAsRead marks all messages up to a certain point as read
// Also updates last_read_message_id in chat_members
func (r *MessageRepository) MarkMessagesAsRead(ctx context.Context, chatID, userID, lastMessageID int) error {
	// Update message_status for backward compatibility
	query := `
		INSERT INTO message_status (message_id, user_id, status)
		SELECT m.id, $2, 'read'
		FROM messages m
		WHERE m.chat_id = $1 
		  AND m.id <= $3 
		  AND m.sender_id != $2
		ON CONFLICT (message_id, user_id) 
		DO UPDATE SET status = 'read', timestamp = CURRENT_TIMESTAMP
		WHERE message_status.status != 'read'`

	if _, err := r.db.ExecContext(ctx, query, chatID, userID, lastMessageID); err != nil {
		return err
	}

	// Update last_read_message_id in chat_members (main source of truth)
	updateQuery := `
		UPDATE chat_members 
		SET last_read_message_id = GREATEST(COALESCE(last_read_message_id, 0), $3)
		WHERE chat_id = $1 AND user_id = $2`

	_, err := r.db.ExecContext(ctx, updateQuery, chatID, userID, lastMessageID)
	return err
}

// GetUnreadCount returns the number of unread messages for a user in a chat
// Uses last_read_message_id from chat_members
func (r *MessageRepository) GetUnreadCount(ctx context.Context, chatID, userID int) (int, error) {
	query := `
		SELECT COUNT(*)
		FROM messages m
		INNER JOIN chat_members cm ON cm.chat_id = m.chat_id AND cm.user_id = $2
		WHERE m.chat_id = $1
		AND m.sender_id != $2
		AND m.is_deleted = FALSE
		AND m.id > COALESCE(cm.last_read_message_id, 0)`

	var count int
	err := r.db.QueryRowContext(ctx, query, chatID, userID).Scan(&count)
	return count, err
}

// GetFirstUnreadMessageId returns the UUID of the first unread message for a user in a chat
func (r *MessageRepository) GetFirstUnreadMessageId(ctx context.Context, chatID, userID int) (string, error) {
	query := `
		SELECT m.message_id
		FROM messages m
		INNER JOIN chat_members cm ON cm.chat_id = m.chat_id AND cm.user_id = $2
		WHERE m.chat_id = $1
		AND m.sender_id != $2
		AND m.is_deleted = FALSE
		AND m.id > COALESCE(cm.last_read_message_id, 0)
		ORDER BY m.id ASC
		LIMIT 1`

	var messageId string
	err := r.db.QueryRowContext(ctx, query, chatID, userID).Scan(&messageId)
	if err != nil {
		return "", err
	}
	return messageId, nil
}
