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
func (s *GroupService) JoinPublicGroup(ctx context.Context, groupID, userID int) error {
	chat, err := s.chatRepo.GetByID(ctx, groupID)
	if err != nil {
		return err
	}

	if chat.GroupType != "public_group" {
		return errors.New("group is not public")
	}

	isMember, err := s.chatRepo.IsMember(ctx, groupID, userID)
	if err != nil {
		return err
	}

	if isMember {
		return errors.New("already a member")
	}

	member := &models.ChatMember{
		ChatID:      groupID,
		UserID:      userID,
		Role:        "member",
		Permissions: chat.DefaultPermissions,
	}

	return s.chatRepo.AddMember(ctx, member)
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

	err = s.JoinPublicGroup(ctx, chat.ID, userID)
	if err != nil {
		return nil, err
	}

	return chat, nil
}

// SearchPublicGroups searches for public groups by query
func (s *GroupService) SearchPublicGroups(ctx context.Context, query string, limit int) ([]*models.Chat, error) {
	return s.chatRepo.SearchPublicGroups(ctx, query, limit)
}
