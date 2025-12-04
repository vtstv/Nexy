package repositories

import (
	"context"
	"database/sql"

	"github.com/vtstv/nexy/internal/models"
)

// SearchMessages searches for messages in a chat
func (r *MessageRepository) SearchMessages(ctx context.Context, chatID int, queryStr string) ([]*models.Message, error) {
	query := `
		SELECT m.id, m.message_id, m.chat_id, m.sender_id, m.message_type, m.content, m.media_url, m.media_type,
			   m.file_size, m.reply_to_id, m.is_edited, m.is_deleted, m.created_at, m.updated_at,
			   COALESCE(
				   (SELECT status FROM message_status ms WHERE ms.message_id = m.id AND ms.user_id != m.sender_id ORDER BY CASE status WHEN 'read' THEN 3 WHEN 'delivered' THEN 2 ELSE 1 END DESC LIMIT 1),
				   'sent'
			   ) as status
		FROM messages m
		WHERE m.chat_id = $1 AND m.is_deleted = false AND m.content ILIKE $2
		ORDER BY m.created_at DESC
		LIMIT 50`

	rows, err := r.db.QueryContext(ctx, query, chatID, "%"+queryStr+"%")
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var messages []*models.Message
	for rows.Next() {
		msg := &models.Message{}
		var replyToID sql.NullInt64
		var fileSize sql.NullInt64
		var mediaURL sql.NullString
		var mediaType sql.NullString
		var status string
		err := rows.Scan(
			&msg.ID,
			&msg.MessageID,
			&msg.ChatID,
			&msg.SenderID,
			&msg.MessageType,
			&msg.Content,
			&mediaURL,
			&mediaType,
			&fileSize,
			&replyToID,
			&msg.IsEdited,
			&msg.IsDeleted,
			&msg.CreatedAt,
			&msg.UpdatedAt,
			&status,
		)
		if err != nil {
			return nil, err
		}
		msg.Status = status
		if mediaURL.Valid {
			msg.MediaURL = mediaURL.String
		}
		if mediaType.Valid {
			msg.MediaType = mediaType.String
		}
		if fileSize.Valid {
			msg.FileSize = &fileSize.Int64
		}
		if replyToID.Valid {
			id := int(replyToID.Int64)
			msg.ReplyToID = &id
		}
		messages = append(messages, msg)
	}

	return messages, rows.Err()
}
