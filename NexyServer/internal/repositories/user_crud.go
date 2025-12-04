/*
 * © 2025 Murr | https://github.com/vtstv
 */
package repositories

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/vtstv/nexy/internal/models"
)

// Create creates a new user
func (r *UserRepository) Create(ctx context.Context, user *models.User) error {
	query := `
		INSERT INTO users (username, email, password_hash, display_name, avatar_url, bio)
		VALUES ($1, $2, $3, $4, $5, $6)
		RETURNING id, read_receipts_enabled, typing_indicators_enabled, created_at, updated_at`

	return r.db.QueryRowContext(ctx, query,
		user.Username,
		user.Email,
		user.PasswordHash,
		user.DisplayName,
		user.AvatarURL,
		user.Bio,
	).Scan(&user.ID, &user.ReadReceiptsEnabled, &user.TypingIndicatorsEnabled, &user.CreatedAt, &user.UpdatedAt)
}

// GetByID retrieves user by ID
func (r *UserRepository) GetByID(ctx context.Context, id int) (*models.User, error) {
	user := &models.User{}
	query := `
		SELECT id, username, email, password_hash, display_name, avatar_url, bio, read_receipts_enabled, typing_indicators_enabled, created_at, updated_at
		FROM users
		WHERE id = $1`

	var avatarURL sql.NullString
	var bio sql.NullString
	err := r.db.QueryRowContext(ctx, query, id).Scan(
		&user.ID,
		&user.Username,
		&user.Email,
		&user.PasswordHash,
		&user.DisplayName,
		&avatarURL,
		&bio,
		&user.ReadReceiptsEnabled,
		&user.TypingIndicatorsEnabled,
		&user.CreatedAt,
		&user.UpdatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, fmt.Errorf("user not found")
	}
	if avatarURL.Valid {
		user.AvatarURL = avatarURL.String
	}
	if bio.Valid {
		user.Bio = bio.String
	}
	return user, err
}

// Update updates user profile
func (r *UserRepository) Update(ctx context.Context, user *models.User) error {
	query := `
		UPDATE users
		SET display_name = $1, avatar_url = $2, bio = $3, read_receipts_enabled = $4, typing_indicators_enabled = $5, updated_at = CURRENT_TIMESTAMP
		WHERE id = $6
		RETURNING updated_at`

	return r.db.QueryRowContext(ctx, query,
		user.DisplayName,
		user.AvatarURL,
		user.Bio,
		user.ReadReceiptsEnabled,
		user.TypingIndicatorsEnabled,
		user.ID,
	).Scan(&user.UpdatedAt)
}

// UpdateAvatar updates user avatar URL
func (r *UserRepository) UpdateAvatar(ctx context.Context, userID int, avatarURL string) error {
	query := `UPDATE users SET avatar_url = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2`
	_, err := r.db.ExecContext(ctx, query, avatarURL, userID)
	return err
}
