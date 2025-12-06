package repositories

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/vtstv/nexy-admin/internal/models"
)

type UserRepository struct {
	db *sql.DB
}

func NewUserRepository(db *sql.DB) *UserRepository {
	return &UserRepository{db: db}
}

func (r *UserRepository) GetByUsername(ctx context.Context, username string) (*models.User, error) {
	query := `
		SELECT id, username, email, display_name, avatar_url, bio,
			   read_receipts_enabled, typing_indicators_enabled, voice_messages_enabled,
			   show_online_status, last_seen, created_at, updated_at
		FROM users WHERE username = $1`

	var user models.User
	err := r.db.QueryRowContext(ctx, query, username).Scan(
		&user.ID, &user.Username, &user.Email, &user.DisplayName, &user.AvatarURL,
		&user.Bio, &user.ReadReceiptsEnabled, &user.TypingIndicatorsEnabled,
		&user.VoiceMessagesEnabled, &user.ShowOnlineStatus, &user.LastSeen,
		&user.CreatedAt, &user.UpdatedAt,
	)

	if err == sql.ErrNoRows {
		return nil, fmt.Errorf("user not found")
	}
	if err != nil {
		return nil, err
	}

	return &user, nil
}

func (r *UserRepository) GetAll(ctx context.Context, params models.PaginationParams) ([]models.User, int, error) {
	countQuery := "SELECT COUNT(*) FROM users"
	args := []interface{}{}
	argPos := 1

	if params.Search != "" {
		countQuery += fmt.Sprintf(" WHERE username ILIKE $%d OR email ILIKE $%d OR display_name ILIKE $%d", argPos, argPos+1, argPos+2)
		searchPattern := "%" + params.Search + "%"
		args = append(args, searchPattern, searchPattern, searchPattern)
		argPos += 3
	}

	var totalCount int
	err := r.db.QueryRowContext(ctx, countQuery, args...).Scan(&totalCount)
	if err != nil {
		return nil, 0, err
	}

	query := `
		SELECT id, username, email, display_name, avatar_url, bio,
			   read_receipts_enabled, typing_indicators_enabled, voice_messages_enabled,
			   show_online_status, last_seen, is_banned, banned_at, banned_reason, banned_by,
			   created_at, updated_at
		FROM users`

	if params.Search != "" {
		query += " WHERE username ILIKE $1 OR email ILIKE $2 OR display_name ILIKE $3"
	}

	if params.SortBy != "" {
		query += fmt.Sprintf(" ORDER BY %s %s", params.SortBy, params.SortDir)
	} else {
		query += " ORDER BY created_at DESC"
	}

	offset := (params.Page - 1) * params.PageSize
	query += fmt.Sprintf(" LIMIT $%d OFFSET $%d", argPos, argPos+1)
	args = append(args, params.PageSize, offset)

	rows, err := r.db.QueryContext(ctx, query, args...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var users []models.User
	for rows.Next() {
		var user models.User
		var bannedAt sql.NullTime
		var bannedReason sql.NullString
		var bannedBy sql.NullInt64
		err := rows.Scan(
			&user.ID, &user.Username, &user.Email, &user.DisplayName, &user.AvatarURL,
			&user.Bio, &user.ReadReceiptsEnabled, &user.TypingIndicatorsEnabled,
			&user.VoiceMessagesEnabled, &user.ShowOnlineStatus, &user.LastSeen,
			&user.IsBanned, &bannedAt, &bannedReason, &bannedBy,
			&user.CreatedAt, &user.UpdatedAt,
		)
		if err != nil {
			return nil, 0, err
		}
		if bannedAt.Valid {
			user.BannedAt = &bannedAt.Time
		}
		if bannedReason.Valid {
			user.BannedReason = &bannedReason.String
		}
		if bannedBy.Valid {
			val := int(bannedBy.Int64)
			user.BannedBy = &val
		}
		users = append(users, user)
	}

	return users, totalCount, nil
}

func (r *UserRepository) GetByID(ctx context.Context, id int) (*models.User, error) {
	query := `
		SELECT id, username, email, display_name, avatar_url, bio,
			   read_receipts_enabled, typing_indicators_enabled, voice_messages_enabled,
			   show_online_status, last_seen, is_banned, banned_at, banned_reason, banned_by,
			   created_at, updated_at
		FROM users WHERE id = $1`

	var user models.User
	var bannedAt sql.NullTime
	var bannedReason sql.NullString
	var bannedBy sql.NullInt64
	err := r.db.QueryRowContext(ctx, query, id).Scan(
		&user.ID, &user.Username, &user.Email, &user.DisplayName, &user.AvatarURL,
		&user.Bio, &user.ReadReceiptsEnabled, &user.TypingIndicatorsEnabled,
		&user.VoiceMessagesEnabled, &user.ShowOnlineStatus, &user.LastSeen,
		&user.IsBanned, &bannedAt, &bannedReason, &bannedBy,
		&user.CreatedAt, &user.UpdatedAt,
	)

	if err == sql.ErrNoRows {
		return nil, fmt.Errorf("user not found")
	}
	if err != nil {
		return nil, err
	}

	if bannedAt.Valid {
		user.BannedAt = &bannedAt.Time
	}
	if bannedReason.Valid {
		user.BannedReason = &bannedReason.String
	}
	if bannedBy.Valid {
		val := int(bannedBy.Int64)
		user.BannedBy = &val
	}

	return &user, nil
}

func (r *UserRepository) Update(ctx context.Context, user *models.User) error {
	query := `
		UPDATE users
		SET display_name = $1, bio = $2, avatar_url = $3, updated_at = NOW()
		WHERE id = $4`

	_, err := r.db.ExecContext(ctx, query, user.DisplayName, user.Bio, user.AvatarURL, user.ID)
	return err
}

func (r *UserRepository) Delete(ctx context.Context, id int) error {
	_, err := r.db.ExecContext(ctx, "DELETE FROM users WHERE id = $1", id)
	return err
}

func (r *UserRepository) Ban(ctx context.Context, userID int, reason string, bannedBy int) error {
	query := `
		UPDATE users
		SET is_banned = true, banned_at = NOW(), banned_reason = $1, banned_by = $2
		WHERE id = $3`

	_, err := r.db.ExecContext(ctx, query, reason, bannedBy, userID)
	return err
}

func (r *UserRepository) Unban(ctx context.Context, userID int) error {
	query := `
		UPDATE users
		SET is_banned = false, banned_at = NULL, banned_reason = NULL, banned_by = NULL
		WHERE id = $1`

	_, err := r.db.ExecContext(ctx, query, userID)
	return err
}

func (r *UserRepository) GetSessions(ctx context.Context, userID int) ([]models.UserSession, error) {
	query := `
		SELECT id, user_id, device_id, device_name, device_type, ip_address,
			   last_active, created_at, is_current
		FROM user_sessions
		WHERE user_id = $1
		ORDER BY last_active DESC`

	rows, err := r.db.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var sessions []models.UserSession
	for rows.Next() {
		var session models.UserSession
		err := rows.Scan(
			&session.ID, &session.UserID, &session.DeviceID, &session.DeviceName,
			&session.DeviceType, &session.IPAddress, &session.LastActive,
			&session.CreatedAt, &session.IsCurrent,
		)
		if err != nil {
			return nil, err
		}
		sessions = append(sessions, session)
	}

	return sessions, nil
}
