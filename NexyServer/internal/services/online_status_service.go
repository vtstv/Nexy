/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"time"

	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

const (
	OnlineThreshold   = 30 * time.Second
	RecentlyThreshold = 3 * 24 * time.Hour  // 3 days
	WeekThreshold     = 7 * 24 * time.Hour  // 7 days
	MonthThreshold    = 30 * 24 * time.Hour // 30 days
)

const (
	StatusOnline      = "online"
	StatusRecently    = "last seen recently"
	StatusWithinWeek  = "last seen within a week"
	StatusWithinMonth = "last seen within a month"
	StatusLongTimeAgo = "last seen a long time ago"
	StatusHidden      = ""
)

type OnlineStatusService struct {
	userRepo *repositories.UserRepository
}

func NewOnlineStatusService(userRepo *repositories.UserRepository) *OnlineStatusService {
	return &OnlineStatusService{userRepo: userRepo}
}

func (s *OnlineStatusService) UpdateLastSeen(ctx context.Context, userID int) error {
	return s.userRepo.UpdateLastSeen(ctx, userID)
}

func (s *OnlineStatusService) GetOnlineStatus(lastSeen *time.Time, isOnline bool) string {
	if isOnline {
		return StatusOnline
	}

	if lastSeen == nil {
		return StatusLongTimeAgo
	}

	elapsed := time.Since(*lastSeen)

	if elapsed < OnlineThreshold {
		return StatusOnline
	} else if elapsed < RecentlyThreshold {
		return StatusRecently
	} else if elapsed < WeekThreshold {
		return StatusWithinWeek
	} else if elapsed < MonthThreshold {
		return StatusWithinMonth
	}

	return StatusLongTimeAgo
}

func (s *OnlineStatusService) ApplyPrivacyFilter(requestingUser, targetUser *models.User, isOnline bool) string {
	if !requestingUser.ShowOnlineStatus {
		return StatusHidden
	}

	if !targetUser.ShowOnlineStatus {
		return StatusHidden
	}

	return s.GetOnlineStatus(targetUser.LastSeen, isOnline)
}

func (s *OnlineStatusService) EnrichUserWithStatus(ctx context.Context, user *models.User, requestingUserID int, isOnline bool) error {
	if user == nil {
		return nil
	}

	requestingUser, err := s.userRepo.GetByID(ctx, requestingUserID)
	if err != nil {
		return err
	}

	user.OnlineStatus = s.ApplyPrivacyFilter(requestingUser, user, isOnline)
	return nil
}

func (s *OnlineStatusService) EnrichUsersWithStatus(ctx context.Context, users []*models.User, requestingUserID int, onlineUserIDs map[int]bool) error {
	if len(users) == 0 {
		return nil
	}

	requestingUser, err := s.userRepo.GetByID(ctx, requestingUserID)
	if err != nil {
		return err
	}

	for _, user := range users {
		isOnline := onlineUserIDs[user.ID]
		user.OnlineStatus = s.ApplyPrivacyFilter(requestingUser, user, isOnline)
	}

	return nil
}
