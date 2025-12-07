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

// AddReactionResult contains the result of adding a reaction
type AddReactionResult struct {
	ChatID        int
	OldEmoji      string // The emoji that was removed (if any)
	IsNewReaction bool   // True if a new reaction was added (not just toggled off)
}

func (s *ReactionService) AddReaction(ctx context.Context, messageID, userID int, emoji string) (*AddReactionResult, error) {
	// Validate emoji (basic check)
	if len(emoji) == 0 || len(emoji) > 10 {
		return nil, errors.New("invalid emoji")
	}

	// Check if message exists
	message, err := s.messageRepo.GetByID(ctx, messageID)
	if err != nil {
		return nil, errors.New("message not found")
	}

	// Check if user is a member of the chat
	isMember, err := s.chatRepo.IsMember(ctx, message.ChatID, userID)
	if err != nil {
		return nil, err
	}
	if !isMember {
		return nil, errors.New("user is not a member of this chat")
	}

	// Remove any existing reaction by this user on this message (single reaction per user)
	oldEmoji, err := s.reactionRepo.RemoveAllUserReactions(ctx, messageID, userID)
	if err != nil {
		return nil, err
	}

	result := &AddReactionResult{
		ChatID:   message.ChatID,
		OldEmoji: oldEmoji,
	}

	// If user clicked the same emoji they already had, just remove it (toggle off)
	if oldEmoji == emoji {
		result.IsNewReaction = false
		return result, nil
	}

	// Add the new reaction
	reaction := &models.MessageReaction{
		MessageID: messageID,
		UserID:    userID,
		Emoji:     emoji,
	}

	err = s.reactionRepo.AddReaction(ctx, reaction)
	if err != nil {
		return nil, err
	}

	result.IsNewReaction = true
	return result, nil
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
