package repositories

import (
	"context"
	"database/sql"
	"encoding/json"

	"github.com/vtstv/nexy/internal/models"
)

// GetPrivateChatBetween finds a private chat between two users
func (r *ChatRepository) GetPrivateChatBetween(ctx context.Context, user1ID, user2ID int) (*models.Chat, error) {
	query := `
		SELECT c.id, c.type, c.name, c.avatar_url, c.created_by, c.created_at, c.updated_at
		FROM chats c
		INNER JOIN chat_members cm1 ON c.id = cm1.chat_id AND cm1.user_id = $1
		INNER JOIN chat_members cm2 ON c.id = cm2.chat_id AND cm2.user_id = $2
		WHERE c.type = 'private'
		LIMIT 1`

	chat := &models.Chat{}
	var createdBy sql.NullInt64
	err := r.db.QueryRowContext(ctx, query, user1ID, user2ID).Scan(
		&chat.ID,
		&chat.Type,
		&chat.Name,
		&chat.AvatarURL,
		&createdBy,
		&chat.CreatedAt,
		&chat.UpdatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if createdBy.Valid {
		id := int(createdBy.Int64)
		chat.CreatedBy = &id
	}

	// Load participants
	participants, err := r.GetChatMembers(ctx, chat.ID)
	if err == nil {
		chat.ParticipantIds = participants
	}

	return chat, err
}

// GetSelfChat finds a user's self-chat (Saved Messages)
func (r *ChatRepository) GetSelfChat(ctx context.Context, userID int) (*models.Chat, error) {
	query := `
		SELECT c.id, c.type, c.name, c.avatar_url, c.created_by, c.created_at, c.updated_at
		FROM chats c
		JOIN chat_members cm ON c.id = cm.chat_id
		WHERE c.type = 'private'
		GROUP BY c.id, c.type, c.name, c.avatar_url, c.created_by, c.created_at, c.updated_at
		HAVING COUNT(cm.user_id) = 1 AND MAX(cm.user_id) = $1
		LIMIT 1`

	chat := &models.Chat{}
	var createdBy sql.NullInt64
	err := r.db.QueryRowContext(ctx, query, userID).Scan(
		&chat.ID,
		&chat.Type,
		&chat.Name,
		&chat.AvatarURL,
		&createdBy,
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

	// Load participants
	participants, err := r.GetChatMembers(ctx, chat.ID)
	if err == nil {
		chat.ParticipantIds = participants
	}

	return chat, nil
}

// GetUserChats retrieves all chats for a user
func (r *ChatRepository) GetUserChats(ctx context.Context, userID int) ([]*models.Chat, error) {
	query := `
		SELECT c.id, c.type, c.name, c.avatar_url, c.created_by, c.created_at, c.updated_at
		FROM chats c
		INNER JOIN chat_members cm ON c.id = cm.chat_id
		WHERE cm.user_id = $1
		ORDER BY c.updated_at DESC`

	rows, err := r.db.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var chats []*models.Chat
	for rows.Next() {
		chat := &models.Chat{}
		var createdBy sql.NullInt64
		err := rows.Scan(
			&chat.ID,
			&chat.Type,
			&chat.Name,
			&chat.AvatarURL,
			&createdBy,
			&chat.CreatedAt,
			&chat.UpdatedAt,
		)
		if err != nil {
			return nil, err
		}
		if createdBy.Valid {
			id := int(createdBy.Int64)
			chat.CreatedBy = &id
		}

		// Load participants
		participants, err := r.GetChatMembers(ctx, chat.ID)
		if err == nil {
			chat.ParticipantIds = participants
		}

		chats = append(chats, chat)
	}

	return chats, rows.Err()
}

// GetChatByUsername retrieves a chat by its username (deprecated, use GetByUsername)
func (r *ChatRepository) GetChatByUsername(ctx context.Context, username string) (*models.Chat, error) {
	chat := &models.Chat{}
	query := `
		SELECT id, type, name, username, description, avatar_url, created_by, default_permissions, created_at, updated_at
		FROM chats
		WHERE username = $1`

	var createdBy sql.NullInt64
	var desc sql.NullString
	var defaultPermissions []byte

	err := r.db.QueryRowContext(ctx, query, username).Scan(
		&chat.ID,
		&chat.Type,
		&chat.Name,
		&chat.Username,
		&desc,
		&chat.AvatarURL,
		&createdBy,
		&defaultPermissions,
		&chat.CreatedAt,
		&chat.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}
	if createdBy.Valid {
		id := int(createdBy.Int64)
		chat.CreatedBy = &id
	}
	if desc.Valid {
		chat.Description = desc.String
	}
	if len(defaultPermissions) > 0 {
		var perms models.ChatPermissions
		if err := json.Unmarshal(defaultPermissions, &perms); err == nil {
			chat.DefaultPermissions = &perms
		}
	}
	return chat, nil
}

// GetByUsername retrieves a chat by its username
func (r *ChatRepository) GetByUsername(ctx context.Context, username string) (*models.Chat, error) {
	chat := &models.Chat{}
	query := `
		SELECT id, type, group_type, name, username, description, avatar_url, created_by, default_permissions, created_at, updated_at
		FROM chats
		WHERE username = $1`

	var createdBy sql.NullInt64
	var desc, groupType sql.NullString
	var defaultPermissions []byte

	err := r.db.QueryRowContext(ctx, query, username).Scan(
		&chat.ID,
		&chat.Type,
		&groupType,
		&chat.Name,
		&chat.Username,
		&desc,
		&chat.AvatarURL,
		&createdBy,
		&defaultPermissions,
		&chat.CreatedAt,
		&chat.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}
	if createdBy.Valid {
		id := int(createdBy.Int64)
		chat.CreatedBy = &id
	}
	if desc.Valid {
		chat.Description = desc.String
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
	}

	return chat, nil
}

// SearchPublicGroups searches for public groups
func (r *ChatRepository) SearchPublicGroups(ctx context.Context, query string, limit int) ([]*models.Chat, error) {
	sqlQuery := `
		SELECT id, type, group_type, name, username, description, avatar_url, created_by, default_permissions, created_at, updated_at
		FROM chats
		WHERE group_type = 'public_group' AND (name ILIKE $1 OR username ILIKE $1 OR description ILIKE $1)
		ORDER BY created_at DESC
		LIMIT $2`

	rows, err := r.db.QueryContext(ctx, sqlQuery, "%"+query+"%", limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var chats []*models.Chat
	for rows.Next() {
		chat := &models.Chat{}
		var createdBy sql.NullInt64
		var username, description, groupType sql.NullString
		var defaultPermissions []byte

		err := rows.Scan(
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

		chats = append(chats, chat)
	}

	return chats, rows.Err()
}
