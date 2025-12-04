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
func (r *MessageRepository) MarkMessagesAsRead(ctx context.Context, chatID, userID, lastMessageID int) error {
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

	_, err := r.db.ExecContext(ctx, query, chatID, userID, lastMessageID)
	return err
}
