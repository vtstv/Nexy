package services

import (
	"context"

	"github.com/vtstv/nexy-admin/internal/models"
	"github.com/vtstv/nexy-admin/internal/repositories"
)

type MessageService struct {
	repo *repositories.MessageRepository
}

func NewMessageService(repo *repositories.MessageRepository) *MessageService {
	return &MessageService{repo: repo}
}

func (s *MessageService) GetMessages(ctx context.Context, params models.PaginationParams) (*models.PaginatedResponse, error) {
	messages, total, err := s.repo.GetAll(ctx, params)
	if err != nil {
		return nil, err
	}

	totalPages := (total + params.PageSize - 1) / params.PageSize

	return &models.PaginatedResponse{
		Data:       messages,
		Page:       params.Page,
		PageSize:   params.PageSize,
		TotalCount: total,
		TotalPages: totalPages,
	}, nil
}

func (s *MessageService) GetMessage(ctx context.Context, id int) (*models.Message, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *MessageService) DeleteMessage(ctx context.Context, id int) error {
	return s.repo.Delete(ctx, id)
}

func (s *MessageService) SearchMessages(ctx context.Context, query string, limit int) ([]models.Message, error) {
	return s.repo.Search(ctx, query, limit)
}

func (s *MessageService) GetChatMessages(ctx context.Context, chatID int, params models.MessageFilterParams) (*models.PaginatedResponse, error) {
	messages, total, err := s.repo.GetByChatID(ctx, chatID, params)
	if err != nil {
		return nil, err
	}

	totalPages := (total + params.PageSize - 1) / params.PageSize

	return &models.PaginatedResponse{
		Data:       messages,
		Page:       params.Page,
		PageSize:   params.PageSize,
		TotalCount: total,
		TotalPages: totalPages,
	}, nil
}
