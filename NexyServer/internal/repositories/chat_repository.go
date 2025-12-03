package repositories

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"

	"github.com/vtstv/nexy/internal/database"
	"github.com/vtstv/nexy/internal/models"
)

type ChatRepository struct {
	db *database.DB
}

func NewChatRepository(db *database.DB) *ChatRepository {
	return &ChatRepository{db: db}
}

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
		return nil, fmt.Errorf("chat not found")
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

func (r *ChatRepository) AddMember(ctx context.Context, member *models.ChatMember) error {
	query := `
		INSERT INTO chat_members (chat_id, user_id, role, permissions)
		VALUES ($1, $2, $3, $4)
		RETURNING id, joined_at`

	permsJSON, _ := json.Marshal(member.Permissions)

	return r.db.QueryRowContext(ctx, query,
		member.ChatID,
		member.UserID,
		member.Role,
		permsJSON,
	).Scan(&member.ID, &member.JoinedAt)
}

func (r *ChatRepository) RemoveMember(ctx context.Context, chatID, userID int) error {
	query := `DELETE FROM chat_members WHERE chat_id = $1 AND user_id = $2`
	_, err := r.db.ExecContext(ctx, query, chatID, userID)
	return err
}

func (r *ChatRepository) UpdateMemberRole(ctx context.Context, chatID, userID int, role string) error {
	query := `UPDATE chat_members SET role = $1 WHERE chat_id = $2 AND user_id = $3`
	_, err := r.db.ExecContext(ctx, query, role, chatID, userID)
	return err
}

func (r *ChatRepository) UpdateMemberPermissions(ctx context.Context, chatID, userID int, permissions *models.ChatPermissions) error {
	query := `UPDATE chat_members SET permissions = $1 WHERE chat_id = $2 AND user_id = $3`
	permsJSON, _ := json.Marshal(permissions)
	_, err := r.db.ExecContext(ctx, query, permsJSON, chatID, userID)
	return err
}

func (r *ChatRepository) CreateInviteLink(ctx context.Context, link *models.ChatInviteLink) error {
	query := `
		INSERT INTO chat_invite_links (chat_id, creator_id, code, expires_at, usage_limit)
		VALUES ($1, $2, $3, $4, $5)
		RETURNING id, created_at`

	return r.db.QueryRowContext(ctx, query,
		link.ChatID,
		link.CreatorID,
		link.Code,
		link.ExpiresAt,
		link.UsageLimit,
	).Scan(&link.ID, &link.CreatedAt)
}

func (r *ChatRepository) GetInviteLink(ctx context.Context, code string) (*models.ChatInviteLink, error) {
	link := &models.ChatInviteLink{}
	query := `
		SELECT id, chat_id, creator_id, code, is_revoked, expires_at, usage_limit, usage_count, created_at
		FROM chat_invite_links
		WHERE code = $1`

	err := r.db.QueryRowContext(ctx, query, code).Scan(
		&link.ID,
		&link.ChatID,
		&link.CreatorID,
		&link.Code,
		&link.IsRevoked,
		&link.ExpiresAt,
		&link.UsageLimit,
		&link.UsageCount,
		&link.CreatedAt,
	)
	if err != nil {
		return nil, err
	}
	return link, nil
}

func (r *ChatRepository) IncrementInviteUsage(ctx context.Context, linkID int) error {
	query := `UPDATE chat_invite_links SET usage_count = usage_count + 1 WHERE id = $1`
	_, err := r.db.ExecContext(ctx, query, linkID)
	return err
}

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

func (r *ChatRepository) IsMember(ctx context.Context, chatID, userID int) (bool, error) {
	query := `SELECT EXISTS(SELECT 1 FROM chat_members WHERE chat_id = $1 AND user_id = $2)`
	var exists bool
	err := r.db.QueryRowContext(ctx, query, chatID, userID).Scan(&exists)
	return exists, err
}

