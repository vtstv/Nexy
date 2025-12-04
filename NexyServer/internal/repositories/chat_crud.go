package repositories

import (
	"context"
	"database/sql"
	"encoding/json"

	"github.com/vtstv/nexy/internal/models"
)

// Create creates a new chat in the database
func (r *ChatRepository) Create(ctx context.Context, chat *models.Chat) error {
	query := `
		INSERT INTO chats (type, group_type, name, username, description, avatar_url, created_by, default_permissions)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
		RETURNING id, created_at, updated_at`

	var username interface{} = chat.Username
	if chat.Username == "" {
		username = nil
	}

	var groupType interface{} = chat.GroupType
	if chat.GroupType == "" {
		groupType = nil
	}

	var defaultPermissions interface{}
	if chat.DefaultPermissions != nil {
		permsJSON, err := json.Marshal(chat.DefaultPermissions)
		if err != nil {
			return err
		}
		defaultPermissions = permsJSON
	}

	return r.db.QueryRowContext(ctx, query,
		chat.Type,
		groupType,
		chat.Name,
		username,
		chat.Description,
		chat.AvatarURL,
		chat.CreatedBy,
		defaultPermissions,
	).Scan(&chat.ID, &chat.CreatedAt, &chat.UpdatedAt)
}

// GetByID retrieves a chat by its ID
func (r *ChatRepository) GetByID(ctx context.Context, id int) (*models.Chat, error) {
	chat := &models.Chat{}
	query := `
		SELECT id, type, group_type, name, username, description, avatar_url, created_by, default_permissions, created_at, updated_at
		FROM chats
		WHERE id = $1`

	var createdBy sql.NullInt64
	var username, description, groupType sql.NullString
	var defaultPermissions []byte

	err := r.db.QueryRowContext(ctx, query, id).Scan(
		&chat.ID,
		&chat.Type,
		&groupType,
		&chat.Name,
		&username,
		&description,
		&chat.AvatarURL,
		&createdBy,
		&defaultPermissions,
		&chat.CreatedAt,
		&chat.UpdatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	if createdBy.Valid {
		id := int(createdBy.Int64)
		chat.CreatedBy = &id
	}
	if username.Valid {
		chat.Username = username.String
	}
	if description.Valid {
		chat.Description = description.String
	}
	if groupType.Valid {
		chat.GroupType = groupType.String
	}
	if len(defaultPermissions) > 0 {
		var perms models.ChatPermissions
		if err := json.Unmarshal(defaultPermissions, &perms); err == nil {
			chat.DefaultPermissions = &perms
		}
	}

	participants, err := r.GetChatMembers(ctx, chat.ID)
	if err == nil {
		chat.ParticipantIds = participants
	} else {
		return nil, err
	}

	return chat, nil
}

// UpdateChat updates an existing chat
func (r *ChatRepository) UpdateChat(ctx context.Context, chat *models.Chat) error {
	query := `
		UPDATE chats
		SET name = $1, username = $2, description = $3, avatar_url = $4, group_type = $5, default_permissions = $6, updated_at = CURRENT_TIMESTAMP
		WHERE id = $7`

	permsJSON, _ := json.Marshal(chat.DefaultPermissions)

	var username interface{} = chat.Username
	if chat.Username == "" {
		username = nil
	}

	var groupType interface{} = chat.GroupType
	if chat.GroupType == "" {
		groupType = nil
	}

	_, err := r.db.ExecContext(ctx, query,
		chat.Name,
		username,
		chat.Description,
		chat.AvatarURL,
		groupType,
		permsJSON,
		chat.ID,
	)
	return err
}

// DeleteChat deletes a chat from the database
func (r *ChatRepository) DeleteChat(ctx context.Context, chatID int) error {
	query := `DELETE FROM chats WHERE id = $1`
	_, err := r.db.ExecContext(ctx, query, chatID)
	return err
}

// ClearMessages deletes all messages in a chat
func (r *ChatRepository) ClearMessages(ctx context.Context, chatID int) error {
	query := `DELETE FROM messages WHERE chat_id = $1`
	_, err := r.db.ExecContext(ctx, query, chatID)
	return err
}
