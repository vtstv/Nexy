/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"

	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

type SyncService struct {
	syncRepo *repositories.SyncRepository
}

func NewSyncService(syncRepo *repositories.SyncRepository) *SyncService {
	return &SyncService{syncRepo: syncRepo}
}

// GetState returns the current sync state for a user
func (s *SyncService) GetState(ctx context.Context, userID int) (*models.SyncState, error) {
	return s.syncRepo.GetUserSyncState(ctx, userID)
}

// GetDifference returns all updates since the given pts
func (s *SyncService) GetDifference(ctx context.Context, userID int, pts int, limit int) (*models.UpdatesDifference, error) {
	diff, err := s.syncRepo.GetDifference(ctx, userID, pts, limit)
	if err != nil {
		return nil, err
	}

	// Update user's sync state
	if diff.State.Pts > pts {
		s.syncRepo.UpdateUserSyncState(ctx, userID, diff.State.Pts)
	}

	return diff, nil
}

// GetChannelDifference returns updates for a specific channel
func (s *SyncService) GetChannelDifference(ctx context.Context, userID, chatID int, pts int, limit int) (*models.ChannelDifference, error) {
	diff, err := s.syncRepo.GetChannelDifference(ctx, userID, chatID, pts, limit)
	if err != nil {
		return nil, err
	}

	// Update channel sync state
	if diff.Pts > pts {
		s.syncRepo.UpdateChannelSyncState(ctx, userID, chatID, diff.Pts)
	}

	return diff, nil
}

// GetCurrentPts returns the current maximum pts
func (s *SyncService) GetCurrentPts(ctx context.Context) (int, error) {
	return s.syncRepo.GetCurrentPts(ctx)
}

// UpdateUserState updates the user's sync state to the given pts
func (s *SyncService) UpdateUserState(ctx context.Context, userID int, pts int) error {
	return s.syncRepo.UpdateUserSyncState(ctx, userID, pts)
}
