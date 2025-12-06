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

// RemoveMember removes a member from the group (kick)
func (s *GroupService) RemoveMember(ctx context.Context, groupID, requestorID, targetUserID int) error {
	requestor, err := s.chatRepo.GetChatMember(ctx, groupID, requestorID)
	if err != nil {
		return err
	}

	// Only owners and admins can kick others
	if requestor.Role != "owner" && requestor.Role != "admin" {
		// Users can only leave themselves
		if requestorID != targetUserID {
			return errors.New("permission denied")
		}
	}

	// Admins cannot kick owners or other admins
	if requestor.Role == "admin" {
		target, err := s.chatRepo.GetChatMember(ctx, groupID, targetUserID)
		if err != nil {
			return err
		}
		if target.Role == "owner" || target.Role == "admin" {
			return errors.New("permission denied: cannot kick owner or admin")
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

// BanMember bans a user from the group
func (s *GroupService) BanMember(ctx context.Context, groupID, requestorID, targetUserID int, reason string) error {
	requestor, err := s.chatRepo.GetChatMember(ctx, groupID, requestorID)
	if err != nil {
		return err
	}

	// Only owners and admins can ban
	if requestor.Role != "owner" && requestor.Role != "admin" {
		return errors.New("permission denied")
	}

	// Admins cannot ban owners or other admins
	if requestor.Role == "admin" {
		target, err := s.chatRepo.GetChatMember(ctx, groupID, targetUserID)
		if err == nil && (target.Role == "owner" || target.Role == "admin") {
			return errors.New("permission denied: cannot ban owner or admin")
		}
	}

	// Cannot ban yourself
	if requestorID == targetUserID {
		return errors.New("cannot ban yourself")
	}

	// Remove from group first
	s.chatRepo.RemoveMember(ctx, groupID, targetUserID)

	// Add to ban list
	return s.chatRepo.BanUser(ctx, groupID, targetUserID, requestorID, reason)
}

// UnbanMember removes a ban from a user
func (s *GroupService) UnbanMember(ctx context.Context, groupID, requestorID, targetUserID int) error {
	requestor, err := s.chatRepo.GetChatMember(ctx, groupID, requestorID)
	if err != nil {
		return err
	}

	// Only owners and admins can unban
	if requestor.Role != "owner" && requestor.Role != "admin" {
		return errors.New("permission denied")
	}

	return s.chatRepo.UnbanUser(ctx, groupID, targetUserID)
}

// GroupBanWithUser represents a ban with user info
type GroupBanWithUser struct {
	ID         int          `json:"id"`
	ChatID     int          `json:"chat_id"`
	UserID     int          `json:"user_id"`
	BannedBy   int          `json:"banned_by"`
	Reason     string       `json:"reason"`
	BannedAt   string       `json:"banned_at"`
	User       *models.User `json:"user"`
	BannedByUser *models.User `json:"banned_by_user"`
}

// GetBannedMembers returns list of banned users in a group with user details
func (s *GroupService) GetBannedMembers(ctx context.Context, groupID, requestorID int) ([]*GroupBanWithUser, error) {
	requestor, err := s.chatRepo.GetChatMember(ctx, groupID, requestorID)
	if err != nil {
		return nil, err
	}

	// Only owners and admins can see ban list
	if requestor.Role != "owner" && requestor.Role != "admin" {
		return nil, errors.New("permission denied")
	}

	bans, err := s.chatRepo.GetBannedUsers(ctx, groupID)
	if err != nil {
		return nil, err
	}

	// Enrich with user info
	result := make([]*GroupBanWithUser, 0, len(bans))
	for _, ban := range bans {
		banWithUser := &GroupBanWithUser{
			ID:       ban.ID,
			ChatID:   ban.ChatID,
			UserID:   ban.UserID,
			BannedBy: ban.BannedBy,
			Reason:   ban.Reason,
			BannedAt: ban.BannedAt.Format("2006-01-02T15:04:05Z"),
		}

		// Get banned user info
		if user, err := s.userRepo.GetByID(ctx, ban.UserID); err == nil && user != nil {
			banWithUser.User = user
		}

		// Get banner user info
		if user, err := s.userRepo.GetByID(ctx, ban.BannedBy); err == nil && user != nil {
			banWithUser.BannedByUser = user
		}

		result = append(result, banWithUser)
	}

	return result, nil
}

// IsBanned checks if user is banned from a group
func (s *GroupService) IsBanned(ctx context.Context, groupID, userID int) (bool, error) {
	return s.chatRepo.IsBanned(ctx, groupID, userID)
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
