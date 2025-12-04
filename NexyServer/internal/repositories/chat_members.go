package repositories

import (
	"context"
	"encoding/json"

	"github.com/vtstv/nexy/internal/models"
)

// AddMember adds a user to a chat
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

// RemoveMember removes a user from a chat
func (r *ChatRepository) RemoveMember(ctx context.Context, chatID, userID int) error {
	query := `DELETE FROM chat_members WHERE chat_id = $1 AND user_id = $2`
	_, err := r.db.ExecContext(ctx, query, chatID, userID)
	return err
}

// UpdateMemberRole updates a chat member's role
func (r *ChatRepository) UpdateMemberRole(ctx context.Context, chatID, userID int, role string) error {
	query := `UPDATE chat_members SET role = $1 WHERE chat_id = $2 AND user_id = $3`
	_, err := r.db.ExecContext(ctx, query, role, chatID, userID)
	return err
}

// UpdateMemberPermissions updates a chat member's permissions
func (r *ChatRepository) UpdateMemberPermissions(ctx context.Context, chatID, userID int, permissions *models.ChatPermissions) error {
	query := `UPDATE chat_members SET permissions = $1 WHERE chat_id = $2 AND user_id = $3`
	permsJSON, _ := json.Marshal(permissions)
	_, err := r.db.ExecContext(ctx, query, permsJSON, chatID, userID)
	return err
}

// IsMember checks if a user is a member of a chat
func (r *ChatRepository) IsMember(ctx context.Context, chatID, userID int) (bool, error) {
	query := `SELECT EXISTS(SELECT 1 FROM chat_members WHERE chat_id = $1 AND user_id = $2)`
	var exists bool
	err := r.db.QueryRowContext(ctx, query, chatID, userID).Scan(&exists)
	return exists, err
}

// GetChatMember retrieves a specific chat member
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

// GetChatMembers retrieves all member IDs for a chat
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

// GetChatMembersFull retrieves full member details for a chat
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

// GetChatMembersWithSearch retrieves members that match a search query
func (r *ChatRepository) GetChatMembersWithSearch(ctx context.Context, chatID int, queryStr string) ([]*models.ChatMember, error) {
	query := `
		SELECT cm.id, cm.chat_id, cm.user_id, cm.role, cm.permissions, cm.joined_at,
			   u.id, u.username, u.email, u.display_name, u.avatar_url, u.bio, u.read_receipts_enabled, u.created_at, u.updated_at
		FROM chat_members cm
		JOIN users u ON cm.user_id = u.id
		WHERE cm.chat_id = $1 AND (u.username ILIKE $2 OR u.display_name ILIKE $2)`

	rows, err := r.db.QueryContext(ctx, query, chatID, "%"+queryStr+"%")
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var members []*models.ChatMember
	for rows.Next() {
		member := &models.ChatMember{}
		user := &models.User{}
		var permissions []byte

		err := rows.Scan(
			&member.ID,
			&member.ChatID,
			&member.UserID,
			&member.Role,
			&permissions,
			&member.JoinedAt,
			&user.ID,
			&user.Username,
			&user.Email,
			&user.DisplayName,
			&user.AvatarURL,
			&user.Bio,
			&user.ReadReceiptsEnabled,
			&user.CreatedAt,
			&user.UpdatedAt,
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
		member.User = user
		members = append(members, member)
	}
	return members, nil
}
