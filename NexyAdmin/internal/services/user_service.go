package services

import (
	"context"
	"fmt"

	"github.com/redis/go-redis/v9"
	"github.com/vtstv/nexy-admin/internal/models"
	"github.com/vtstv/nexy-admin/internal/repositories"
)

type UserService struct {
	repo        *repositories.UserRepository
	redisClient *redis.Client
}

func NewUserService(repo *repositories.UserRepository, redisClient *redis.Client) *UserService {
	return &UserService{
		repo:        repo,
		redisClient: redisClient,
	}
}

func (s *UserService) GetUsers(ctx context.Context, params models.PaginationParams) (*models.PaginatedResponse, error) {
	users, total, err := s.repo.GetAll(ctx, params)
	if err != nil {
		return nil, err
	}

	totalPages := (total + params.PageSize - 1) / params.PageSize

	return &models.PaginatedResponse{
		Data:       users,
		Page:       params.Page,
		PageSize:   params.PageSize,
		TotalCount: total,
		TotalPages: totalPages,
	}, nil
}

func (s *UserService) GetUser(ctx context.Context, id int) (*models.User, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *UserService) UpdateUser(ctx context.Context, user *models.User) error {
	return s.repo.Update(ctx, user)
}

func (s *UserService) DeleteUser(ctx context.Context, id int) error {
	return s.repo.Delete(ctx, id)
}

func (s *UserService) BanUser(ctx context.Context, id int, reason string, bannedBy int) error {
	// Update database
	if err := s.repo.Ban(ctx, id, reason, bannedBy); err != nil {
		return err
	}

	// Update Redis cache
	key := fmt.Sprintf("banned:user:%d", id)
	return s.redisClient.Set(ctx, key, "1", 0).Err()
}

func (s *UserService) UnbanUser(ctx context.Context, id int) error {
	// Update database
	if err := s.repo.Unban(ctx, id); err != nil {
		return err
	}

	// Remove from Redis cache
	key := fmt.Sprintf("banned:user:%d", id)
	return s.redisClient.Del(ctx, key).Err()
}

func (s *UserService) GetUserSessions(ctx context.Context, userID int) ([]models.UserSession, error) {
	return s.repo.GetSessions(ctx, userID)
}
