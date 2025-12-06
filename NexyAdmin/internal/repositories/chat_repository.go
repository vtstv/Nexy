package repositories

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/vtstv/nexy-admin/internal/models"
)

type ChatRepository struct {
	db *sql.DB
}

func NewChatRepository(db *sql.DB) *ChatRepository {
	return &ChatRepository{db: db}
}

func (r *ChatRepository) GetAll(ctx context.Context, params models.PaginationParams) ([]models.Chat, int, error) {
	countQuery := "SELECT COUNT(*) FROM chats"
	args := []interface{}{}
	argPos := 1

	if params.Search != "" {
		countQuery += fmt.Sprintf(" WHERE name ILIKE $%d OR username ILIKE $%d", argPos, argPos+1)
		searchPattern := "%" + params.Search + "%"
		args = append(args, searchPattern, searchPattern)
		argPos += 2
	}

	var totalCount int
	err := r.db.QueryRowContext(ctx, countQuery, args...).Scan(&totalCount)
	if err != nil {
		return nil, 0, err
	}

	query := `
		SELECT c.id, c.type, c.group_type, c.name, c.username, c.description,
			   c.avatar_url, c.created_by, c.created_at, c.updated_at,
			   COUNT(cm.id) as member_count,
			   STRING_AGG(u.username, ', ') as member_names
		FROM chats c
		LEFT JOIN chat_members cm ON c.id = cm.chat_id
		LEFT JOIN users u ON cm.user_id = u.id`

	if params.Search != "" {
		query += " WHERE c.name ILIKE $1 OR c.username ILIKE $2"
	}

	query += " GROUP BY c.id"

	if params.SortBy != "" {
		query += fmt.Sprintf(" ORDER BY c.%s %s", params.SortBy, params.SortDir)
	} else {
		query += " ORDER BY c.created_at DESC"
	}

	offset := (params.Page - 1) * params.PageSize
	query += fmt.Sprintf(" LIMIT $%d OFFSET $%d", argPos, argPos+1)
	args = append(args, params.PageSize, offset)

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var chats []models.Chat
	for rows.Next() {
		var chat models.Chat
		var groupType, name, username, description, avatarURL, memberNames sql.NullString
		var createdBy sql.NullInt64
		err := rows.Scan(
			&chat.ID, &chat.Type, &groupType, &name, &username,
			&description, &avatarURL, &createdBy,
			&chat.CreatedAt, &chat.UpdatedAt, &chat.MemberCount, &memberNames,
		)
		if err != nil {
			return nil, 0, err
		}
		if groupType.Valid {
			chat.GroupType = &groupType.String
		}
		if name.Valid {
			chat.Name = &name.String
		}
		if username.Valid {
			chat.Username = &username.String
		}
		if description.Valid {
			chat.Description = &description.String
		}
		if avatarURL.Valid {
			chat.AvatarURL = &avatarURL.String
		}
		if createdBy.Valid {
			val := int(createdBy.Int64)
			chat.CreatedBy = &val
		}
		if memberNames.Valid {
			chat.MemberNames = &memberNames.String
		}
		chats = append(chats, chat)
	}

	return chats, totalCount, nil
}

func (r *ChatRepository) GetByID(ctx context.Context, id int) (*models.Chat, error) {
	query := `
		SELECT c.id, c.type, c.group_type, c.name, c.username, c.description,
			   c.avatar_url, c.created_by, c.created_at, c.updated_at,
			   COUNT(cm.id) as member_count,
			   STRING_AGG(u.username, ', ') as member_names
		FROM chats c
		LEFT JOIN chat_members cm ON c.id = cm.chat_id
		LEFT JOIN users u ON cm.user_id = u.id
		WHERE c.id = $1
		GROUP BY c.id`

	var chat models.Chat
	var groupType, name, username, description, avatarURL, memberNames sql.NullString
	var createdBy sql.NullInt64
	err := r.db.QueryRowContext(ctx, query, id).Scan(
		&chat.ID, &chat.Type, &groupType, &name, &username,
		&description, &avatarURL, &createdBy,
		&chat.CreatedAt, &chat.UpdatedAt, &chat.MemberCount, &memberNames,
	)

	if err == sql.ErrNoRows {
		return nil, fmt.Errorf("chat not found")
	}
	if err != nil {
		return nil, err
	}

	if groupType.Valid {
		chat.GroupType = &groupType.String
	}
	if name.Valid {
		chat.Name = &name.String
	}
	if username.Valid {
		chat.Username = &username.String
	}
	if description.Valid {
		chat.Description = &description.String
	}
	if avatarURL.Valid {
		chat.AvatarURL = &avatarURL.String
	}
	if createdBy.Valid {
		val := int(createdBy.Int64)
		chat.CreatedBy = &val
	}
	if memberNames.Valid {
		chat.MemberNames = &memberNames.String
	}

	return &chat, nil
}

func (r *ChatRepository) Update(ctx context.Context, chat *models.Chat) error {
	query := `
		UPDATE chats
		SET name = $1, description = $2, avatar_url = $3, updated_at = NOW()
		WHERE id = $4`

	var name, description, avatarURL interface{}
	if chat.Name != nil {
		name = *chat.Name
	}
	if chat.Description != nil {
		description = *chat.Description
	}
	if chat.AvatarURL != nil {
		avatarURL = *chat.AvatarURL
	}

	_, err := r.db.ExecContext(ctx, query, name, description, avatarURL, chat.ID)
	return err
}

func (r *ChatRepository) Delete(ctx context.Context, id int) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM chats WHERE id = $1", id)
	return err
}

func (r *ChatRepository) GetMembers(ctx context.Context, chatID int) ([]models.ChatMember, error) {
	query := `
		SELECT cm.id, cm.chat_id, cm.user_id, cm.role, cm.joined_at, cm.last_read_message_id,
			   u.username, u.display_name, u.avatar_url
		FROM chat_members cm
		JOIN users u ON cm.user_id = u.id
		WHERE cm.chat_id = $1
		ORDER BY cm.joined_at DESC`

	rows, err := r.db.QueryContext(ctx, query, chatID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var members []models.ChatMember
	for rows.Next() {
		var member models.ChatMember
		err := rows.Scan(
			&member.ID, &member.ChatID, &member.UserID, &member.Role,
			&member.JoinedAt, &member.LastReadMessageId,
			&member.Username, &member.DisplayName, &member.AvatarURL,
		)
		if err != nil {
			return nil, err
		}
		members = append(members, member)
	}

	return members, nil
}

func (r *ChatRepository) RemoveMember(ctx context.Context, chatID, userID int) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM chat_members WHERE chat_id = $1 AND user_id = $2", chatID, userID)
	return err
}
