package nexy

import (
	"context"
	"encoding/json"
	"log"
	"sync"
	"time"

	"github.com/gorilla/websocket"
	"github.com/redis/go-redis/v9"
	"github.com/vtstv/nexy/internal/models"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = (pongWait * 9) / 10
	maxMessageSize = 512 * 1024
)

type Client struct {
	hub      *Hub
	conn     *websocket.Conn
	send     chan []byte
	userID   int
	mu       sync.Mutex
	isClosed bool
}

type Hub struct {
	clients     map[int]*Client
	register    chan *Client
	unregister  chan *Client
	broadcast   chan *NexyMessage
	redis       *redis.Client
	mu          sync.RWMutex
	messageRepo MessageRepository
	chatRepo    ChatRepository
	userRepo    UserRepository
}

type MessageRepository interface {
	CreateMessageFromWebSocket(ctx context.Context, messageID string, chatID, senderID int, bodyJSON []byte) error
	GetByUUID(ctx context.Context, uuid string) (*models.Message, error)
	UpdateStatus(ctx context.Context, status *models.MessageStatus) error
}

type ChatRepository interface {
	IsMember(ctx context.Context, chatID, userID int) (bool, error)
	GetPrivateChatBetween(ctx context.Context, user1ID, user2ID int) (*Chat, error)
	CreatePrivateChat(ctx context.Context, user1ID, user2ID int) (*Chat, error)
}

type UserRepository interface {
	GetByID(ctx context.Context, id int) (*models.User, error)
}

type Chat struct {
	ID             int    `json:"id"`
	Type           string `json:"type"`
	Name           string `json:"name"`
	ParticipantIds []int  `json:"participant_ids"`
}

