package repositories

import (
	"context"
	"database/sql"

	"github.com/lib/pq"
	"github.com/vtstv/nexy/internal/models"
)

type ReactionRepository struct {
	db *sql.DB
}

func NewReactionRepository(db *sql.DB) *ReactionRepository {
	return &ReactionRepository{db: db}
}

func (r *ReactionRepository) AddReaction(ctx context.Context, reaction *models.MessageReaction) error {
	query := `
		INSERT INTO message_reactions (message_id, user_id, emoji)
		VALUES ($1, $2, $3)
		ON CONFLICT (message_id, user_id, emoji) DO NOTHING
		RETURNING id, created_at
	`
	return r.db.QueryRowContext(ctx, query, reaction.MessageID, reaction.UserID, reaction.Emoji).
		Scan(&reaction.ID, &reaction.CreatedAt)
}

// HasUserReaction checks if a user already has a specific reaction on a message
func (r *ReactionRepository) HasUserReaction(ctx context.Context, messageID, userID int, emoji string) (bool, error) {
	query := `SELECT EXISTS(SELECT 1 FROM message_reactions WHERE message_id = $1 AND user_id = $2 AND emoji = $3)`
	var exists bool
	err := r.db.QueryRowContext(ctx, query, messageID, userID, emoji).Scan(&exists)
	return exists, err
}

// EmojiExistsOnMessage checks if any user has reacted with this emoji on this message
func (r *ReactionRepository) EmojiExistsOnMessage(ctx context.Context, messageID int, emoji string) (bool, error) {
	query := `SELECT EXISTS(SELECT 1 FROM message_reactions WHERE message_id = $1 AND emoji = $2)`
	var exists bool
	err := r.db.QueryRowContext(ctx, query, messageID, emoji).Scan(&exists)
	return exists, err
}

// GetUserOwnReactionEmoji returns the emoji of user's "own" reaction (first one they created that others haven't joined)
// If user has multiple reactions, returns the first one that is NOT shared with others, or empty string if all are shared
func (r *ReactionRepository) GetUserOwnReactionEmoji(ctx context.Context, messageID, userID int) (string, error) {
	// Get all emojis this user has reacted with
	query := `SELECT emoji FROM message_reactions WHERE message_id = $1 AND user_id = $2 ORDER BY created_at ASC`
	rows, err := r.db.QueryContext(ctx, query, messageID, userID)
	if err != nil {
		return "", err
	}
	defer rows.Close()

	var userEmojis []string
	for rows.Next() {
		var emoji string
		if err := rows.Scan(&emoji); err != nil {
			return "", err
		}
		userEmojis = append(userEmojis, emoji)
	}

	if len(userEmojis) == 0 {
		return "", nil
	}

	// Return the first emoji (oldest) - this is considered the user's "own" reaction
	return userEmojis[0], nil
}

// RemoveUserOwnReaction removes user's "own" reaction (the first/oldest one they created)
func (r *ReactionRepository) RemoveUserOwnReaction(ctx context.Context, messageID, userID int, emoji string) error {
	query := `DELETE FROM message_reactions WHERE message_id = $1 AND user_id = $2 AND emoji = $3`
	_, err := r.db.ExecContext(ctx, query, messageID, userID, emoji)
	return err
}

func (r *ReactionRepository) RemoveReaction(ctx context.Context, messageID, userID int, emoji string) error {
	query := `DELETE FROM message_reactions WHERE message_id = $1 AND user_id = $2 AND emoji = $3`
	_, err := r.db.ExecContext(ctx, query, messageID, userID, emoji)
	return err
}

func (r *ReactionRepository) GetReactionsByMessageID(ctx context.Context, messageID int, currentUserID int) ([]models.ReactionCount, error) {
	query := `
		SELECT 
			emoji,
			COUNT(*) as count,
			ARRAY_AGG(user_id) as user_ids,
			BOOL_OR(user_id = $2) as reacted_by
		FROM message_reactions
		WHERE message_id = $1
		GROUP BY emoji
		ORDER BY MIN(created_at)
	`

	rows, err := r.db.QueryContext(ctx, query, messageID, currentUserID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var reactions []models.ReactionCount
	for rows.Next() {
		var reaction models.ReactionCount
		var userIDsArray []byte // PostgreSQL returns array as byte array

		err := rows.Scan(&reaction.Emoji, &reaction.Count, &userIDsArray, &reaction.ReactedBy)
		if err != nil {
			return nil, err
		}

		// Parse the PostgreSQL integer array
		var userIDs []int
		rows2, err2 := r.db.QueryContext(ctx, `SELECT unnest($1::int[])`, userIDsArray)
		if err2 == nil {
			defer rows2.Close()
			for rows2.Next() {
				var uid int
				if err2 := rows2.Scan(&uid); err2 == nil {
					userIDs = append(userIDs, uid)
				}
			}
		}
		reaction.UserIDs = userIDs

		reactions = append(reactions, reaction)
	}

	return reactions, rows.Err()
}

func (r *ReactionRepository) GetReactionsByMessageIDs(ctx context.Context, messageIDs []int, currentUserID int) (map[int][]models.ReactionCount, error) {
	if len(messageIDs) == 0 {
		return make(map[int][]models.ReactionCount), nil
	}

	query := `
		SELECT 
			message_id,
			emoji,
			COUNT(*) as count,
			ARRAY_AGG(user_id) as user_ids,
			BOOL_OR(user_id = $2) as reacted_by
		FROM message_reactions
		WHERE message_id = ANY($1)
		GROUP BY message_id, emoji
		ORDER BY message_id, MIN(created_at)
	`

	rows, err := r.db.QueryContext(ctx, query, pq.Array(messageIDs), currentUserID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := make(map[int][]models.ReactionCount)
	for rows.Next() {
		var messageID int
		var reaction models.ReactionCount
		var userIDsArray []byte

		err := rows.Scan(&messageID, &reaction.Emoji, &reaction.Count, &userIDsArray, &reaction.ReactedBy)
		if err != nil {
			return nil, err
		}

		// Parse the PostgreSQL integer array
		var userIDs []int
		rows2, err2 := r.db.QueryContext(ctx, `SELECT unnest($1::int[])`, userIDsArray)
		if err2 == nil {
			defer rows2.Close()
			for rows2.Next() {
				var uid int
				if err2 := rows2.Scan(&uid); err2 == nil {
					userIDs = append(userIDs, uid)
				}
			}
		}
		reaction.UserIDs = userIDs

		result[messageID] = append(result[messageID], reaction)
	}

	return result, rows.Err()
}
