package services

import (
	"context"
	"errors"

	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

type ReactionService struct {
	reactionRepo *repositories.ReactionRepository
	messageRepo  *repositories.MessageRepository
	chatRepo     *repositories.ChatRepository
}

func NewReactionService(reactionRepo *repositories.ReactionRepository, messageRepo *repositories.MessageRepository, chatRepo *repositories.ChatRepository) *ReactionService {
	return &ReactionService{
		reactionRepo: reactionRepo,
		messageRepo:  messageRepo,
		chatRepo:     chatRepo,
	}
}

func (s *ReactionService) AddReaction(ctx context.Context, messageID, userID int, emoji string) (int, error) {
	// Validate emoji (basic check)
	if len(emoji) == 0 || len(emoji) > 10 {
		return 0, errors.New("invalid emoji")
	}

	// Check if message exists
	message, err := s.messageRepo.GetByID(ctx, messageID)
	if err != nil {
		return 0, errors.New("message not found")
	}

	// Check if user is a member of the chat
	isMember, err := s.chatRepo.IsMember(ctx, message.ChatID, userID)
	if err != nil {
		return 0, err
	}
	if !isMember {
		return 0, errors.New("user is not a member of this chat")
	}

	reaction := &models.MessageReaction{
		MessageID: messageID,
		UserID:    userID,
		Emoji:     emoji,
	}

	return message.ChatID, s.reactionRepo.AddReaction(ctx, reaction)
}

func (s *ReactionService) RemoveReaction(ctx context.Context, messageID, userID int, emoji string) (int, error) {
	// Check if message exists
	message, err := s.messageRepo.GetByID(ctx, messageID)
	if err != nil {
		return 0, errors.New("message not found")
	}

	// Check if user is a member of the chat
	isMember, err := s.chatRepo.IsMember(ctx, message.ChatID, userID)
	if err != nil {
		return 0, err
	}
	if !isMember {
		return 0, errors.New("user is not a member of this chat")
	}

	return message.ChatID, s.reactionRepo.RemoveReaction(ctx, messageID, userID, emoji)
}

func (s *ReactionService) GetReactionsByMessageID(ctx context.Context, messageID, userID int) ([]models.ReactionCount, error) {
	return s.reactionRepo.GetReactionsByMessageID(ctx, messageID, userID)
}

func (s *ReactionService) GetReactionsByMessageIDs(ctx context.Context, messageIDs []int, userID int) (map[int][]models.ReactionCount, error) {
	return s.reactionRepo.GetReactionsByMessageIDs(ctx, messageIDs, userID)
}
