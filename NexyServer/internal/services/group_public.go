/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"errors"

	"github.com/vtstv/nexy/internal/models"
)

// JoinPublicGroup allows a user to join a public group
func (s *GroupService) JoinPublicGroup(ctx context.Context, groupID, userID int) (*models.Chat, error) {
	chat, err := s.chatRepo.GetByID(ctx, groupID)
	if err != nil {
		return nil, err
	}
	if chat == nil {
		return nil, errors.New("group not found")
	}

	if chat.GroupType != "public_group" {
		return nil, errors.New("group is not public")
	}

	// Check if user is banned
	isBanned, err := s.chatRepo.IsBanned(ctx, groupID, userID)
	if err != nil {
		return nil, err
	}
	if isBanned {
		return nil, errors.New("you are banned from this group")
	}

	isMember, err := s.chatRepo.IsMember(ctx, groupID, userID)
	if err != nil {
		return nil, err
	}

	if isMember {
		return nil, errors.New("already a member")
	}

	member := &models.ChatMember{
		ChatID:      groupID,
		UserID:      userID,
		Role:        "member",
		Permissions: chat.DefaultPermissions,
	}

	if err := s.chatRepo.AddMember(ctx, member); err != nil {
		return nil, err
	}

	return chat, nil
}

// JoinGroupByUsername allows a user to join a public group by its username
func (s *GroupService) JoinGroupByUsername(ctx context.Context, username string, userID int) (*models.Chat, error) {
	chat, err := s.chatRepo.GetByUsername(ctx, username)
	if err != nil {
		return nil, err
	}

	if chat.GroupType != "public_group" {
		return nil, errors.New("group is not public")
	}

	return s.JoinPublicGroup(ctx, chat.ID, userID)
}

// SearchPublicGroups searches for public groups by query
func (s *GroupService) SearchPublicGroups(ctx context.Context, query string, limit, userID int) ([]*models.Chat, error) {
	return s.chatRepo.SearchPublicGroups(ctx, query, limit, userID)
}
