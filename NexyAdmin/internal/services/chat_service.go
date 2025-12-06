package services

import (
	"context"

	"github.com/vtstv/nexy-admin/internal/models"
	"github.com/vtstv/nexy-admin/internal/repositories"
)

type ChatService struct {
	repo     *repositories.ChatRepository
	userRepo *repositories.UserRepository
}

func NewChatService(repo *repositories.ChatRepository, userRepo *repositories.UserRepository) *ChatService {
	return &ChatService{
		repo:     repo,
		userRepo: userRepo,
	}
}

func (s *ChatService) GetChats(ctx context.Context, params models.PaginationParams) (*models.PaginatedResponse, error) {
	chats, total, err := s.repo.GetAll(ctx, params)
	if err != nil {
		return nil, err
	}

	totalPages := (total + params.PageSize - 1) / params.PageSize

	return &models.PaginatedResponse{
		Data:       chats,
		Page:       params.Page,
		PageSize:   params.PageSize,
		TotalCount: total,
		TotalPages: totalPages,
	}, nil
}

func (s *ChatService) GetChat(ctx context.Context, id int) (*models.Chat, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *ChatService) UpdateChat(ctx context.Context, chat *models.Chat) error {
	return s.repo.Update(ctx, chat)
}

func (s *ChatService) DeleteChat(ctx context.Context, id int) error {
	return s.repo.Delete(ctx, id)
}

func (s *ChatService) GetChatMembers(ctx context.Context, chatID int) ([]models.ChatMember, error) {
	return s.repo.GetMembers(ctx, chatID)
}

func (s *ChatService) RemoveChatMember(ctx context.Context, chatID, userID int) error {
	return s.repo.RemoveMember(ctx, chatID, userID)
}
