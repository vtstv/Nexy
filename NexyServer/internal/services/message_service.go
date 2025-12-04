/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"errors"

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

func (s *MessageService) UpdateMessage(ctx context.Context, messageID string, userID int, content string) (*models.Message, error) {
	// Get existing message to verify ownership
	msg, err := s.messageRepo.GetByUUID(ctx, messageID)
	if err != nil {
		return nil, err
	}
	if msg == nil {
		return nil, errors.New("message not found")
	}

	if msg.SenderID != userID {
		return nil, errors.New("unauthorized: can only edit own messages")
	}

	if msg.IsDeleted {
		return nil, errors.New("cannot edit deleted message")
	}

	msg.Content = content
	msg.IsEdited = true

	if err := s.messageRepo.Update(ctx, msg); err != nil {
		return nil, err
	}

	return msg, nil
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

func (s *MessageService) SearchMessages(ctx context.Context, chatID, userID int, query string) ([]*models.Message, error) {
	isMember, err := s.chatRepo.IsMember(ctx, chatID, userID)
	if err != nil || !isMember {
		return nil, errors.New("access denied")
	}

	return s.messageRepo.SearchMessages(ctx, chatID, query)
}
