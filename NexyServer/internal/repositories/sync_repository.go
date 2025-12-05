/*
 * © 2025 Murr | https://github.com/vtstv
 */
package repositories

import (
	"context"
	"database/sql"
	"encoding/json"
	"time"

	"github.com/lib/pq"
	"github.com/vtstv/nexy/internal/models"
)

type SyncRepository struct {
	db *sql.DB
}

func NewSyncRepository(db *sql.DB) *SyncRepository {
	return &SyncRepository{db: db}
}

// GetUserSyncState returns the current sync state for a user
func (r *SyncRepository) GetUserSyncState(ctx context.Context, userID int) (*models.SyncState, error) {
	query := `SELECT pts, date FROM user_sync_state WHERE user_id = $1`

	var state models.SyncState
	err := r.db.QueryRowContext(ctx, query, userID).Scan(&state.Pts, &state.Date)
	if err == sql.ErrNoRows {
		// Return initial state
		return &models.SyncState{Pts: 0, Date: time.Now()}, nil
	}
	if err != nil {
		return nil, err
	}
	return &state, nil
}

// UpdateUserSyncState updates the sync state for a user
func (r *SyncRepository) UpdateUserSyncState(ctx context.Context, userID int, pts int) error {
	query := `
		INSERT INTO user_sync_state (user_id, pts, date, updated_at)
		VALUES ($1, $2, NOW(), NOW())
		ON CONFLICT (user_id) DO UPDATE
		SET pts = GREATEST(user_sync_state.pts, $2), date = NOW(), updated_at = NOW()`

	_, err := r.db.ExecContext(ctx, query, userID, pts)
	return err
}

// GetChannelSyncState returns the sync state for a specific channel/chat
func (r *SyncRepository) GetChannelSyncState(ctx context.Context, userID, chatID int) (int, error) {
	query := `SELECT pts FROM channel_sync_state WHERE user_id = $1 AND chat_id = $2`

	var pts int
	err := r.db.QueryRowContext(ctx, query, userID, chatID).Scan(&pts)
	if err == sql.ErrNoRows {
		return 0, nil
	}
	return pts, err
}

// UpdateChannelSyncState updates the sync state for a channel
func (r *SyncRepository) UpdateChannelSyncState(ctx context.Context, userID, chatID, pts int) error {
	query := `
		INSERT INTO channel_sync_state (user_id, chat_id, pts, updated_at)
		VALUES ($1, $2, $3, NOW())
		ON CONFLICT (user_id, chat_id) DO UPDATE
		SET pts = GREATEST(channel_sync_state.pts, $3), updated_at = NOW()`

	_, err := r.db.ExecContext(ctx, query, userID, chatID, pts)
	return err
}

// GetCurrentPts returns the current maximum pts value
func (r *SyncRepository) GetCurrentPts(ctx context.Context) (int, error) {
	query := `SELECT COALESCE(MAX(pts), 0) FROM messages`
	var pts int
	err := r.db.QueryRowContext(ctx, query).Scan(&pts)
	return pts, err
}

