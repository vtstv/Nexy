/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"fmt"

	"golang.org/x/crypto/bcrypt"

	"github.com/vtstv/nexy/internal/models"
)

// GetUserByID retrieves user by ID
func (s *UserService) GetUserByID(ctx context.Context, id int) (*models.User, error) {
	return s.userRepo.GetByID(ctx, id)
}

// SearchUsers searches for users by query
func (s *UserService) SearchUsers(ctx context.Context, query string, limit int) ([]*models.User, error) {
	if limit <= 0 || limit > 50 {
		limit = 20
	}
	return s.userRepo.Search(ctx, query, limit)
}

// UpdateProfile updates user profile information
func (s *UserService) UpdateProfile(ctx context.Context, userID int, displayName, bio, avatarURL, email, password string, readReceiptsEnabled, typingIndicatorsEnabled, showOnlineStatus *bool) (*models.User, error) {
	user, err := s.userRepo.GetByID(ctx, userID)
	if err != nil {
		return nil, err
	}

	user.DisplayName = displayName
	user.Bio = bio
	if avatarURL != "" {
		user.AvatarURL = avatarURL
	}
	if email != "" {
		user.Email = email
	}
	if password != "" {
		hashedPassword, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
		if err != nil {
			return nil, fmt.Errorf("failed to hash password: %w", err)
		}
		user.PasswordHash = string(hashedPassword)
	}
	if readReceiptsEnabled != nil {
		user.ReadReceiptsEnabled = *readReceiptsEnabled
	}
	if typingIndicatorsEnabled != nil {
		user.TypingIndicatorsEnabled = *typingIndicatorsEnabled
	}
	if showOnlineStatus != nil {
		user.ShowOnlineStatus = *showOnlineStatus
	}

	if err := s.userRepo.Update(ctx, user); err != nil {
		return nil, err
	}

	return user, nil
}

// UpdateAvatar updates user avatar URL
func (s *UserService) UpdateAvatar(ctx context.Context, userID int, avatarURL string) error {
	return s.userRepo.UpdateAvatar(ctx, userID, avatarURL)
}
