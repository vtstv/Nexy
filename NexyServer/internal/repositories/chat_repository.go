package repositories

import (
	"github.com/vtstv/nexy/internal/database"
)

// ChatRepository handles all chat-related database operations
type ChatRepository struct {
	db *database.DB
}

// NewChatRepository creates a new ChatRepository
func NewChatRepository(db *database.DB) *ChatRepository {
	return &ChatRepository{db: db}
}