// GetDifference returns updates since the given pts
// Similar to Telegram's updates.getDifference
func (r *SyncRepository) GetDifference(ctx context.Context, userID int, fromPts int, limit int) (*models.UpdatesDifference, error) {
	if limit <= 0 {
		limit = 100
	}
	if limit > 1000 {
		limit = 1000
	}

	// Get user's chats
	chatIDs, err := r.getUserChatIDs(ctx, userID)
	if err != nil {
		return nil, err
	}
	if len(chatIDs) == 0 {
		currentPts, _ := r.GetCurrentPts(ctx)
		return &models.UpdatesDifference{
			NewMessages: []*models.Message{},
			State:       models.SyncState{Pts: currentPts, Date: time.Now()},
		}, nil
	}

	// Get new messages since pts
	query := `
		SELECT m.id, m.message_id, m.chat_id, m.sender_id, m.message_type, 
		       m.content, m.media_url, m.media_type, m.file_size, m.reply_to_id,
		       m.is_edited, m.is_deleted, COALESCE(m.pts, m.id), m.created_at, m.updated_at,
		       u.id, u.username, u.email, u.display_name, u.avatar_url, u.bio
		FROM messages m
		LEFT JOIN users u ON m.sender_id = u.id
		WHERE COALESCE(m.pts, m.id) > $1
		  AND m.chat_id = ANY($2)
		  AND m.is_deleted = false
		ORDER BY m.pts ASC
		LIMIT $3`

	rows, err := r.db.QueryContext(ctx, query, fromPts, pq.Array(chatIDs), limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var messages []*models.Message
	var maxPts int = fromPts

	for rows.Next() {
		var msg models.Message
		var sender models.User
		var fileSize sql.NullInt64
		var replyToID sql.NullInt64
		var mediaURL, mediaType, content sql.NullString

		err := rows.Scan(
			&msg.ID, &msg.MessageID, &msg.ChatID, &msg.SenderID, &msg.MessageType,
			&content, &mediaURL, &mediaType, &fileSize, &replyToID,
			&msg.IsEdited, &msg.IsDeleted, &msg.Pts, &msg.CreatedAt, &msg.UpdatedAt,
			&sender.ID, &sender.Username, &sender.Email, &sender.DisplayName, &sender.AvatarURL, &sender.Bio,
		)
		if err != nil {
			return nil, err
		}

		if content.Valid {
			msg.Content = content.String
		}
		if mediaURL.Valid {
			msg.MediaURL = mediaURL.String
		}
		if mediaType.Valid {
			msg.MediaType = mediaType.String
		}
		if fileSize.Valid {
			size := fileSize.Int64
			msg.FileSize = &size
		}
		if replyToID.Valid {
			id := int(replyToID.Int64)
			msg.ReplyToID = &id
		}

		msg.Sender = &sender
		messages = append(messages, &msg)

		if msg.Pts > maxPts {
			maxPts = msg.Pts
		}
	}

	// Get current pts if no messages found
	if len(messages) == 0 {
		currentPts, _ := r.GetCurrentPts(ctx)
		if currentPts > maxPts {
			maxPts = currentPts
		}
	}

	return &models.UpdatesDifference{
		NewMessages: messages,
		State:       models.SyncState{Pts: maxPts, Date: time.Now()},
	}, nil
}

// GetChannelDifference returns updates for a specific channel since the given pts
func (r *SyncRepository) GetChannelDifference(ctx context.Context, userID, chatID int, fromPts int, limit int) (*models.ChannelDifference, error) {
	if limit <= 0 {
		limit = 100
	}
	if limit > 500 {
		limit = 500
	}

	query := `
		SELECT m.id, m.message_id, m.chat_id, m.sender_id, m.message_type,
		       m.content, m.media_url, m.media_type, m.file_size, m.reply_to_id,
		       m.is_edited, m.is_deleted, COALESCE(m.pts, m.id), m.created_at, m.updated_at,
		       u.id, u.username, u.email, u.display_name, u.avatar_url, u.bio
		FROM messages m
		LEFT JOIN users u ON m.sender_id = u.id
		WHERE m.chat_id = $1
		  AND COALESCE(m.pts, m.id) > $2
		  AND m.is_deleted = false
		ORDER BY m.pts ASC
		LIMIT $3`

	rows, err := r.db.QueryContext(ctx, query, chatID, fromPts, limit+1) // +1 to check if more exists
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var messages []*models.Message
	var maxPts int = fromPts
	count := 0

	for rows.Next() {
		count++
		if count > limit {
			// More messages exist
			break
		}

		var msg models.Message
		var sender models.User
		var fileSize sql.NullInt64
		var replyToID sql.NullInt64
		var mediaURL, mediaType, content sql.NullString

		err := rows.Scan(
			&msg.ID, &msg.MessageID, &msg.ChatID, &msg.SenderID, &msg.MessageType,
			&content, &mediaURL, &mediaType, &fileSize, &replyToID,
			&msg.IsEdited, &msg.IsDeleted, &msg.Pts, &msg.CreatedAt, &msg.UpdatedAt,
			&sender.ID, &sender.Username, &sender.Email, &sender.DisplayName, &sender.AvatarURL, &sender.Bio,
		)
		if err != nil {
			return nil, err
		}

		if content.Valid {
			msg.Content = content.String
		}
		if mediaURL.Valid {
			msg.MediaURL = mediaURL.String
		}
		if mediaType.Valid {
			msg.MediaType = mediaType.String
		}
		if fileSize.Valid {
			size := fileSize.Int64
			msg.FileSize = &size
		}
		if replyToID.Valid {
			id := int(replyToID.Int64)
			msg.ReplyToID = &id
		}

		msg.Sender = &sender
		messages = append(messages, &msg)

		if msg.Pts > maxPts {
			maxPts = msg.Pts
		}
	}

	return &models.ChannelDifference{
		Final:       count <= limit,
		NewMessages: messages,
		Pts:         maxPts,
	}, nil
}

// LogUpdate logs an update for future getDifference calls
func (r *SyncRepository) LogUpdate(ctx context.Context, pts int, chatID int, updateType string, data interface{}) error {
	jsonData, err := json.Marshal(data)
	if err != nil {
		return err
	}

	query := `
		INSERT INTO updates_log (pts, chat_id, update_type, update_data, created_at)
		VALUES ($1, $2, $3, $4, NOW())`

	_, err = r.db.ExecContext(ctx, query, pts, chatID, updateType, jsonData)
	return err
}

// CleanupOldUpdates removes updates older than 7 days
func (r *SyncRepository) CleanupOldUpdates(ctx context.Context) error {
	query := `DELETE FROM updates_log WHERE created_at < NOW() - INTERVAL '7 days'`
	_, err := r.db.ExecContext(ctx, query)
	return err
}

// getUserChatIDs returns all chat IDs that a user is a member of
func (r *SyncRepository) getUserChatIDs(ctx context.Context, userID int) ([]int, error) {
	query := `SELECT chat_id FROM chat_members WHERE user_id = $1`

	rows, err := r.db.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var chatIDs []int
	for rows.Next() {
		var id int
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		chatIDs = append(chatIDs, id)
	}
	return chatIDs, nil
}
