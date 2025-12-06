package repositories

import (
	"context"
	"database/sql"
	"time"

	"github.com/vtstv/nexy/internal/database"
	"github.com/vtstv/nexy/internal/models"
)

type SessionRepository struct {
	db *database.DB
}

func NewSessionRepository(db *database.DB) *SessionRepository {
	return &SessionRepository{db: db}
}

func (r *SessionRepository) Create(ctx context.Context, session *models.UserSession) error {
	query := `
		INSERT INTO user_sessions (user_id, refresh_token_id, device_name, device_type, ip_address, user_agent, is_current)
		VALUES ($1, $2, $3, $4, $5, $6, $7)
		RETURNING id, last_active, created_at`

	return r.db.QueryRowContext(ctx, query,
		session.UserID,
		session.RefreshTokenID,
		session.DeviceName,
		session.DeviceType,
		session.IPAddress,
		session.UserAgent,
		session.IsCurrent,
	).Scan(&session.ID, &session.LastActive, &session.CreatedAt)
}

func (r *SessionRepository) CreateFromLogin(ctx context.Context, userID int, deviceID, deviceName, deviceType, ipAddress, userAgent string) error {
	query := `
		INSERT INTO user_sessions (user_id, device_id, device_name, device_type, ip_address, user_agent, is_current, last_active)
		VALUES ($1, $2, $3, $4, $5, $6, TRUE, NOW())
		RETURNING id`

	var id int
	return r.db.QueryRowContext(ctx, query,
		userID,
		deviceID,
		deviceName,
		deviceType,
		ipAddress,
		userAgent,
	).Scan(&id)
}

func (r *SessionRepository) GetByUserID(ctx context.Context, userID int) ([]models.UserSession, error) {
	query := `
		SELECT id, user_id, refresh_token_id, device_id, device_name, device_type, ip_address, user_agent, last_active, created_at, is_current
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
		var s models.UserSession
		var refreshTokenID sql.NullInt64
		if err := rows.Scan(
			&s.ID,
			&s.UserID,
			&refreshTokenID,
			&s.DeviceID,
			&s.DeviceName,
			&s.DeviceType,
			&s.IPAddress,
			&s.UserAgent,
			&s.LastActive,
			&s.CreatedAt,
			&s.IsCurrent,
		); err != nil {
			return nil, err
		}
		if refreshTokenID.Valid {
			id := int(refreshTokenID.Int64)
			s.RefreshTokenID = &id
		}
		sessions = append(sessions, s)
	}
	return sessions, rows.Err()
}

func (r *SessionRepository) UpdateLastActive(ctx context.Context, sessionID int) error {
	query := `UPDATE user_sessions SET last_active = $1 WHERE id = $2`
	_, err := r.db.ExecContext(ctx, query, time.Now(), sessionID)
	return err
}

func (r *SessionRepository) Delete(ctx context.Context, sessionID int) error {
	query := `DELETE FROM user_sessions WHERE id = $1`
	_, err := r.db.ExecContext(ctx, query, sessionID)
	return err
}

func (r *SessionRepository) DeleteByRefreshToken(ctx context.Context, refreshTokenID int) error {
	query := `DELETE FROM user_sessions WHERE refresh_token_id = $1`
	_, err := r.db.ExecContext(ctx, query, refreshTokenID)
	return err
}

func (r *SessionRepository) DeleteAllExceptCurrent(ctx context.Context, userID int, currentSessionID int) error {
	query := `DELETE FROM user_sessions WHERE user_id = $1 AND id != $2`
	_, err := r.db.ExecContext(ctx, query, userID, currentSessionID)
	return err
}

func (r *SessionRepository) SetCurrentSession(ctx context.Context, userID int, sessionID int) error {
	tx, err := r.db.BeginTx(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	_, err = tx.ExecContext(ctx, `UPDATE user_sessions SET is_current = FALSE WHERE user_id = $1`, userID)
	if err != nil {
		return err
	}

	_, err = tx.ExecContext(ctx, `UPDATE user_sessions SET is_current = TRUE WHERE id = $1`, sessionID)
	if err != nil {
		return err
	}

	return tx.Commit()
}

func (r *SessionRepository) GetByRefreshTokenID(ctx context.Context, tokenID int) (*models.UserSession, error) {
	query := `
		SELECT id, user_id, refresh_token_id, device_name, device_type, ip_address, user_agent, last_active, created_at, is_current
		FROM user_sessions
		WHERE refresh_token_id = $1`

	var s models.UserSession
	var refreshTokenID sql.NullInt64
	err := r.db.QueryRowContext(ctx, query, tokenID).Scan(
		&s.ID,
		&s.UserID,
		&refreshTokenID,
		&s.DeviceName,
		&s.DeviceType,
		&s.IPAddress,
		&s.UserAgent,
		&s.LastActive,
		&s.CreatedAt,
		&s.IsCurrent,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	if refreshTokenID.Valid {
		id := int(refreshTokenID.Int64)
		s.RefreshTokenID = &id
	}
	return &s, nil
}
