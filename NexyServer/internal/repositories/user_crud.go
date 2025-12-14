/*
 * © 2025 Murr | https://github.com/vtstv
 */
package repositories

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"unicode"

	"github.com/lib/pq"
	"github.com/vtstv/nexy/internal/models"
)

// normalizePhoneNumber removes all non-digit characters and converts to standard format
func normalizePhoneNumber(phone string) string {
	if phone == "" {
		return ""
	}

	var result strings.Builder
	hasPlus := strings.HasPrefix(phone, "+")

	for _, r := range phone {
		if unicode.IsDigit(r) {
			result.WriteRune(r)
		}
	}

	normalized := result.String()
	if normalized == "" {
		return ""
	}

	// Handle Russian format: 8XXXXXXXXXX -> 7XXXXXXXXXX
	if len(normalized) == 11 && strings.HasPrefix(normalized, "8") {
		normalized = "7" + normalized[1:]
	}

	// Add + prefix for international format
	if hasPlus || len(normalized) >= 10 {
		return "+" + normalized
	}

	return normalized
}

// Create creates a new user
func (r *UserRepository) Create(ctx context.Context, user *models.User) error {
	query := `
		INSERT INTO users (username, email, password_hash, display_name, avatar_url, bio, phone_number)
		VALUES ($1, $2, $3, $4, $5, $6, NULLIF($7, ''))
		RETURNING id, read_receipts_enabled, typing_indicators_enabled, voice_messages_enabled, show_online_status, created_at, updated_at`

	normalizedPhone := normalizePhoneNumber(user.PhoneNumber)
	return r.db.QueryRowContext(ctx, query,
		user.Username,
		user.Email,
		user.PasswordHash,
		user.DisplayName,
		user.AvatarURL,
		user.Bio,
		normalizedPhone,
	).Scan(&user.ID, &user.ReadReceiptsEnabled, &user.TypingIndicatorsEnabled, &user.VoiceMessagesEnabled, &user.ShowOnlineStatus, &user.CreatedAt, &user.UpdatedAt)
}

