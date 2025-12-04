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
	CreatePrivateChat(ctx context.Context, user1ID, user2ID int) (*Chat, error)
	GetChatMembers(ctx context.Context, chatID int) ([]*models.ChatMember, error)
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
		clients:      make(map[int]*Client),
		register:     make(chan *Client),
		unregister:   make(chan *Client),
		broadcast:    make(chan *NexyMessage, 256),
		redis:        redisClient,
		messageRepo:  messageRepo,
		chatRepo:     chatRepo,
		userRepo:     userRepo,
		typingStatus: make(map[int]map[int]bool),
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

	// Check if user was typing in any chats and broadcast stop typing
	h.typingMu.Lock()
	if chats, ok := h.typingStatus[client.userID]; ok {
		for chatID := range chats {
			// Create stop typing message
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

			// We need to release the lock before calling handleTypingMessage to avoid deadlock
			// But handleTypingMessage will try to acquire the lock again.
			// So we should just broadcast directly or call a helper that doesn't lock.
			// Actually, handleTypingMessage does logic we want (checking permissions).
			// Let's just queue it to be handled.
			go h.handleTypingMessage(stopTypingMsg)
		}
		delete(h.typingStatus, client.userID)
	}
	h.typingMu.Unlock()
}

func (h *Hub) handleBroadcast(message *NexyMessage) {
	switch message.Header.Type {
	case TypeChatMessage:
		h.handleChatMessage(message)
	case TypeEdit:
		h.handleEditMessage(message)
	case TypeTyping:
		h.handleTypingMessage(message)
	case TypeDelivered, TypeRead:
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

func (h *Hub) handleEditMessage(message *NexyMessage) {
	ctx := context.Background()
	var editBody EditMessageBody
	if err := json.Unmarshal(message.Body, &editBody); err != nil {
		log.Printf("Error unmarshalling edit body: %v", err)
		return
	}

	// Get existing message
	msg, err := h.messageRepo.GetByUUID(ctx, editBody.MessageID)
	if err != nil {
		log.Printf("Error getting message for edit: %v", err)
		return
	}
	if msg == nil {
		log.Printf("Message not found for edit: %s", editBody.MessageID)
		return
	}

	// Verify ownership
	if msg.SenderID != message.Header.SenderID {
		log.Printf("Unauthorized edit attempt by user %d on message %s", message.Header.SenderID, editBody.MessageID)
		return
	}

	// Update in DB
	msg.Content = editBody.Content
	msg.IsEdited = true
	if err := h.messageRepo.Update(ctx, msg); err != nil {
		log.Printf("Failed to update message in DB: %v", err)
		return
	}

	// Broadcast to chat members
	message.Header.ChatID = &msg.ChatID
	h.broadcastToChatMembers(msg.ChatID, message)
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
				// Mark this message and all previous unread messages in this chat as read
				if err := h.messageRepo.MarkMessagesAsRead(ctx, msg.ChatID, message.Header.SenderID, msg.ID); err != nil {
					log.Printf("Failed to mark messages as read in DB: %v", err)
				} else {
					log.Printf("Marked messages up to %s as read by user %d", targetMessageID, message.Header.SenderID)
				}

				// Broadcast to chat members (Privacy: Only if sender has read receipts enabled)
				// Check Sender (Reader)
				sender, err := h.userRepo.GetByID(ctx, message.Header.SenderID)
				if err != nil {
					log.Printf("Error getting sender for read receipt check: %v", err)
					return
				}

				if !sender.ReadReceiptsEnabled {
					log.Printf("Read receipt suppressed by sender %d settings", sender.ID)
					return
				}

				// Get chat members to broadcast to
				members, err := h.chatRepo.GetChatMembers(ctx, msg.ChatID)
				if err != nil {
					log.Printf("Error getting chat members for read receipt broadcast: %v", err)
					return
				}

				data, _ := json.Marshal(message)
				h.mu.RLock()
				defer h.mu.RUnlock()

				for _, member := range members {
					// Skip sender (the one who read it)
					if member.UserID == message.Header.SenderID {
						continue
					}

					// Check if recipient is connected
					client, ok := h.clients[member.UserID]
					if !ok {
						continue
					}

					// Check if recipient has read receipts enabled (Reciprocal privacy)
					recipient, err := h.userRepo.GetByID(ctx, member.UserID)
					if err != nil {
						continue
					}

					if !recipient.ReadReceiptsEnabled {
						continue
					}

					select {
					case client.send <- data:
					default:
						go h.unregisterClient(client)
					}
				}
			}
		}
		return
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

func (h *Hub) handleTypingMessage(message *NexyMessage) {
	ctx := context.Background()
	senderID := message.Header.SenderID

	// Check if sender has typing indicators enabled
	sender, err := h.userRepo.GetByID(ctx, senderID)
	if err != nil {
		log.Printf("Error getting sender for typing status: %v", err)
		return
	}

	if !sender.TypingIndicatorsEnabled {
		// If sender disabled typing indicators, do not broadcast
		return
	}

	var typingBody TypingBody
	if err := json.Unmarshal(message.Body, &typingBody); err != nil {
		log.Printf("Error unmarshalling typing body: %v", err)
		return
	}

	log.Printf("Handling typing message from user %d for chat %d. IsTyping: %v", senderID, typingBody.ChatID, typingBody.IsTyping)

	// Update typing status in memory
	h.typingMu.Lock()
	if typingBody.IsTyping {
		if _, ok := h.typingStatus[senderID]; !ok {
			h.typingStatus[senderID] = make(map[int]bool)
		}
		h.typingStatus[senderID][typingBody.ChatID] = true
	} else {
		if chats, ok := h.typingStatus[senderID]; ok {
			delete(chats, typingBody.ChatID)
			if len(chats) == 0 {
				delete(h.typingStatus, senderID)
			}
		}
	}
	h.typingMu.Unlock()

	// Broadcast to chat members who also have typing indicators enabled
	members, err := h.chatRepo.GetChatMembers(ctx, typingBody.ChatID)
	if err != nil {
		log.Printf("Error getting chat members for typing broadcast: %v", err)
		return
	}

	data, _ := json.Marshal(message)
	h.mu.RLock()
	defer h.mu.RUnlock()

	for _, member := range members {
		// Skip sender
		if member.UserID == senderID {
			continue
		}

		// Check if recipient is connected
		client, ok := h.clients[member.UserID]
		if !ok {
			// log.Printf("User %d not connected, skipping typing broadcast", member.UserID)
			continue
		}

		// Check if recipient has typing indicators enabled
		// Optimization: We could cache this or fetch in bulk, but for now fetch individually
		recipient, err := h.userRepo.GetByID(ctx, member.UserID)
		if err != nil {
			log.Printf("Error getting recipient %d for typing check: %v", member.UserID, err)
			continue
		}

		if !recipient.TypingIndicatorsEnabled {
			// log.Printf("Recipient %d has typing indicators disabled", member.UserID)
			continue
		}

		select {
		case client.send <- data:
		default:
			go h.unregisterClient(client)
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
