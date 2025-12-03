package repositories

import (
	"context"
	"database/sql"
	"encoding/json"
	"log"

	"github.com/vtstv/nexy/internal/database"
	"github.com/vtstv/nexy/internal/models"
)

type MessageRepository struct {
	db *database.DB
}

func NewMessageRepository(db *database.DB) *MessageRepository {
	return &MessageRepository{db: db}
}

func (r *MessageRepository) Create(ctx context.Context, msg *models.Message) error {
	query := `
		INSERT INTO messages (message_id, chat_id, sender_id, message_type, content, media_url, media_type, file_size, reply_to_id)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
		RETURNING id, created_at, updated_at`

	return r.db.QueryRowContext(ctx, query,
		msg.MessageID,
		msg.ChatID,
		msg.SenderID,
		msg.MessageType,
		msg.Content,
		msg.MediaURL,
		msg.MediaType,
		msg.FileSize,
		msg.ReplyToID,
	).Scan(&msg.ID, &msg.CreatedAt, &msg.UpdatedAt)
}

func (r *MessageRepository) GetByID(ctx context.Context, id int) (*models.Message, error) {
	msg := &models.Message{}
	query := `
		SELECT id, message_id, chat_id, sender_id, message_type, content, media_url, media_type, 
			   file_size, reply_to_id, is_edited, is_deleted, created_at, updated_at
		FROM messages
		WHERE id = $1`

	var replyToID sql.NullInt64
	var fileSize sql.NullInt64
	var mediaURL sql.NullString
	var mediaType sql.NullString
	err := r.db.QueryRowContext(ctx, query, id).Scan(
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
	)
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
	return msg, err
}

func (r *MessageRepository) GetByChatID(ctx context.Context, chatID int, limit, offset int) ([]*models.Message, error) {
	query := `
		SELECT id, message_id, chat_id, sender_id, message_type, content, media_url, media_type,
			   file_size, reply_to_id, is_edited, is_deleted, created_at, updated_at
		FROM messages
		WHERE chat_id = $1 AND is_deleted = false
		ORDER BY created_at DESC
		LIMIT $2 OFFSET $3`

	rows, err := r.db.QueryContext(ctx, query, chatID, limit, offset)
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
		)
		if err != nil {
			return nil, err
		}
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

func (r *MessageRepository) UpdateStatus(ctx context.Context, status *models.MessageStatus) error {
	query := `
		INSERT INTO message_status (message_id, user_id, status)
		VALUES ($1, $2, $3)
		ON CONFLICT (message_id, user_id) DO UPDATE SET status = $3, timestamp = CURRENT_TIMESTAMP`

	_, err := r.db.ExecContext(ctx, query, status.MessageID, status.UserID, status.Status)
	return err
}

// CreateMessageFromWebSocket creates a message from WebSocket data
func (r *MessageRepository) CreateMessageFromWebSocket(ctx context.Context, messageID string, chatID, senderID int, bodyJSON []byte) error {
	var body struct {
		Content     string `json:"content"`
		MessageType string `json:"message_type"`
		MediaURL    string `json:"media_url"`
		MediaType   string `json:"media_type"`
		FileSize    *int64 `json:"file_size"`
		ReplyToID   *int   `json:"reply_to_id"`
	}

	if err := json.Unmarshal(bodyJSON, &body); err != nil {
		log.Printf("Failed to parse message body: %v", err)
		return err
	}

	msg := &models.Message{
		MessageID:   messageID,
		ChatID:      chatID,
		SenderID:    senderID,
		MessageType: body.MessageType,
		Content:     body.Content,
		MediaURL:    body.MediaURL,
		MediaType:   body.MediaType,
		FileSize:    body.FileSize,
		ReplyToID:   body.ReplyToID,
	}

	log.Printf("Creating message: id=%s, chatID=%d, senderID=%d, type=%s, content='%s'",
		messageID, chatID, senderID, body.MessageType, body.Content)

	return r.Create(ctx, msg)
}

func (r *MessageRepository) DeleteMessage(ctx context.Context, messageID string, userID int) error {
	query := `
		UPDATE messages 
		SET is_deleted = true, updated_at = CURRENT_TIMESTAMP
		WHERE message_id = $1 AND sender_id = $2`

	result, err := r.db.ExecContext(ctx, query, messageID, userID)
	if err != nil {
		return err
	}

	rows, err := result.RowsAffected()
	if err != nil {
		return err
	}

	if rows == 0 {
		return sql.ErrNoRows
	}

	return nil
}
