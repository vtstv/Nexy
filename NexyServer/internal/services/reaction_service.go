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
	ChatID          int
	IsNewReaction   bool   // True if a new reaction was added, false if toggled off (removed)
	RemovedOldEmoji string // If user's own reaction was replaced, this contains the old emoji
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

	result := &AddReactionResult{
		ChatID: message.ChatID,
	}

	// Check if user already has this exact reaction
	hasReaction, err := s.reactionRepo.HasUserReaction(ctx, messageID, userID, emoji)
	if err != nil {
		return nil, err
	}

	if hasReaction {
		// Toggle off - remove the reaction
		err = s.reactionRepo.RemoveReaction(ctx, messageID, userID, emoji)
		if err != nil {
			return nil, err
		}
		result.IsNewReaction = false
		return result, nil
	}

	// Check if this emoji already exists on the message (from other users)
	emojiExists, err := s.reactionRepo.EmojiExistsOnMessage(ctx, messageID, emoji)
	if err != nil {
		return nil, err
	}

	// If emoji doesn't exist yet (user is creating a NEW reaction type),
	// we need to remove their old "own" reaction first (one own reaction per user)
	if !emojiExists {
		oldEmoji, err := s.reactionRepo.GetUserOwnReactionEmoji(ctx, messageID, userID)
		if err != nil {
			return nil, err
		}
		if oldEmoji != "" {
			// Remove user's old own reaction
			err = s.reactionRepo.RemoveUserOwnReaction(ctx, messageID, userID, oldEmoji)
			if err != nil {
				return nil, err
			}
			result.RemovedOldEmoji = oldEmoji
		}
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
