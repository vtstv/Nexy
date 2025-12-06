package services

import (
	"context"

	"github.com/redis/go-redis/v9"
	"github.com/vtstv/nexy-admin/internal/models"
	"github.com/vtstv/nexy-admin/internal/repositories"
)

type StatsService struct {
	repo        *repositories.StatsRepository
	redisClient *redis.Client
}

func NewStatsService(repo *repositories.StatsRepository, redisClient *redis.Client) *StatsService {
	return &StatsService{
		repo:        repo,
		redisClient: redisClient,
	}
}

func (s *StatsService) GetOverview(ctx context.Context) (*models.Stats, error) {
	stats, err := s.repo.GetOverview(ctx)
	if err != nil {
		return nil, err
	}

	userGrowth, err := s.repo.GetUserGrowth(ctx, 30)
	if err == nil {
		stats.UserGrowth = userGrowth
	}

	messageActivity, err := s.repo.GetMessageActivity(ctx, 30)
	if err == nil {
		stats.MessageActivity = messageActivity
	}

	return stats, nil
}

func (s *StatsService) GetUserStats(ctx context.Context, days int) ([]models.TimeSeriesData, error) {
	return s.repo.GetUserGrowth(ctx, days)
}

func (s *StatsService) GetChatStats(ctx context.Context) (map[string]int, error) {
	return s.repo.GetChatStats(ctx)
}

func (s *StatsService) GetMessageStats(ctx context.Context, days int) ([]models.TimeSeriesData, error) {
	return s.repo.GetMessageActivity(ctx, days)
}
