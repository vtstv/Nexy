/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"

	"github.com/vtstv/nexy/internal/repositories"
)

type FcmService struct {
	userRepo *repositories.UserRepository
}

func NewFcmService(userRepo *repositories.UserRepository) *FcmService {
	return &FcmService{
		userRepo: userRepo,
	}
}

func (s *FcmService) UpdateUserFcmToken(ctx context.Context, userID int, fcmToken string) error {
	return s.userRepo.UpdateFcmToken(ctx, userID, fcmToken)
}

func (s *FcmService) DeleteUserFcmToken(ctx context.Context, userID int) error {
	return s.userRepo.UpdateFcmToken(ctx, userID, "")
}

func (s *FcmService) GetUserFcmToken(ctx context.Context, userID int) (string, error) {
	return s.userRepo.GetFcmToken(ctx, userID)
}
