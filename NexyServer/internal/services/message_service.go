/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"

	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

type MessageService struct {
	messageRepo *repositories.MessageRepository
	chatRepo    *repositories.ChatRepository
}

func NewMessageService(messageRepo *repositories.MessageRepository, chatRepo *repositories.ChatRepository) *MessageService {
	return &MessageService{
		messageRepo: messageRepo,
		chatRepo:    chatRepo,
	}
}

func (s *MessageService) GetChatHistory(ctx context.Context, chatID, userID, limit, offset int) ([]*models.Message, error) {
	isMember, err := s.chatRepo.IsMember(ctx, chatID, userID)
	if err != nil || !isMember {
		return nil, err
	}

	if limit <= 0 || limit > 100 {
		limit = 50
	}

	return s.messageRepo.GetByChatID(ctx, chatID, limit, offset)
}

func (s *MessageService) UpdateMessageStatus(ctx context.Context, messageID, userID int, status string) error {
	msgStatus := &models.MessageStatus{
		MessageID: messageID,
		UserID:    userID,
		Status:    status,
	}

	return s.messageRepo.UpdateStatus(ctx, msgStatus)
}

func (s *MessageService) DeleteMessage(ctx context.Context, messageID string, userID int) error {
	// Verify user owns the message before deleting
	return s.messageRepo.DeleteMessage(ctx, messageID, userID)
}
