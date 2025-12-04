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

// GetByEmail retrieves user by email
func (r *UserRepository) GetByEmail(ctx context.Context, email string) (*models.User, error) {
	user := &models.User{}
	query := `
		SELECT id, username, email, password_hash, display_name, avatar_url, bio, read_receipts_enabled, typing_indicators_enabled, created_at, updated_at
		FROM users
		WHERE email = $1`

	var avatarURL sql.NullString
	var bio sql.NullString
	err := r.db.QueryRowContext(ctx, query, email).Scan(
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

// GetByUsername retrieves user by username
func (r *UserRepository) GetByUsername(ctx context.Context, username string) (*models.User, error) {
	user := &models.User{}
	query := `
		SELECT id, username, email, password_hash, display_name, avatar_url, bio, read_receipts_enabled, typing_indicators_enabled, created_at, updated_at
		FROM users
		WHERE username = $1`

	err := r.db.QueryRowContext(ctx, query, username).Scan(
		&user.ID,
		&user.Username,
		&user.Email,
		&user.PasswordHash,
		&user.DisplayName,
		&user.AvatarURL,
		&user.Bio,
		&user.ReadReceiptsEnabled,
		&user.TypingIndicatorsEnabled,
		&user.CreatedAt,
		&user.UpdatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, fmt.Errorf("user not found")
	}
	return user, err
}

// Search searches for users by query
func (r *UserRepository) Search(ctx context.Context, query string, limit int) ([]*models.User, error) {
	sqlQuery := `
		SELECT id, username, email, display_name, avatar_url, bio, read_receipts_enabled, typing_indicators_enabled, created_at, updated_at
		FROM users
		WHERE username ILIKE $1 OR display_name ILIKE $1 OR email ILIKE $1
		LIMIT $2`

	rows, err := r.db.QueryContext(ctx, sqlQuery, "%"+query+"%", limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var users []*models.User
	for rows.Next() {
		user := &models.User{}
		var avatarURL sql.NullString
		var bio sql.NullString
		err := rows.Scan(
			&user.ID,
			&user.Username,
			&user.Email,
			&user.DisplayName,
			&avatarURL,
			&bio,
			&user.ReadReceiptsEnabled,
			&user.TypingIndicatorsEnabled,
			&user.CreatedAt,
			&user.UpdatedAt,
		)
		if err != nil {
			return nil, err
		}
		if avatarURL.Valid {
			user.AvatarURL = avatarURL.String
		}
		if bio.Valid {
			user.Bio = bio.String
		}
		users = append(users, user)
	}

	return users, rows.Err()
}
