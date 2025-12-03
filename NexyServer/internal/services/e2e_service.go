/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"fmt"

	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

type E2EService struct {
	e2eRepo *repositories.E2ERepository
}

func NewE2EService(e2eRepo *repositories.E2ERepository) *E2EService {
	return &E2EService{e2eRepo: e2eRepo}
}

// Upload identity key
func (s *E2EService) UploadIdentityKey(ctx context.Context, userID int, publicKey string) error {
	if publicKey == "" {
		return fmt.Errorf("public key cannot be empty")
	}
	return s.e2eRepo.SaveIdentityKey(ctx, userID, publicKey)
}

// Upload signed prekey
func (s *E2EService) UploadSignedPreKey(ctx context.Context, userID, keyID int, publicKey, signature string) error {
	if publicKey == "" || signature == "" {
		return fmt.Errorf("public key and signature cannot be empty")
	}
	return s.e2eRepo.SaveSignedPreKey(ctx, userID, keyID, publicKey, signature)
}

// Upload batch of one-time prekeys
func (s *E2EService) UploadPreKeys(ctx context.Context, userID int, keys []models.PreKey) error {
	if len(keys) == 0 {
		return fmt.Errorf("no prekeys provided")
	}
	return s.e2eRepo.SavePreKeys(ctx, userID, keys)
}

// Get key bundle for establishing session with a user
func (s *E2EService) GetKeyBundle(ctx context.Context, userID, deviceID int) (*models.KeyBundle, error) {
	bundle, err := s.e2eRepo.GetKeyBundle(ctx, userID, deviceID)
	if err != nil {
		return nil, fmt.Errorf("failed to get key bundle: %w", err)
	}
	return bundle, nil
}

// Check if user needs to upload more prekeys
func (s *E2EService) CheckPreKeyCount(ctx context.Context, userID int) (int, bool, error) {
	count, err := s.e2eRepo.CountUnusedPreKeys(ctx, userID)
	if err != nil {
		return 0, false, err
	}
	needsMore := count < 10 // Threshold: replenish when below 10
	return count, needsMore, nil
}
