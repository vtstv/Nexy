/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"fmt"
	"time"

	"github.com/vtstv/nexy/internal/models"
)

// GetOrCreatePrivateChat gets or creates a private chat between two users
func (s *UserService) GetOrCreatePrivateChat(ctx context.Context, user1ID, user2ID int) (*models.Chat, error) {
	if user1ID == user2ID {
		// Handle Self Chat (Notepad)
		chat, err := s.chatRepo.GetSelfChat(ctx, user1ID)
		if err != nil {
			return nil, err
		}

		if chat != nil {
			return chat, nil
		}

		newChat := &models.Chat{
			Type: "private",
		}

		if err := s.chatRepo.Create(ctx, newChat); err != nil {
			return nil, fmt.Errorf("failed to create chat: %w", err)
		}

		member := &models.ChatMember{
			ChatID: newChat.ID,
			UserID: user1ID,
			Role:   "member",
		}

		if err := s.chatRepo.AddMember(ctx, member); err != nil {
			return nil, fmt.Errorf("failed to add member: %w", err)
		}

		newChat.ParticipantIds = []int{user1ID}

		return newChat, nil
	}

	chat, err := s.chatRepo.GetPrivateChatBetween(ctx, user1ID, user2ID)
	if err != nil {
		return nil, err
	}

	if chat != nil {
		return chat, nil
	}

	newChat := &models.Chat{
		Type: "private",
	}

	if err := s.chatRepo.Create(ctx, newChat); err != nil {
		return nil, fmt.Errorf("failed to create chat: %w", err)
	}

	member1 := &models.ChatMember{
		ChatID: newChat.ID,
		UserID: user1ID,
		Role:   "member",
	}

	if err := s.chatRepo.AddMember(ctx, member1); err != nil {
		return nil, fmt.Errorf("failed to add member 1: %w", err)
	}

	member2 := &models.ChatMember{
		ChatID: newChat.ID,
		UserID: user2ID,
		Role:   "member",
	}

	if err := s.chatRepo.AddMember(ctx, member2); err != nil {
		return nil, fmt.Errorf("failed to add member 2: %w", err)
	}

	newChat.ParticipantIds = []int{user1ID, user2ID}

	return newChat, nil
}

// CreateGroupChat creates a new group chat
func (s *UserService) CreateGroupChat(ctx context.Context, creatorID int, name string, memberIDs []int) (*models.Chat, error) {
	if name == "" {
		return nil, fmt.Errorf("group name is required")
	}

	// Create group chat
	groupChat := &models.Chat{
		Type:      "group",
		Name:      name,
		CreatedBy: &creatorID,
	}

	if err := s.chatRepo.Create(ctx, groupChat); err != nil {
		return nil, fmt.Errorf("failed to create group chat: %w", err)
	}

	// Add creator as admin
	creatorMember := &models.ChatMember{
		ChatID: groupChat.ID,
		UserID: creatorID,
		Role:   "admin",
	}

	if err := s.chatRepo.AddMember(ctx, creatorMember); err != nil {
		return nil, fmt.Errorf("failed to add creator: %w", err)
	}

	// Add other members
	for _, memberID := range memberIDs {
		if memberID == creatorID {
			continue // Skip creator, already added as admin
		}

		member := &models.ChatMember{
			ChatID: groupChat.ID,
			UserID: memberID,
			Role:   "member",
		}

		if err := s.chatRepo.AddMember(ctx, member); err != nil {
			return nil, fmt.Errorf("failed to add member %d: %w", memberID, err)
		}
	}

	return groupChat, nil
}

// GetUserChats retrieves all chats for a user
func (s *UserService) GetUserChats(ctx context.Context, userID int) ([]*models.Chat, error) {
	return s.chatRepo.GetUserChats(ctx, userID)
}

// GetChat retrieves a specific chat for a user
func (s *UserService) GetChat(ctx context.Context, userID, chatID int) (*models.Chat, error) {
	// Check if user is member
	isMember, err := s.chatRepo.IsMember(ctx, chatID, userID)
	if err != nil {
		return nil, err
	}

	chat, err := s.chatRepo.GetByID(ctx, chatID)
	if err != nil {
		return nil, err
	}

	if !isMember {
		// If not member, check if it's a public group
		if chat.GroupType != "public_group" {
			return nil, fmt.Errorf("access denied: user %d is not a member of chat %d and chat is not public", userID, chatID)
		}
	} else {
		// Get member details to check muted status
		member, err := s.chatRepo.GetChatMember(ctx, chatID, userID)
		if err == nil {
			chat.MutedUntil = member.MutedUntil
		}
	}

	chat.IsMember = isMember
	return chat, nil
}

// DeleteChat deletes a chat
func (s *UserService) DeleteChat(ctx context.Context, userID, chatID int) error {
	// Check if user is a member of the chat
	isMember, err := s.chatRepo.IsMember(ctx, chatID, userID)
	if err != nil {
		return fmt.Errorf("failed to check membership: %w", err)
	}
	if !isMember {
		return fmt.Errorf("user is not a member of this chat")
	}

	// Delete the chat (this will cascade delete members and messages)
	return s.chatRepo.DeleteChat(ctx, chatID)
}

// ClearChatMessages clears all messages in a chat
func (s *UserService) ClearChatMessages(ctx context.Context, userID, chatID int) error {
	// Check if user is a member of the chat
	isMember, err := s.chatRepo.IsMember(ctx, chatID, userID)
	if err != nil {
		return fmt.Errorf("failed to check membership: %w", err)
	}
	if !isMember {
		return fmt.Errorf("user is not a member of this chat")
	}

	// Clear all messages from the chat
	return s.chatRepo.ClearMessages(ctx, chatID)
}

// MuteChat mutes a chat for a user
func (s *UserService) MuteChat(ctx context.Context, userID, chatID int, until *time.Time) error {
	// Check if user is member
	isMember, err := s.chatRepo.IsMember(ctx, chatID, userID)
	if err != nil {
		return err
	}
	if !isMember {
		return fmt.Errorf("user %d is not a member of chat %d", userID, chatID)
	}

	return s.chatRepo.MuteChat(ctx, chatID, userID, until)
}

// UnmuteChat unmutes a chat for a user
func (s *UserService) UnmuteChat(ctx context.Context, userID, chatID int) error {
	// Check if user is member
	isMember, err := s.chatRepo.IsMember(ctx, chatID, userID)
	if err != nil {
		return err
	}
	if !isMember {
		return fmt.Errorf("user %d is not a member of chat %d", userID, chatID)
	}

	return s.chatRepo.MuteChat(ctx, chatID, userID, nil)
}
