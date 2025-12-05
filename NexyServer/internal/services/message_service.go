/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"errors"
	"strings"

	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

type MessageService struct {
	messageRepo *repositories.MessageRepository
	chatRepo    *repositories.ChatRepository
	userRepo    *repositories.UserRepository
	fileService *FileService
}

func NewMessageService(messageRepo *repositories.MessageRepository, chatRepo *repositories.ChatRepository, userRepo *repositories.UserRepository, fileService *FileService) *MessageService {
	return &MessageService{
		messageRepo: messageRepo,
		chatRepo:    chatRepo,
		userRepo:    userRepo,
		fileService: fileService,
	}
}

func (s *MessageService) GetChatHistory(ctx context.Context, chatID, userID, limit, offset int) ([]*models.Message, error) {
	chat, err := s.chatRepo.GetByID(ctx, chatID)
	if err != nil {
		return nil, err
	}

	isMember, err := s.chatRepo.IsMember(ctx, chatID, userID)
	if err != nil {
		return nil, err
	}

	// Allow access if user is a member OR if it's a public group
	if !isMember && chat.GroupType != "public_group" {
		return nil, errors.New("not a member of this chat")
	}

	if limit <= 0 || limit > 100 {
		limit = 50
	}

	messages, err := s.messageRepo.GetByChatID(ctx, chatID, limit, offset)
	if err != nil {
		return nil, err
	}

	// Enrich messages with sender info
	for _, msg := range messages {
		if msg.SenderID > 0 {
			sender, err := s.userRepo.GetByID(ctx, msg.SenderID)
			if err == nil && sender != nil {
				msg.Sender = sender
			}
		}
	}

	return messages, nil
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

func (s *MessageService) DeleteMessage(ctx context.Context, messageID string, userID int) (*models.Message, error) {
	// Get message to check for attachments
	msg, err := s.messageRepo.GetByUUID(ctx, messageID)
	if err != nil {
		return nil, err
	}
	if msg == nil {
		return nil, errors.New("message not found")
	}

	// Verify ownership
	if msg.SenderID != userID {
		return nil, errors.New("unauthorized")
	}

	// Delete attachment if exists
	if msg.MediaURL != "" {
		// Extract file ID from URL (e.g. /files/uuid -> uuid)
		parts := strings.Split(msg.MediaURL, "/")
		if len(parts) > 0 {
			fileID := parts[len(parts)-1]
			// Ignore error if file deletion fails, proceed to delete message
			_ = s.fileService.DeleteFile(ctx, fileID)
		}
	}

	// Verify user owns the message before deleting
	if err := s.messageRepo.DeleteMessage(ctx, messageID, userID); err != nil {
		return nil, err
	}

	return msg, nil
}

func (s *MessageService) SearchMessages(ctx context.Context, chatID, userID int, query string) ([]*models.Message, error) {
	isMember, err := s.chatRepo.IsMember(ctx, chatID, userID)
	if err != nil || !isMember {
		return nil, errors.New("access denied")
	}

	return s.messageRepo.SearchMessages(ctx, chatID, query)
}
