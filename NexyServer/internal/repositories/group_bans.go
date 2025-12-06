/*
 * © 2025 Murr | https://github.com/vtstv
 */
package repositories

import (
	"context"
	"time"
)

type GroupBan struct {
	ID       int
	ChatID   int
	UserID   int
	BannedBy int
	Reason   string
	BannedAt time.Time
}

// BanUser adds a user to the ban list
func (r *ChatRepository) BanUser(ctx context.Context, chatID, userID, bannedBy int, reason string) error {
	query := `
		INSERT INTO group_bans (chat_id, user_id, banned_by, reason)
		VALUES ($1, $2, $3, $4)
		ON CONFLICT (chat_id, user_id) DO UPDATE SET
			banned_by = EXCLUDED.banned_by,
			reason = EXCLUDED.reason,
			banned_at = CURRENT_TIMESTAMP`
	_, err := r.db.ExecContext(ctx, query, chatID, userID, bannedBy, reason)
	return err
}

// UnbanUser removes a user from the ban list
func (r *ChatRepository) UnbanUser(ctx context.Context, chatID, userID int) error {
	query := `DELETE FROM group_bans WHERE chat_id = $1 AND user_id = $2`
	_, err := r.db.ExecContext(ctx, query, chatID, userID)
	return err
}

// IsBanned checks if a user is banned from a chat
func (r *ChatRepository) IsBanned(ctx context.Context, chatID, userID int) (bool, error) {
	query := `SELECT EXISTS(SELECT 1 FROM group_bans WHERE chat_id = $1 AND user_id = $2)`
	var exists bool
	err := r.db.QueryRowContext(ctx, query, chatID, userID).Scan(&exists)
	return exists, err
}

// GetBannedUsers returns list of banned users for a chat
func (r *ChatRepository) GetBannedUsers(ctx context.Context, chatID int) ([]*GroupBan, error) {
	query := `
		SELECT id, chat_id, user_id, banned_by, COALESCE(reason, ''), banned_at
		FROM group_bans
		WHERE chat_id = $1
		ORDER BY banned_at DESC`
	
	rows, err := r.db.QueryContext(ctx, query, chatID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	
	var bans []*GroupBan
	for rows.Next() {
		ban := &GroupBan{}
		if err := rows.Scan(&ban.ID, &ban.ChatID, &ban.UserID, &ban.BannedBy, &ban.Reason, &ban.BannedAt); err != nil {
			return nil, err
		}
		bans = append(bans, ban)
	}
	return bans, nil
}
