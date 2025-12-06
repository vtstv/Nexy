package nexy

import (
	"context"
	"fmt"
	"log"
	"net/http"

	"github.com/gorilla/websocket"
	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		return true
	},
}

type WSHandler struct {
	hub *Hub
}

func NewWSHandler(hub *Hub) *WSHandler {
	return &WSHandler{hub: hub}
}

func (h *WSHandler) ServeWS(w http.ResponseWriter, r *http.Request, userID int, deviceID string) {
	// Check if user is banned before allowing WebSocket connection
	if h.hub.redis != nil {
		banKey := "banned:user:" + string(rune(userID))
		if userID > 0 {
			ctx := context.Background()
			banKey = "banned:user:" + string(rune(userID+'0'))
			// Fix: use fmt.Sprintf for proper string conversion
			banKey = "banned:user:" + fmt.Sprint(userID)
			result, err := h.hub.redis.Get(ctx, banKey).Result()
			if err == nil && result == "1" {
				log.Printf("Banned user %d attempted WebSocket connection", userID)
				http.Error(w, "Account has been banned", http.StatusForbidden)
				return
			}
		}
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("WebSocket upgrade error: %v", err)
		return
	}

	client := newClient(h.hub, conn, userID, deviceID)

	client.hub.register <- client

	go client.writePump()
	go client.readPump()
}

type NexyChatRepo struct {
	repo *repositories.ChatRepository
}

func NewNexyChatRepo(repo *repositories.ChatRepository) *NexyChatRepo {
	return &NexyChatRepo{repo: repo}
}

func (r *NexyChatRepo) IsMember(ctx context.Context, chatID, userID int) (bool, error) {
	return r.repo.IsMember(ctx, chatID, userID)
}

func (r *NexyChatRepo) GetPrivateChatBetween(ctx context.Context, user1ID, user2ID int) (*Chat, error) {
	chat, err := r.repo.GetPrivateChatBetween(ctx, user1ID, user2ID)
	if err != nil || chat == nil {
		return nil, err
	}

	return &Chat{
		ID:             chat.ID,
		Type:           chat.Type,
		Name:           chat.Name,
		ParticipantIds: chat.ParticipantIds,
	}, nil
}

func (r *NexyChatRepo) CreatePrivateChat(ctx context.Context, user1ID, user2ID int) (*Chat, error) {
	// Create chat using models
	chat := &models.Chat{
		Type:      "private",
		Name:      "",
		CreatedBy: &user1ID,
	}

	if err := r.repo.Create(ctx, chat); err != nil {
		return nil, err
	}

	// Add both users as members
	member1 := &models.ChatMember{
		ChatID: chat.ID,
		UserID: user1ID,
		Role:   "admin",
	}

	member2 := &models.ChatMember{
		ChatID: chat.ID,
		UserID: user2ID,
		Role:   "admin",
	}

	if err := r.repo.AddMember(ctx, member1); err != nil {
		return nil, err
	}

	if err := r.repo.AddMember(ctx, member2); err != nil {
		return nil, err
	}

	return &Chat{
		ID:             chat.ID,
		Type:           chat.Type,
		Name:           chat.Name,
		ParticipantIds: []int{user1ID, user2ID},
	}, nil
}

func (r *NexyChatRepo) GetChatMembers(ctx context.Context, chatID int) ([]int, error) {
	return r.repo.GetChatMembers(ctx, chatID)
}

func (r *NexyChatRepo) GetByID(ctx context.Context, id int) (*models.Chat, error) {
	return r.repo.GetByID(ctx, id)
}

func (r *NexyChatRepo) Create(ctx context.Context, chat *models.Chat) error {
	return r.repo.Create(ctx, chat)
}

func (r *NexyChatRepo) AddMember(ctx context.Context, member *models.ChatMember) error {
	return r.repo.AddMember(ctx, member)
}
