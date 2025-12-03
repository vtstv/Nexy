/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"time"

	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

type InviteService struct {
	inviteRepo *repositories.InviteRepository
}

func NewInviteService(inviteRepo *repositories.InviteRepository) *InviteService {
	return &InviteService{inviteRepo: inviteRepo}
}

func (s *InviteService) CreateInvite(ctx context.Context, creatorID, maxUses int, expiresInHours int) (*models.InviteLink, error) {
	code, err := generateInviteCode()
	if err != nil {
		return nil, fmt.Errorf("failed to generate invite code: %w", err)
	}

	var expiresAt *time.Time
	if expiresInHours > 0 {
		exp := time.Now().Add(time.Duration(expiresInHours) * time.Hour)
		expiresAt = &exp
	}

	invite := &models.InviteLink{
		Code:      code,
		CreatorID: creatorID,
		MaxUses:   maxUses,
		ExpiresAt: expiresAt,
	}

	if err := s.inviteRepo.Create(ctx, invite); err != nil {
		return nil, fmt.Errorf("failed to create invite: %w", err)
	}

	return invite, nil
}

func (s *InviteService) ValidateInvite(ctx context.Context, code string) (*models.InviteLink, error) {
	invite, err := s.inviteRepo.GetByCode(ctx, code)
	if err != nil {
		return nil, fmt.Errorf("invite not found")
	}

	valid, err := s.inviteRepo.IsValid(ctx, code)
	if err != nil {
		return nil, err
	}

	if !valid {
		return nil, fmt.Errorf("invite is no longer valid")
	}

	return invite, nil
}

func (s *InviteService) UseInvite(ctx context.Context, code string) error {
	invite, err := s.ValidateInvite(ctx, code)
	if err != nil {
		return err
	}

	return s.inviteRepo.IncrementUses(ctx, invite.ID)
}

func (s *InviteService) GetUserInvites(ctx context.Context, creatorID int) ([]*models.InviteLink, error) {
	return s.inviteRepo.GetByCreator(ctx, creatorID)
}

func generateInviteCode() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.URLEncoding.EncodeToString(b)[:22], nil
}
