/*
 * © 2025 Murr | https://github.com/vtstv
 */
package repositories

import (
	"context"

	"github.com/vtstv/nexy/internal/database"
)

type UserRepository struct {
	db *database.DB
}

func NewUserRepository(db *database.DB) *UserRepository {
	return &UserRepository{db: db}
}

func (r *UserRepository) UpdateFcmToken(ctx context.Context, userID int, fcmToken string) error {
	query := `UPDATE users SET fcm_token = $1, updated_at = NOW() WHERE id = $2`
	_, err := r.db.ExecContext(ctx, query, fcmToken, userID)
	return err
}

func (r *UserRepository) GetFcmToken(ctx context.Context, userID int) (string, error) {
	var fcmToken string
	query := `SELECT COALESCE(fcm_token, '') FROM users WHERE id = $1`
	err := r.db.QueryRowContext(ctx, query, userID).Scan(&fcmToken)
	return fcmToken, err
}
