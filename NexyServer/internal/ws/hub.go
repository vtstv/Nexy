package nexy

import (
	"context"
	"encoding/json"
	"log"
	"sync"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/vtstv/nexy/internal/models"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = (pongWait * 9) / 10
	maxMessageSize = 512 * 1024
)

type Hub struct {
	clients      map[int]*Client
	register     chan *Client
	unregister   chan *Client
	broadcast    chan *NexyMessage
	redis        *redis.Client
	mu           sync.RWMutex
	typingStatus map[int]map[int]bool // UserID -> ChatID -> isTyping
	typingMu     sync.Mutex
	messageRepo  MessageRepository
	chatRepo     ChatRepository
	userRepo     UserRepository
}

type MessageRepository interface {
	CreateMessageFromWebSocket(ctx context.Context, messageID string, chatID, senderID int, bodyJSON []byte) error
	GetByUUID(ctx context.Context, uuid string) (*models.Message, error)
	UpdateStatus(ctx context.Context, status *models.MessageStatus) error
	Update(ctx context.Context, msg *models.Message) error
	MarkMessagesAsRead(ctx context.Context, chatID, userID, lastMessageID int) error
}

type ChatRepository interface {
	IsMember(ctx context.Context, chatID, userID int) (bool, error)
	GetPrivateChatBetween(ctx context.Context, user1ID, user2ID int) (*Chat, error)
	Create(ctx context.Context, chat *models.Chat) error
	AddMember(ctx context.Context, member *models.ChatMember) error
	GetChatMembers(ctx context.Context, chatID int) ([]int, error)
}

type UserRepository interface {
	GetByID(ctx context.Context, id int) (*models.User, error)
	UpdateLastSeen(ctx context.Context, userID int) error
}

type Chat struct {
	ID             int    `json:"id"`
	Type           string `json:"type"`
	Name           string `json:"name"`
	ParticipantIds []int  `json:"participant_ids"`
}

func NewHub(redisClient *redis.Client, messageRepo MessageRepository, chatRepo ChatRepository, userRepo UserRepository) *Hub {
	return &Hub{
		clients:      make(map[int]*Client),
		register:     make(chan *Client),
		unregister:   make(chan *Client),
		broadcast:    make(chan *NexyMessage, 256),
		redis:        redisClient,
		typingStatus: make(map[int]map[int]bool),
		messageRepo:  messageRepo,
		chatRepo:     chatRepo,
		userRepo:     userRepo,
	}
}

func (h *Hub) Run() {
	for {
		select {
		case client := <-h.register:
			h.registerClient(client)
		case client := <-h.unregister:
			h.unregisterClient(client)
		case message := <-h.broadcast:
			h.handleBroadcast(message)
		}
	}
}

func (h *Hub) registerClient(client *Client) {
	h.mu.Lock()
	h.clients[client.userID] = client
	h.mu.Unlock()

	ctx := context.Background()
	h.redis.Set(ctx, userOnlineKey(client.userID), "1", 0)

	onlineMsg, _ := NewNexyMessage(TypeOnline, client.userID, nil, OnlineBody{UserID: client.userID})
	h.broadcastToAll(onlineMsg, h.unregisterClientFunc)

	log.Printf("Client connected: user_id=%d", client.userID)
}

func (h *Hub) unregisterClient(client *Client) {
	h.mu.Lock()
	// Only remove if this is the same client (prevents race condition with reconnects)
	if existingClient, ok := h.clients[client.userID]; ok && existingClient == client {
		delete(h.clients, client.userID)
		client.closeConnection()
	}
	h.mu.Unlock()

	ctx := context.Background()
	h.redis.Del(ctx, userOnlineKey(client.userID))

	// Update last seen when user disconnects
	if err := h.userRepo.UpdateLastSeen(ctx, client.userID); err != nil {
		log.Printf("Error updating last seen for user %d: %v", client.userID, err)
	}

	offlineMsg, _ := NewNexyMessage(TypeOffline, client.userID, nil, OnlineBody{UserID: client.userID})
	h.broadcastToAll(offlineMsg, h.unregisterClientFunc)

	log.Printf("Client disconnected: user_id=%d", client.userID)

	// Check if user was typing in any chats and broadcast stop typing
	chatIDs := h.clearTypingStatusForUser(client.userID)
	for _, chatID := range chatIDs {
		typingBody := TypingBody{
			ChatID:   chatID,
			IsTyping: false,
		}
		bodyBytes, _ := json.Marshal(typingBody)

		stopTypingMsg := &NexyMessage{
			Header: NexyHeader{
				Type:      TypeTyping,
				Timestamp: time.Now().Unix(),
				SenderID:  client.userID,
				ChatID:    &chatID,
			},
			Body: bodyBytes,
		}

		go h.handleTypingMessage(stopTypingMsg, h.unregisterClientFunc)
	}
}

func (h *Hub) unregisterClientFunc(client *Client) {
	h.unregister <- client
}

func (h *Hub) handleBroadcast(message *NexyMessage) {
	switch message.Header.Type {
	case TypeChatMessage:
		h.handleChatMessage(message, h.unregisterClientFunc)
	case TypeEdit:
		h.handleEditMessage(message)
	case TypeTyping:
		h.handleTypingMessage(message, h.unregisterClientFunc)
	case TypeDelivered, TypeRead:
		h.handleStatusMessage(message, h.unregisterClientFunc)
	case TypeCallOffer, TypeCallAnswer, TypeICECandidate, TypeCallCancel, TypeCallEnd, TypeCallBusy:
		h.handleSignalingMessage(message, h.unregisterClientFunc)
	}
}

func (h *Hub) BroadcastEdit(msg *models.Message) {
	editBody := EditMessageBody{
		MessageID: msg.MessageID,
		Content:   msg.Content,
	}
	bodyBytes, _ := json.Marshal(editBody)

	nexyMsg := &NexyMessage{
		Header: NexyHeader{
			Type:      "edit",
			MessageID: msg.MessageID,
			Timestamp: time.Now().Unix(),
			SenderID:  msg.SenderID,
			ChatID:    &msg.ChatID,
		},
		Body: bodyBytes,
	}

	h.broadcastToChatMembers(msg.ChatID, nexyMsg)
}

func (h *Hub) BroadcastDelete(msg *models.Message) {
	deleteBody := map[string]string{
		"message_id": msg.MessageID,
	}
	bodyBytes, _ := json.Marshal(deleteBody)

	nexyMsg := &NexyMessage{
		Header: NexyHeader{
			Type:      TypeDelete,
			MessageID: msg.MessageID,
			Timestamp: time.Now().Unix(),
			SenderID:  msg.SenderID,
			ChatID:    &msg.ChatID,
		},
		Body: bodyBytes,
	}

	h.broadcastToChatMembers(msg.ChatID, nexyMsg)
}

func userOnlineKey(userID int) string {
	return "user:online:" + string(rune(userID))
}

func (h *Hub) IsUserOnline(userID int) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	_, online := h.clients[userID]
	return online
}

func (h *Hub) GetOnlineUserIDs() map[int]bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	result := make(map[int]bool)
	for userID := range h.clients {
		result[userID] = true
	}
	return result
}

func (h *Hub) GetOnlineUserIDsForUsers(userIDs []int) map[int]bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	result := make(map[int]bool)
	for _, userID := range userIDs {
		if _, online := h.clients[userID]; online {
			result[userID] = true
		}
	}
	return result
}
