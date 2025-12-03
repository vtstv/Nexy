/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"errors"
	"time"

	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

type GroupService struct {
	chatRepo *repositories.ChatRepository
	userRepo *repositories.UserRepository
}

func NewGroupService(chatRepo *repositories.ChatRepository, userRepo *repositories.UserRepository) *GroupService {
	return &GroupService{
		chatRepo: chatRepo,
		userRepo: userRepo,
	}
}

func (s *GroupService) CreateGroup(ctx context.Context, name, description, groupType, username string, creatorID int, initialMembers []int) (*models.Chat, error) {
	if groupType != "private_group" && groupType != "public_group" {
		return nil, errors.New("invalid group type: must be 'private_group' or 'public_group'")
	}

	chat := &models.Chat{
		Type:        "group",
		GroupType:   groupType,
		Name:        name,
		Description: description,
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

func (s *GroupService) GetGroup(ctx context.Context, groupID, userID int) (*models.Chat, error) {
	chat, err := s.chatRepo.GetByID(ctx, groupID)
	if err != nil {
		return nil, err
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

func (s *GroupService) CreateInviteLink(ctx context.Context, groupID, userID int, usageLimit *int, expiresAt *time.Time) (*models.ChatInviteLink, error) {
	// Check permissions
	member, err := s.chatRepo.GetChatMember(ctx, groupID, userID)
	if err != nil {
		return nil, err
	}
	if member.Role != "owner" && member.Role != "admin" {
		// Check if member has add_users permission? Usually invite links are admin only or add_users
		if member.Permissions == nil || !member.Permissions.AddUsers {
			return nil, errors.New("permission denied")
		}
	}

	// Generate code
	code, err := generateInviteCode()
	if err != nil {
		return nil, err
	}

	link := &models.ChatInviteLink{
		ChatID:     groupID,
		CreatorID:  userID,
		Code:       code,
		UsageLimit: usageLimit,
		ExpiresAt:  expiresAt,
	}

	err = s.chatRepo.CreateInviteLink(ctx, link)
	if err != nil {
		return nil, err
	}

	return link, nil
}

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

func (s *GroupService) GetGroupMembers(ctx context.Context, groupID, userID int) ([]*models.ChatMember, error) {
	chat, err := s.chatRepo.GetByID(ctx, groupID)
	if err != nil {
		return nil, err
	}

	isMember, err := s.chatRepo.IsMember(ctx, groupID, userID)
	if err != nil {
		return nil, err
	}

	if !isMember && chat.GroupType != "public_group" {
		return nil, errors.New("access denied")
	}

	members, err := s.chatRepo.GetChatMembersFull(ctx, groupID)
	if err != nil {
		return nil, err
	}

	// Load user information for each member
	for _, member := range members {
		user, err := s.userRepo.GetByID(ctx, member.UserID)
		if err == nil {
			member.User = user
		}
	}

	return members, nil
}

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

	return s.chatRepo.RemoveMember(ctx, groupID, targetUserID)
}

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

	member := &models.ChatMember{
		ChatID:      groupID,
		UserID:      targetUserID,
		Role:        "member",
		Permissions: chat.DefaultPermissions,
	}

	return s.chatRepo.AddMember(ctx, member)
}

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

func (s *GroupService) SearchPublicGroups(ctx context.Context, query string, limit int) ([]*models.Chat, error) {
	return s.chatRepo.SearchPublicGroups(ctx, query, limit)
}

func (s *GroupService) JoinGroupByInvite(ctx context.Context, inviteCode string, userID int) (*models.Chat, error) {
	invite, err := s.chatRepo.GetInviteLinkByCode(ctx, inviteCode)
	if err != nil {
		return nil, err
	}

	if invite.IsRevoked {
		return nil, errors.New("invite link is revoked")
	}

	if invite.ExpiresAt != nil && invite.ExpiresAt.Before(time.Now()) {
		return nil, errors.New("invite link has expired")
	}

	if invite.UsageLimit != nil && invite.UsageCount >= *invite.UsageLimit {
		return nil, errors.New("invite link usage limit reached")
	}

	chat, err := s.chatRepo.GetByID(ctx, invite.ChatID)
	if err != nil {
		return nil, err
	}

	isMember, err := s.chatRepo.IsMember(ctx, invite.ChatID, userID)
	if err != nil {
		return nil, err
	}

	if isMember {
		return chat, nil
	}

	member := &models.ChatMember{
		ChatID:      invite.ChatID,
		UserID:      userID,
		Role:        "member",
		Permissions: chat.DefaultPermissions,
	}

	err = s.chatRepo.AddMember(ctx, member)
	if err != nil {
		return nil, err
	}

	invite.UsageCount++
	err = s.chatRepo.UpdateInviteLink(ctx, invite)
	if err != nil {
		return nil, err
	}

	return chat, nil
}
