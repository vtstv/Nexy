package repositories

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/vtstv/nexy-admin/internal/models"
)

type MessageRepository struct {
	db *sql.DB
}

func NewMessageRepository(db *sql.DB) *MessageRepository {
	return &MessageRepository{db: db}
}

func (r *MessageRepository) GetAll(ctx context.Context, params models.PaginationParams) ([]models.Message, int, error) {
	countQuery := "SELECT COUNT(*) FROM messages WHERE is_deleted = false"
	args := []interface{}{}
	argPos := 1

	if params.Search != "" {
		countQuery += fmt.Sprintf(" AND content ILIKE $%d", argPos)
		searchPattern := "%" + params.Search + "%"
		args = append(args, searchPattern)
		argPos++
	}

	var totalCount int
	err := r.db.QueryRowContext(ctx, countQuery, args...).Scan(&totalCount)
	if err != nil {
		return nil, 0, err
	}

	query := `
		SELECT m.id, m.message_id, m.chat_id, m.sender_id, m.message_type,
			   m.content, m.media_url, m.media_type, m.is_edited, m.is_deleted,
			   m.created_at, m.updated_at,
			   u.display_name as sender_name,
			   COALESCE(c.name, u2.display_name) as chat_name
		FROM messages m
		JOIN users u ON m.sender_id = u.id
		JOIN chats c ON m.chat_id = c.id
		LEFT JOIN chat_members cm ON c.id = cm.chat_id AND c.type = 'private'
		LEFT JOIN users u2 ON cm.user_id = u2.id AND u2.id != m.sender_id
		WHERE m.is_deleted = false`

	if params.Search != "" {
		query += " AND m.content ILIKE $1"
	}

	if params.SortBy != "" {
		query += fmt.Sprintf(" ORDER BY m.%s %s", params.SortBy, params.SortDir)
	} else {
		query += " ORDER BY m.created_at DESC"
	}

	offset := (params.Page - 1) * params.PageSize
	query += fmt.Sprintf(" LIMIT $%d OFFSET $%d", argPos, argPos+1)
	args = append(args, params.PageSize, offset)

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var messages []models.Message
	for rows.Next() {
		var msg models.Message
		err := rows.Scan(
			&msg.ID, &msg.MessageID, &msg.ChatID, &msg.SenderID, &msg.MessageType,
			&msg.Content, &msg.MediaURL, &msg.MediaType, &msg.IsEdited, &msg.IsDeleted,
			&msg.CreatedAt, &msg.UpdatedAt, &msg.SenderName, &msg.ChatName,
		)
		if err != nil {
			return nil, 0, err
		}
		messages = append(messages, msg)
	}

	return messages, totalCount, nil
}

func (r *MessageRepository) GetByID(ctx context.Context, id int) (*models.Message, error) {
	query := `
		SELECT m.id, m.message_id, m.chat_id, m.sender_id, m.message_type,
			   m.content, m.media_url, m.media_type, m.is_edited, m.is_deleted,
			   m.created_at, m.updated_at,
			   u.display_name as sender_name,
			   COALESCE(c.name, 'Private Chat') as chat_name
		FROM messages m
		JOIN users u ON m.sender_id = u.id
		JOIN chats c ON m.chat_id = c.id
		WHERE m.id = $1`

	var msg models.Message
	err := r.db.QueryRowContext(ctx, query, id).Scan(
		&msg.ID, &msg.MessageID, &msg.ChatID, &msg.SenderID, &msg.MessageType,
		&msg.Content, &msg.MediaURL, &msg.MediaType, &msg.IsEdited, &msg.IsDeleted,
		&msg.CreatedAt, &msg.UpdatedAt, &msg.SenderName, &msg.ChatName,
	)

	if err == sql.ErrNoRows {
		return nil, fmt.Errorf("message not found")
	}
	if err != nil {
		return nil, err
	}

	return &msg, nil
}

func (r *MessageRepository) Delete(ctx context.Context, id int) error {
	query := "UPDATE messages SET is_deleted = true, content = '[Deleted]', updated_at = NOW() WHERE id = $1"
	_, err := r.db.ExecContext(ctx, query, id)
	return err
}

