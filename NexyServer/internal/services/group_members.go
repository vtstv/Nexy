/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"errors"

	"github.com/vtstv/nexy/internal/models"
)

// GetGroupMembers retrieves list of group members
func (s *GroupService) GetGroupMembers(ctx context.Context, groupID, userID int, query string) ([]*models.ChatMember, error) {
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

	if !isMember && chat.GroupType != "public_group" {
		return nil, errors.New("access denied")
	}

	if query != "" {
		return s.chatRepo.GetChatMembersWithSearch(ctx, groupID, query)
	}

	members, err := s.chatRepo.GetChatMembersFull(ctx, groupID)
	if err != nil {
		return nil, err
	}

	// Get requesting user for privacy filter
	requestingUser, _ := s.userRepo.GetByID(ctx, userID)

	// Load user information for each member
	for _, member := range members {
		user, err := s.userRepo.GetByID(ctx, member.UserID)
		if err == nil {
			member.User = user

			// Apply online status with privacy filter
			if s.onlineStatusService != nil && requestingUser != nil {
				isOnline := false
				if s.onlineChecker != nil {
					isOnline = s.onlineChecker.IsUserOnline(member.UserID)
				}
				member.User.OnlineStatus = s.onlineStatusService.ApplyPrivacyFilter(
					requestingUser,
					member.User,
					isOnline,
				)
			}
		}
	}

	return members, nil
}

// AddMember adds a new member to the group
func (s *GroupService) AddMember(ctx context.Context, groupID, requestorID, targetUserID int) error {
	// Check permissions
	requestor, err := s.chatRepo.GetChatMember(ctx, groupID, requestorID)
	if err != nil {
		return err
	}

	if requestor.Role != "owner" && requestor.Role != "admin" {
		if requestor.Permissions == nil || !requestor.Permissions.AddUsers {
			return errors.New("permission denied")
		}
	}

	// Check if already member
	isMember, err := s.chatRepo.IsMember(ctx, groupID, targetUserID)
	if err != nil {
		return err
	}
	if isMember {
		return errors.New("user is already a member")
	}

	// Get chat to get default permissions
	chat, err := s.chatRepo.GetByID(ctx, groupID)
	if err != nil {
		return err
	}
	if chat == nil {
		return errors.New("group not found")
	}

	member := &models.ChatMember{
		ChatID:      groupID,
		UserID:      targetUserID,
		Role:        "member",
		Permissions: chat.DefaultPermissions,
	}

	return s.chatRepo.AddMember(ctx, member)
}

// RemoveMember removes a member from the group
func (s *GroupService) RemoveMember(ctx context.Context, groupID, requestorID, targetUserID int) error {
	requestor, err := s.chatRepo.GetChatMember(ctx, groupID, requestorID)
	if err != nil {
		return err
	}

	if requestor.Role != "owner" && requestor.Role != "admin" {
		if requestorID != targetUserID {
			return errors.New("permission denied")
		}
	}

	err = s.chatRepo.RemoveMember(ctx, groupID, targetUserID)
	if err != nil {
		return err
	}

	// Check if group is empty
	members, err := s.chatRepo.GetChatMembers(ctx, groupID)
	if err != nil {
		return nil // Ignore error, just don't delete
	}

	if len(members) == 0 {
		return s.chatRepo.DeleteChat(ctx, groupID)
	}

	return nil
}

// UpdateMemberRole updates member's role in the group
func (s *GroupService) UpdateMemberRole(ctx context.Context, groupID, requestorID, targetUserID int, role string) error {
	requestor, err := s.chatRepo.GetChatMember(ctx, groupID, requestorID)
	if err != nil {
		return err
	}

	if requestor.Role != "owner" {
		return errors.New("permission denied")
	}

	return s.chatRepo.UpdateMemberRole(ctx, groupID, targetUserID, role)
}

// TransferOwnership transfers group ownership to another member
func (s *GroupService) TransferOwnership(ctx context.Context, groupID, currentOwnerID, newOwnerID int) error {
	currentOwner, err := s.chatRepo.GetChatMember(ctx, groupID, currentOwnerID)
	if err != nil {
		return err
	}

	if currentOwner.Role != "owner" {
		return errors.New("permission denied: only owner can transfer ownership")
	}

	_, err = s.chatRepo.GetChatMember(ctx, groupID, newOwnerID)
	if err != nil {
		return errors.New("new owner is not a member of the group")
	}

	if err := s.chatRepo.UpdateMemberRole(ctx, groupID, currentOwnerID, "admin"); err != nil {
		return err
	}

	if err := s.chatRepo.UpdateMemberRole(ctx, groupID, newOwnerID, "owner"); err != nil {
		s.chatRepo.UpdateMemberRole(ctx, groupID, currentOwnerID, "owner")
		return err
	}

	chat, err := s.chatRepo.GetByID(ctx, groupID)
	if err == nil {
		chat.CreatedBy = &newOwnerID
		s.chatRepo.UpdateChat(ctx, chat)
	}

	return nil
}
