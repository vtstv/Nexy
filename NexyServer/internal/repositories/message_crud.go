package repositories

import (
	"context"
	"database/sql"
	"encoding/json"
	"log"

	"github.com/vtstv/nexy/internal/models"
)

// Create creates a new message
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

// GetByID retrieves a message by its database ID
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

// GetByUUID retrieves a message by its UUID
func (r *MessageRepository) GetByUUID(ctx context.Context, uuid string) (*models.Message, error) {
	msg := &models.Message{}
	query := `
		SELECT id, message_id, chat_id, sender_id, message_type, content, media_url, media_type, 
			   file_size, reply_to_id, is_edited, is_deleted, created_at, updated_at
		FROM messages
		WHERE message_id = $1`

	var replyToID sql.NullInt64
	var fileSize sql.NullInt64
	var mediaURL sql.NullString
	var mediaType sql.NullString
	err := r.db.QueryRowContext(ctx, query, uuid).Scan(
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
	return msg, nil
}

// GetByChatID retrieves messages for a chat with pagination
func (r *MessageRepository) GetByChatID(ctx context.Context, chatID int, limit, offset int) ([]*models.Message, error) {
	query := `
		SELECT m.id, m.message_id, m.chat_id, m.sender_id, m.message_type, m.content, m.media_url, m.media_type,
			   m.file_size, m.reply_to_id, m.is_edited, m.is_deleted, m.created_at, m.updated_at,
			   COALESCE(
				   (SELECT status FROM message_status ms WHERE ms.message_id = m.id AND ms.user_id != m.sender_id ORDER BY CASE status WHEN 'read' THEN 3 WHEN 'delivered' THEN 2 ELSE 1 END DESC LIMIT 1),
				   'sent'
			   ) as status
		FROM messages m
		WHERE m.chat_id = $1
		ORDER BY m.created_at DESC
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

		// Clear content for deleted messages to protect privacy
		if msg.IsDeleted {
			msg.Content = ""
			msg.MediaURL = ""
			msg.MediaType = ""
			msg.FileSize = nil
		}

		msg.Status = status
		if mediaURL.Valid && !msg.IsDeleted {
			msg.MediaURL = mediaURL.String
		}
		if mediaType.Valid && !msg.IsDeleted {
			msg.MediaType = mediaType.String
		}
		if fileSize.Valid && !msg.IsDeleted {
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

// Update updates an existing message
func (r *MessageRepository) Update(ctx context.Context, msg *models.Message) error {
	query := `
		UPDATE messages 
		SET content = $1, is_edited = $2, updated_at = NOW()
		WHERE message_id = $3 AND sender_id = $4
		RETURNING updated_at`

	return r.db.QueryRowContext(ctx, query,
		msg.Content,
		true,
		msg.MessageID,
		msg.SenderID,
	).Scan(&msg.UpdatedAt)
}

// DeleteMessage soft-deletes a message
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
