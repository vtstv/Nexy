/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"errors"

	"github.com/vtstv/nexy/internal/models"
)

// CreateGroup creates a new group with initial members
func (s *GroupService) CreateGroup(ctx context.Context, name, description, groupType, username string, creatorID int, initialMembers []int, avatarURL string) (*models.Chat, error) {
	if groupType != "private_group" && groupType != "public_group" {
		return nil, errors.New("invalid group type: must be 'private_group' or 'public_group'")
	}

	chat := &models.Chat{
		Type:        "group",
		GroupType:   groupType,
		Name:        name,
		Description: description,
		AvatarURL:   avatarURL,
		CreatedBy:   &creatorID,
		DefaultPermissions: &models.ChatPermissions{
			SendMessages: true,
			SendMedia:    true,
			AddUsers:     true,
			PinMessages:  false,
			ChangeInfo:   false,
		},
	}

	if groupType == "public_group" {
		if username == "" {
			return nil, errors.New("username is required for public groups")
		}
		chat.Username = username
	}

	err := s.chatRepo.Create(ctx, chat)
	if err != nil {
		return nil, err
	}

	owner := &models.ChatMember{
		ChatID: chat.ID,
		UserID: creatorID,
		Role:   "owner",
		Permissions: &models.ChatPermissions{
			SendMessages: true,
			SendMedia:    true,
			AddUsers:     true,
			PinMessages:  true,
			ChangeInfo:   true,
		},
	}
	s.chatRepo.AddMember(ctx, owner)

	for _, memberID := range initialMembers {
		if memberID == creatorID {
			continue
		}
		member := &models.ChatMember{
			ChatID:      chat.ID,
			UserID:      memberID,
			Role:        "member",
			Permissions: chat.DefaultPermissions,
		}
		s.chatRepo.AddMember(ctx, member)
	}

	return chat, nil
}

// GetGroup retrieves group information
func (s *GroupService) GetGroup(ctx context.Context, groupID, userID int) (*models.Chat, error) {
	chat, err := s.chatRepo.GetByID(ctx, groupID)
	if err != nil {
		return nil, err
	}
	if chat == nil {
		return nil, errors.New("group not found")
	}

	isMember, err := s.chatRepo.IsMember(ctx, groupID, userID)
	if err != nil {
		return nil, err
	}

	if !isMember {
		if chat.GroupType != "public_group" {
			return nil, errors.New("access denied")
		}
	}

	return chat, nil
}

// UpdateGroup updates group information
func (s *GroupService) UpdateGroup(ctx context.Context, groupID, userID int, name, description, username, avatarURL string) (*models.Chat, error) {
	member, err := s.chatRepo.GetChatMember(ctx, groupID, userID)
	if err != nil {
		return nil, err
	}

	if member.Role != "owner" && (member.Permissions == nil || !member.Permissions.ChangeInfo) {
		return nil, errors.New("permission denied")
	}

	chat, err := s.chatRepo.GetByID(ctx, groupID)
	if err != nil {
		return nil, err
	}
	if chat == nil {
		return nil, errors.New("group not found")
	}

	if chat.GroupType == "public_group" && username == "" {
		return nil, errors.New("public groups must have a username")
	}

	chat.Name = name
	chat.Description = description
	chat.Username = username
	chat.AvatarURL = avatarURL

	err = s.chatRepo.UpdateChat(ctx, chat)
	if err != nil {
		return nil, err
	}

	return chat, nil
}
