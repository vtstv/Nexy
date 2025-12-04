package repositories

import (
	"context"
	"database/sql"

	"github.com/vtstv/nexy/internal/models"
)

// CreateInviteLink creates a new invite link for a chat
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

// GetInviteLink retrieves an invite link by its code
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

// GetInviteLinkByCode retrieves an invite link with nullable fields properly handled
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

// IncrementInviteUsage increments the usage count for an invite link
func (r *ChatRepository) IncrementInviteUsage(ctx context.Context, linkID int) error {
	query := `UPDATE chat_invite_links SET usage_count = usage_count + 1 WHERE id = $1`
	_, err := r.db.ExecContext(ctx, query, linkID)
	return err
}

// UpdateInviteLink updates an invite link
func (r *ChatRepository) UpdateInviteLink(ctx context.Context, link *models.ChatInviteLink) error {
	query := `
		UPDATE chat_invite_links
		SET usage_count = $1, is_revoked = $2
		WHERE id = $3`

	_, err := r.db.ExecContext(ctx, query, link.UsageCount, link.IsRevoked, link.ID)
	return err
}