// GetByID retrieves user by ID
func (r *UserRepository) GetByID(ctx context.Context, id int) (*models.User, error) {
	user := &models.User{}
	query := `
		SELECT id, username, email, password_hash, display_name, avatar_url, bio, 
		       phone_number, phone_privacy, allow_phone_discovery,
		       read_receipts_enabled, typing_indicators_enabled, voice_messages_enabled, show_online_status, last_seen, 
		       created_at, updated_at
		FROM users
		WHERE id = $1`

	var avatarURL sql.NullString
	var bio sql.NullString
	var phoneNumber sql.NullString
	var phonePrivacy sql.NullString
	var lastSeen sql.NullTime
	err := r.db.QueryRowContext(ctx, query, id).Scan(
		&user.ID,
		&user.Username,
		&user.Email,
		&user.PasswordHash,
		&user.DisplayName,
		&avatarURL,
		&bio,
		&phoneNumber,
		&phonePrivacy,
		&user.AllowPhoneDiscovery,
		&user.ReadReceiptsEnabled,
		&user.TypingIndicatorsEnabled,
		&user.VoiceMessagesEnabled,
		&user.ShowOnlineStatus,
		&lastSeen,
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
	if phoneNumber.Valid {
		user.PhoneNumber = phoneNumber.String
	}
	if phonePrivacy.Valid {
		user.PhonePrivacy = phonePrivacy.String
	} else {
		user.PhonePrivacy = "contacts"
	}
	if lastSeen.Valid {
		user.LastSeen = &lastSeen.Time
	}
	return user, err
}

// Update updates user profile
func (r *UserRepository) Update(ctx context.Context, user *models.User) error {
	query := `
		UPDATE users
		SET display_name = $1, avatar_url = $2, bio = $3, read_receipts_enabled = $4, 
		    typing_indicators_enabled = $5, voice_messages_enabled = $6, show_online_status = $7,
		    phone_number = NULLIF($8, ''), phone_privacy = $9, allow_phone_discovery = $10,
		    updated_at = CURRENT_TIMESTAMP
		WHERE id = $11
		RETURNING updated_at`

	normalizedPhone := normalizePhoneNumber(user.PhoneNumber)
	return r.db.QueryRowContext(ctx, query,
		user.DisplayName,
		user.AvatarURL,
		user.Bio,
		user.ReadReceiptsEnabled,
		user.TypingIndicatorsEnabled,
		user.VoiceMessagesEnabled,
		user.ShowOnlineStatus,
		normalizedPhone,
		user.PhonePrivacy,
		user.AllowPhoneDiscovery,
		user.ID,
	).Scan(&user.UpdatedAt)
}

// UpdateLastSeen updates user's last seen timestamp
func (r *UserRepository) UpdateLastSeen(ctx context.Context, userID int) error {
	query := `UPDATE users SET last_seen = CURRENT_TIMESTAMP WHERE id = $1`
	_, err := r.db.ExecContext(ctx, query, userID)
	return err
}

// UpdateAvatar updates user avatar URL
func (r *UserRepository) UpdateAvatar(ctx context.Context, userID int, avatarURL string) error {
	query := `UPDATE users SET avatar_url = $1, updated_at = CURRENT_TIMESTAMP WHERE id = $2`
	_, err := r.db.ExecContext(ctx, query, avatarURL, userID)
	return err
}

// GetByPhoneNumber retrieves users by phone number (respecting privacy settings)
func (r *UserRepository) GetByPhoneNumber(ctx context.Context, phoneNumber string, requestingUserID int) (*models.User, error) {
	user := &models.User{}
	normalizedPhone := normalizePhoneNumber(phoneNumber)
	if normalizedPhone == "" {
		return nil, fmt.Errorf("invalid phone number")
	}

	// Only return user if they allow phone discovery
	query := `
		SELECT id, username, email, display_name, avatar_url, bio,
		       phone_number, phone_privacy, allow_phone_discovery,
		       read_receipts_enabled, typing_indicators_enabled, voice_messages_enabled, show_online_status, last_seen,
		       created_at, updated_at
		FROM users
		WHERE phone_number = $1 AND allow_phone_discovery = TRUE`

	var avatarURL sql.NullString
	var bio sql.NullString
	var phoneNum sql.NullString
	var phonePrivacy sql.NullString
	var lastSeen sql.NullTime
	err := r.db.QueryRowContext(ctx, query, normalizedPhone).Scan(
		&user.ID,
		&user.Username,
		&user.Email,
		&user.DisplayName,
		&avatarURL,
		&bio,
		&phoneNum,
		&phonePrivacy,
		&user.AllowPhoneDiscovery,
		&user.ReadReceiptsEnabled,
		&user.TypingIndicatorsEnabled,
		&user.VoiceMessagesEnabled,
		&user.ShowOnlineStatus,
		&lastSeen,
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
	if phoneNum.Valid {
		user.PhoneNumber = phoneNum.String
	}
	if phonePrivacy.Valid {
		user.PhonePrivacy = phonePrivacy.String
	}
	if lastSeen.Valid {
		user.LastSeen = &lastSeen.Time
	}
	return user, err
}

// SearchByPhoneNumbers finds users by multiple phone numbers (for contact sync)
func (r *UserRepository) SearchByPhoneNumbers(ctx context.Context, phoneNumbers []string) ([]*models.User, error) {
	if len(phoneNumbers) == 0 {
		return []*models.User{}, nil
	}

	// Normalize all phone numbers
	normalizedPhones := make([]string, 0, len(phoneNumbers))
	for _, phone := range phoneNumbers {
		if normalized := normalizePhoneNumber(phone); normalized != "" {
			normalizedPhones = append(normalizedPhones, normalized)
		}
	}

	if len(normalizedPhones) == 0 {
		return []*models.User{}, nil
	}

	query := `
		SELECT id, username, email, display_name, avatar_url, bio,
		       phone_number, phone_privacy, allow_phone_discovery,
		       read_receipts_enabled, typing_indicators_enabled, voice_messages_enabled, show_online_status, last_seen,
		       created_at, updated_at
		FROM users
		WHERE phone_number = ANY($1) AND allow_phone_discovery = TRUE`

	rows, err := r.db.QueryContext(ctx, query, pq.Array(normalizedPhones))
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var users []*models.User
	for rows.Next() {
		user := &models.User{}
		var avatarURL sql.NullString
		var bio sql.NullString
		var phoneNum sql.NullString
		var phonePrivacy sql.NullString
		var lastSeen sql.NullTime
		err := rows.Scan(
			&user.ID,
			&user.Username,
			&user.Email,
			&user.DisplayName,
			&avatarURL,
			&bio,
			&phoneNum,
			&phonePrivacy,
			&user.AllowPhoneDiscovery,
			&user.ReadReceiptsEnabled,
			&user.TypingIndicatorsEnabled,
			&user.VoiceMessagesEnabled,
			&user.ShowOnlineStatus,
			&lastSeen,
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
		if phoneNum.Valid {
			user.PhoneNumber = phoneNum.String
		}
		if phonePrivacy.Valid {
			user.PhonePrivacy = phonePrivacy.String
		}
		if lastSeen.Valid {
			user.LastSeen = &lastSeen.Time
		}
		users = append(users, user)
	}
	return users, rows.Err()
}
