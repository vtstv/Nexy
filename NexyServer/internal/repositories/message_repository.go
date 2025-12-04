package repositories

import (
	"github.com/vtstv/nexy/internal/database"
)

type MessageRepository struct {
	db *database.DB
}

func NewMessageRepository(db *database.DB) *MessageRepository {
	return &MessageRepository{db: db}
}
