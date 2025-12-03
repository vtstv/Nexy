package repositories

import (
	"context"
	"database/sql"
	"fmt"
	"time"

	"github.com/vtstv/nexy/internal/database"
	"github.com/vtstv/nexy/internal/models"
)

type InviteRepository struct {
	db *database.DB
}

func NewInviteRepository(db *database.DB) *InviteRepository {
	return &InviteRepository{db: db}
}

func (r *InviteRepository) Create(ctx context.Context, invite *models.InviteLink) error {
	query := `
		INSERT INTO invite_links (code, creator_id, max_uses, expires_at)
		VALUES ($1, $2, $3, $4)
		RETURNING id, uses_count, created_at`

	return r.db.QueryRowContext(ctx, query,
		invite.Code,
		invite.CreatorID,
		invite.MaxUses,
		invite.ExpiresAt,
	).Scan(&invite.ID, &invite.UsesCount, &invite.CreatedAt)
}

func (r *InviteRepository) GetByCode(ctx context.Context, code string) (*models.InviteLink, error) {
	invite := &models.InviteLink{}
	query := `
		SELECT id, code, creator_id, max_uses, uses_count, expires_at, created_at
		FROM invite_links
		WHERE code = $1`

	var expiresAt sql.NullTime
	err := r.db.QueryRowContext(ctx, query, code).Scan(
		&invite.ID,
		&invite.Code,
		&invite.CreatorID,
		&invite.MaxUses,
		&invite.UsesCount,
		&expiresAt,
		&invite.CreatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, fmt.Errorf("invite not found")
	}
	if expiresAt.Valid {
		invite.ExpiresAt = &expiresAt.Time
	}
	return invite, err
}

func (r *InviteRepository) IncrementUses(ctx context.Context, id int) error {
	query := `UPDATE invite_links SET uses_count = uses_count + 1 WHERE id = $1`
	_, err := r.db.ExecContext(ctx, query, id)
	return err
}

func (r *InviteRepository) IsValid(ctx context.Context, code string) (bool, error) {
	invite, err := r.GetByCode(ctx, code)
	if err != nil {
		return false, err
	}

	if invite.UsesCount >= invite.MaxUses {
		return false, nil
	}

	if invite.ExpiresAt != nil && invite.ExpiresAt.Before(time.Now()) {
		return false, nil
	}

	return true, nil
}

func (r *InviteRepository) GetByCreator(ctx context.Context, creatorID int) ([]*models.InviteLink, error) {
	query := `
		SELECT id, code, creator_id, max_uses, uses_count, expires_at, created_at
		FROM invite_links
		WHERE creator_id = $1
		ORDER BY created_at DESC`

	rows, err := r.db.QueryContext(ctx, query, creatorID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var invites []*models.InviteLink
	for rows.Next() {
		invite := &models.InviteLink{}
		var expiresAt sql.NullTime
		err := rows.Scan(
			&invite.ID,
			&invite.Code,
			&invite.CreatorID,
			&invite.MaxUses,
			&invite.UsesCount,
			&expiresAt,
			&invite.CreatedAt,
		)
		if err != nil {
			return nil, err
		}
		if expiresAt.Valid {
			invite.ExpiresAt = &expiresAt.Time
		}
		invites = append(invites, invite)
	}

	return invites, rows.Err()
}