func NewHub(redisClient *redis.Client, messageRepo MessageRepository, chatRepo ChatRepository, userRepo UserRepository) *Hub {
	return &Hub{
		clients:     make(map[int]*Client),
		register:    make(chan *Client),
		unregister:  make(chan *Client),
		broadcast:   make(chan *NexyMessage, 256),
		redis:       redisClient,
		messageRepo: messageRepo,
		chatRepo:    chatRepo,
		userRepo:    userRepo,
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
	h.broadcastToAll(onlineMsg)

	log.Printf("Client connected: user_id=%d", client.userID)
}

func (h *Hub) unregisterClient(client *Client) {
	h.mu.Lock()
	if _, ok := h.clients[client.userID]; ok {
		delete(h.clients, client.userID)
		client.closeConnection()
	}
	h.mu.Unlock()

	ctx := context.Background()
	h.redis.Del(ctx, userOnlineKey(client.userID))

	offlineMsg, _ := NewNexyMessage(TypeOffline, client.userID, nil, OnlineBody{UserID: client.userID})
	h.broadcastToAll(offlineMsg)

	log.Printf("Client disconnected: user_id=%d", client.userID)
}

func (h *Hub) handleBroadcast(message *NexyMessage) {
	switch message.Header.Type {
	case TypeChatMessage:
		h.handleChatMessage(message)
	case TypeTyping, TypeDelivered, TypeRead:
		h.handleStatusMessage(message)
	case TypeCallOffer, TypeCallAnswer, TypeICECandidate, TypeCallCancel, TypeCallEnd, TypeCallBusy:
		h.handleSignalingMessage(message)
	}
}

func (h *Hub) handleChatMessage(message *NexyMessage) {
	log.Printf("handleChatMessage called: messageID=%s, senderID=%d, chatID=%v, recipientID=%v",
		message.Header.MessageID, message.Header.SenderID, message.Header.ChatID, message.Header.RecipientID)

	ctx := context.Background()

	// If chatID is nil but recipientID is provided, find or create private chat
	if message.Header.ChatID == nil && message.Header.RecipientID != nil {
		log.Printf("No chatID provided, checking for private chat between %d and %d",
			message.Header.SenderID, *message.Header.RecipientID)

		// Try to find existing private chat
		existingChat, err := h.chatRepo.GetPrivateChatBetween(ctx, message.Header.SenderID, *message.Header.RecipientID)
		if err != nil {
			log.Printf("Error checking for existing chat: %v", err)
		}

		if existingChat != nil {
			// Use existing chat
			log.Printf("Found existing private chat: chatID=%d", existingChat.ID)
			message.Header.ChatID = &existingChat.ID
		} else {
			// Create new private chat
			log.Printf("Creating new private chat between users %d and %d",
				message.Header.SenderID, *message.Header.RecipientID)

			newChat, err := h.chatRepo.CreatePrivateChat(ctx, message.Header.SenderID, *message.Header.RecipientID)
			if err != nil {
				log.Printf("Failed to create private chat: %v", err)
				return
			}

			log.Printf("Created new private chat: chatID=%d", newChat.ID)
			message.Header.ChatID = &newChat.ID

			// Notify both participants about the new chat
			chatCreatedBody := ChatCreatedBody{
				ChatID:         newChat.ID,
				ChatType:       newChat.Type,
				ParticipantIDs: newChat.ParticipantIds,
				CreatedBy:      message.Header.SenderID,
			}

			chatCreatedMsg, _ := NewNexyMessage(TypeChatCreated, message.Header.SenderID, nil, chatCreatedBody)
			log.Printf("Notifying users about new chat: %d and %d", message.Header.SenderID, *message.Header.RecipientID)
			h.sendToUser(message.Header.SenderID, chatCreatedMsg)
			h.sendToUser(*message.Header.RecipientID, chatCreatedMsg)
		}
	}

	if message.Header.ChatID == nil {
		log.Printf("handleChatMessage: chatID is nil after resolution, skipping")
		return
	}

	isMember, err := h.chatRepo.IsMember(ctx, *message.Header.ChatID, message.Header.SenderID)
	log.Printf("IsMember check: chatID=%d, userID=%d, result=%v, err=%v",
		*message.Header.ChatID, message.Header.SenderID, isMember, err)
	if !isMember {
		log.Printf("handleChatMessage: user %d is not a member of chat %d",
			message.Header.SenderID, *message.Header.ChatID)
		return
	}

	// Save message to database
	log.Printf("Creating message in DB: messageID=%s, chatID=%d, senderID=%d, body=%s",
		message.Header.MessageID, *message.Header.ChatID, message.Header.SenderID, string(message.Body))
	err = h.messageRepo.CreateMessageFromWebSocket(ctx, message.Header.MessageID, *message.Header.ChatID, message.Header.SenderID, message.Body)
	if err != nil {
		log.Printf("Failed to save message to DB: %v", err)
	} else {
		log.Printf("Message saved successfully to DB: messageID=%s", message.Header.MessageID)
	}

	h.broadcastToChatMembers(*message.Header.ChatID, message)
}

func (h *Hub) handleStatusMessage(message *NexyMessage) {
	if message.Header.Type == TypeRead {
		ctx := context.Background()

		var readBody ReadBody
		if err := json.Unmarshal(message.Body, &readBody); err != nil {
			log.Printf("Error unmarshalling read body: %v", err)
		} else {
			targetMessageID := readBody.MessageID

			// Update status in DB
			msg, err := h.messageRepo.GetByUUID(ctx, targetMessageID)
			if err != nil {
				log.Printf("Error getting message for read receipt: %v", err)
			} else if msg != nil {
				status := &models.MessageStatus{
					MessageID: msg.ID,
					UserID:    message.Header.SenderID,
					Status:    "read",
				}
				if err := h.messageRepo.UpdateStatus(ctx, status); err != nil {
					log.Printf("Failed to update read status in DB: %v", err)
				} else {
					log.Printf("Updated read status for message %s by user %d", targetMessageID, message.Header.SenderID)
				}
			}
		}

		if message.Header.RecipientID != nil {
			// Check Sender (Reader)
			sender, err := h.userRepo.GetByID(ctx, message.Header.SenderID)
			if err != nil {
				log.Printf("Error getting sender for read receipt check: %v", err)
				return
			}

			// Check Recipient (Original Sender)
			recipient, err := h.userRepo.GetByID(ctx, *message.Header.RecipientID)
			if err != nil {
				log.Printf("Error getting recipient for read receipt check: %v", err)
				return
			}

			// If either has disabled read receipts, do not send
			if !sender.ReadReceiptsEnabled || !recipient.ReadReceiptsEnabled {
				log.Printf("Read receipt suppressed: Sender(%v)=%v, Recipient(%v)=%v",
					sender.ID, sender.ReadReceiptsEnabled, recipient.ID, recipient.ReadReceiptsEnabled)
				return
			}
		}
	}

	if message.Header.RecipientID != nil {
		h.sendToUser(*message.Header.RecipientID, message)
	}
}

func (h *Hub) handleSignalingMessage(message *NexyMessage) {
	if message.Header.RecipientID != nil {
		h.sendToUser(*message.Header.RecipientID, message)
	}
}

func (h *Hub) sendToUser(userID int, message *NexyMessage) {
	h.mu.RLock()
	client, ok := h.clients[userID]
	h.mu.RUnlock()

	if ok {
		data, _ := json.Marshal(message)
		select {
		case client.send <- data:
		default:
			h.unregisterClient(client)
		}
	}
}

func (h *Hub) broadcastToAll(message *NexyMessage) {
	data, _ := json.Marshal(message)
	h.mu.RLock()
	defer h.mu.RUnlock()

	for _, client := range h.clients {
		select {
		case client.send <- data:
		default:
			go h.unregisterClient(client)
		}
	}
}

func (h *Hub) broadcastToChatMembers(chatID int, message *NexyMessage) {
	data, _ := json.Marshal(message)
	h.mu.RLock()
	defer h.mu.RUnlock()

	log.Printf("Broadcasting message to chat %d members. Total connected clients: %d", chatID, len(h.clients))

	for _, client := range h.clients {
		ctx := context.Background()
		isMember, err := h.chatRepo.IsMember(ctx, chatID, client.userID)
		if err != nil {
			log.Printf("Error checking membership for user %d in chat %d: %v", client.userID, chatID, err)
			continue
		}
		if isMember {
			log.Printf("Sending message to member user %d", client.userID)
			select {
			case client.send <- data:
			default:
				log.Printf("Client %d send channel full, unregistering", client.userID)
				go h.unregisterClient(client)
			}
		} else {
			// log.Printf("User %d is NOT a member of chat %d", client.userID, chatID)
		}
	}
}

func (c *Client) readPump() {
	defer func() {
		c.hub.unregister <- c
	}()

	c.conn.SetReadDeadline(time.Now().Add(pongWait))
	c.conn.SetPongHandler(func(string) error {
		c.conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	c.conn.SetReadLimit(maxMessageSize)

	for {
		_, data, err := c.conn.ReadMessage()
		if err != nil {
			break
		}

		log.Printf("Received raw WebSocket data from user %d: %s", c.userID, string(data))

		var msg NexyMessage
		if err := json.Unmarshal(data, &msg); err != nil {
			log.Printf("Error unmarshaling message: %v", err)
			continue
		}

		log.Printf("Parsed message type: %s, messageID: %s", msg.Header.Type, msg.Header.MessageID)

		if msg.Header.Type == TypeHeartbeat {
			ack, _ := NewNexyMessage(TypeAck, 0, nil, AckBody{MessageID: msg.Header.MessageID, Status: "ok"})
			ackData, _ := json.Marshal(ack)
			c.send <- ackData
			continue
		}

		msg.Header.SenderID = c.userID
		log.Printf("Broadcasting message to hub: type=%s, senderID=%d", msg.Header.Type, c.userID)
		c.hub.broadcast <- &msg
	}
}

func (c *Client) writePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		c.closeConnection()
	}()

	for {
		select {
		case message, ok := <-c.send:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			if err := c.conn.WriteMessage(websocket.TextMessage, message); err != nil {
				return
			}

		case <-ticker.C:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

func (c *Client) closeConnection() {
	c.mu.Lock()
	defer c.mu.Unlock()

	if !c.isClosed {
		close(c.send)
		c.conn.Close()
		c.isClosed = true
	}
}

func userOnlineKey(userID int) string {
	return "user:online:" + string(rune(userID))
}
