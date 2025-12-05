/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"errors"
	"time"

	"github.com/vtstv/nexy/internal/models"
)

// CreateInviteLink creates a new invite link for the group
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

// JoinGroupByInvite allows a user to join a group using an invite link
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
	if chat == nil {
		return nil, errors.New("group not found")
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

// InvitePreviewResponse contains invite validation info with chat preview
type InvitePreviewResponse struct {
	Valid        bool   `json:"valid"`
	ChatID       int    `json:"chat_id,omitempty"`
	ChatName     string `json:"chat_name,omitempty"`
	ChatType     string `json:"chat_type,omitempty"`
	AvatarURL    string `json:"avatar_url,omitempty"`
	MemberCount  int    `json:"member_count,omitempty"`
	ErrorMessage string `json:"error_message,omitempty"`
}

// ValidateGroupInvite validates invite code and returns chat preview
func (s *GroupService) ValidateGroupInvite(ctx context.Context, inviteCode string) (*InvitePreviewResponse, error) {
	invite, err := s.chatRepo.GetInviteLinkByCode(ctx, inviteCode)
	if err != nil {
		return &InvitePreviewResponse{Valid: false, ErrorMessage: "Invite link not found"}, nil
	}

	if invite.IsRevoked {
		return &InvitePreviewResponse{Valid: false, ErrorMessage: "Invite link is revoked"}, nil
	}

	if invite.ExpiresAt != nil && invite.ExpiresAt.Before(time.Now()) {
		return &InvitePreviewResponse{Valid: false, ErrorMessage: "Invite link has expired"}, nil
	}

	if invite.UsageLimit != nil && invite.UsageCount >= *invite.UsageLimit {
		return &InvitePreviewResponse{Valid: false, ErrorMessage: "Invite link usage limit reached"}, nil
	}

	chat, err := s.chatRepo.GetByID(ctx, invite.ChatID)
	if err != nil || chat == nil {
		return &InvitePreviewResponse{Valid: false, ErrorMessage: "Group not found"}, nil
	}

	return &InvitePreviewResponse{
		Valid:       true,
		ChatID:      chat.ID,
		ChatName:    chat.Name,
		ChatType:    chat.Type,
		AvatarURL:   chat.AvatarURL,
		MemberCount: chat.MemberCount,
	}, nil
}