func (r *ChatRepository) GetChatMember(ctx context.Context, chatID, userID int) (*models.ChatMember, error) {
	member := &models.ChatMember{}
	query := `
		SELECT id, chat_id, user_id, role, permissions, joined_at
		FROM chat_members
		WHERE chat_id = $1 AND user_id = $2`

	var permissions []byte
	err := r.db.QueryRowContext(ctx, query, chatID, userID).Scan(
		&member.ID,
		&member.ChatID,
		&member.UserID,
		&member.Role,
		&permissions,
		&member.JoinedAt,
	)
	if err != nil {
		return nil, err
	}

	if len(permissions) > 0 {
		var perms models.ChatPermissions
		if err := json.Unmarshal(permissions, &perms); err == nil {
			member.Permissions = &perms
		}
	}
	return member, nil
}

func (r *ChatRepository) GetChatMembers(ctx context.Context, chatID int) ([]int, error) {
	query := `SELECT user_id FROM chat_members WHERE chat_id = $1`
	rows, err := r.db.QueryContext(ctx, query, chatID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var memberIDs []int
	for rows.Next() {
		var id int
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		memberIDs = append(memberIDs, id)
	}
	return memberIDs, nil
}

func (r *ChatRepository) GetChatMembersFull(ctx context.Context, chatID int) ([]*models.ChatMember, error) {
	query := `
		SELECT id, chat_id, user_id, role, permissions, joined_at
		FROM chat_members
		WHERE chat_id = $1`

	rows, err := r.db.QueryContext(ctx, query, chatID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var members []*models.ChatMember
	for rows.Next() {
		member := &models.ChatMember{}
		var permissions []byte
		if err := rows.Scan(
			&member.ID,
			&member.ChatID,
			&member.UserID,
			&member.Role,
			&permissions,
			&member.JoinedAt,
		); err != nil {
			return nil, err
		}

		if len(permissions) > 0 {
			var perms models.ChatPermissions
			if err := json.Unmarshal(permissions, &perms); err == nil {
				member.Permissions = &perms
			}
		}
		members = append(members, member)
	}
	return members, nil
}

func (r *ChatRepository) DeleteChat(ctx context.Context, chatID int) error {
	query := `DELETE FROM chats WHERE id = $1`
	_, err := r.db.ExecContext(ctx, query, chatID)
	return err
}

func (r *ChatRepository) ClearMessages(ctx context.Context, chatID int) error {
	query := `DELETE FROM messages WHERE chat_id = $1`
	_, err := r.db.ExecContext(ctx, query, chatID)
	return err
}

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

func (r *ChatRepository) GetInviteLinkByCode(ctx context.Context, code string) (*models.ChatInviteLink, error) {
	link := &models.ChatInviteLink{}
	query := `
		SELECT id, chat_id, creator_id, code, is_revoked, expires_at, usage_limit, usage_count, created_at
		FROM chat_invite_links
		WHERE code = $1`

	var expiresAt sql.NullTime
	var usageLimit sql.NullInt64

	err := r.db.QueryRowContext(ctx, query, code).Scan(
		&link.ID,
		&link.ChatID,
		&link.CreatorID,
		&link.Code,
		&link.IsRevoked,
		&expiresAt,
		&usageLimit,
		&link.UsageCount,
		&link.CreatedAt,
	)
	if err != nil {
		return nil, err
	}

	if expiresAt.Valid {
		link.ExpiresAt = &expiresAt.Time
	}
	if usageLimit.Valid {
		limit := int(usageLimit.Int64)
		link.UsageLimit = &limit
	}

	return link, nil
}

func (r *ChatRepository) UpdateInviteLink(ctx context.Context, link *models.ChatInviteLink) error {
	query := `
		UPDATE chat_invite_links
		SET usage_count = $1, is_revoked = $2
		WHERE id = $3`

	_, err := r.db.ExecContext(ctx, query, link.UsageCount, link.IsRevoked, link.ID)
	return err
}