func (r *MessageRepository) Search(ctx context.Context, searchTerm string, limit int) ([]models.Message, error) {
	query := `
		SELECT m.id, m.message_id, m.chat_id, m.sender_id, m.message_type,
			   m.content, m.media_url, m.media_type, m.is_edited, m.is_deleted,
			   m.created_at, m.updated_at,
			   u.display_name as sender_name,
			   COALESCE(c.name, 'Private Chat') as chat_name
		FROM messages m
		JOIN users u ON m.sender_id = u.id
		JOIN chats c ON m.chat_id = c.id
		WHERE m.is_deleted = false AND m.content ILIKE $1
		ORDER BY m.created_at DESC
		LIMIT $2`

	rows, err := r.db.QueryContext(ctx, query, "%"+searchTerm+"%", limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var messages []models.Message
	for rows.Next() {
		var msg models.Message
		err := rows.Scan(
			&msg.ID, &msg.MessageID, &msg.ChatID, &msg.SenderID, &msg.MessageType,
			&msg.Content, &msg.MediaURL, &msg.MediaType, &msg.IsEdited, &msg.IsDeleted,
			&msg.CreatedAt, &msg.UpdatedAt, &msg.SenderName, &msg.ChatName,
		)
		if err != nil {
			return nil, err
		}
		messages = append(messages, msg)
	}

	return messages, nil
}

func (r *MessageRepository) GetByChatID(ctx context.Context, chatID int, params models.MessageFilterParams) ([]models.Message, int, error) {
	// Count query
	countQuery := "SELECT COUNT(*) FROM messages WHERE chat_id = $1 AND is_deleted = false"
	countArgs := []interface{}{chatID}
	argPos := 2

	if params.Search != "" {
		countQuery += fmt.Sprintf(" AND content ILIKE $%d", argPos)
		countArgs = append(countArgs, "%"+params.Search+"%")
		argPos++
	}

	if params.StartDate != "" {
		countQuery += fmt.Sprintf(" AND created_at >= $%d", argPos)
		countArgs = append(countArgs, params.StartDate)
		argPos++
	}

	if params.EndDate != "" {
		countQuery += fmt.Sprintf(" AND created_at <= $%d", argPos)
		countArgs = append(countArgs, params.EndDate)
		argPos++
	}

	if params.SenderID > 0 {
		countQuery += fmt.Sprintf(" AND sender_id = $%d", argPos)
		countArgs = append(countArgs, params.SenderID)
		argPos++
	}

	if params.MessageType != "" {
		countQuery += fmt.Sprintf(" AND message_type = $%d", argPos)
		countArgs = append(countArgs, params.MessageType)
		argPos++
	}

	var totalCount int
	err := r.db.QueryRowContext(ctx, countQuery, countArgs...).Scan(&totalCount)
	if err != nil {
		return nil, 0, err
	}

	// Main query
	query := `
		SELECT m.id, m.message_id, m.chat_id, m.sender_id, m.message_type,
			   m.content, m.media_url, m.media_type, m.is_edited, m.is_deleted,
			   m.created_at, m.updated_at,
			   u.display_name as sender_name,
			   u.username as sender_username
		FROM messages m
		JOIN users u ON m.sender_id = u.id
		WHERE m.chat_id = $1 AND m.is_deleted = false`

	args := []interface{}{chatID}
	argPos = 2

	if params.Search != "" {
		query += fmt.Sprintf(" AND m.content ILIKE $%d", argPos)
		args = append(args, "%"+params.Search+"%")
		argPos++
	}

	if params.StartDate != "" {
		query += fmt.Sprintf(" AND m.created_at >= $%d", argPos)
		args = append(args, params.StartDate)
		argPos++
	}

	if params.EndDate != "" {
		query += fmt.Sprintf(" AND m.created_at <= $%d", argPos)
		args = append(args, params.EndDate)
		argPos++
	}

	if params.SenderID > 0 {
		query += fmt.Sprintf(" AND m.sender_id = $%d", argPos)
		args = append(args, params.SenderID)
		argPos++
	}

	if params.MessageType != "" {
		query += fmt.Sprintf(" AND m.message_type = $%d", argPos)
		args = append(args, params.MessageType)
		argPos++
	}

	query += " ORDER BY m.created_at DESC"

	offset := (params.Page - 1) * params.PageSize
	query += fmt.Sprintf(" LIMIT $%d OFFSET $%d", argPos, argPos+1)
	args = append(args, params.PageSize, offset)

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var messages []models.Message
	for rows.Next() {
		var msg models.Message
		var senderUsername string
		err := rows.Scan(
			&msg.ID, &msg.MessageID, &msg.ChatID, &msg.SenderID, &msg.MessageType,
			&msg.Content, &msg.MediaURL, &msg.MediaType, &msg.IsEdited, &msg.IsDeleted,
			&msg.CreatedAt, &msg.UpdatedAt, &msg.SenderName, &senderUsername,
		)
		if err != nil {
			return nil, 0, err
		}
		msg.SenderName = senderUsername + " (" + msg.SenderName + ")"
		messages = append(messages, msg)
	}

	return messages, totalCount, nil
}
