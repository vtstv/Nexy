package nexy

import (
	"context"
	"encoding/json"
	"log"
)

func (h *Hub) sendToUser(userID int, message *NexyMessage, unregisterFunc func(*Client)) {
	h.mu.RLock()
	client, ok := h.clients[userID]
	h.mu.RUnlock()

	if !ok {
		log.Printf("User %d not connected, will send FCM notification", userID)

		// Send FCM notification for chat messages when user is offline
		if message.Header.Type == TypeChatMessage {
			go h.sendFcmForMessage(userID, message)
		}
		return
	}

	data, err := json.Marshal(message)
	if err != nil {
		log.Printf("Error marshaling message: %v", err)
		return
	}

	select {
	case client.send <- data:
		log.Printf("Message sent to user %d", userID)
	default:
		log.Printf("Send channel full for user %d, unregistering", userID)
		if unregisterFunc != nil {
			go unregisterFunc(client)
		}
	}
}

func (h *Hub) broadcastToAll(message *NexyMessage, unregisterFunc func(*Client)) {
	data, err := json.Marshal(message)
	if err != nil {
		log.Printf("Error marshaling broadcast message: %v", err)
		return
	}

	h.mu.RLock()
	clients := make([]*Client, 0, len(h.clients))
	for _, client := range h.clients {
		clients = append(clients, client)
	}
	h.mu.RUnlock()

	for _, client := range clients {
		select {
		case client.send <- data:
		default:
			if unregisterFunc != nil {
				go unregisterFunc(client)
			}
		}
	}
}

// SendToUser sends a message to a specific user (public method for external use)
func (h *Hub) SendToUser(userID int, message *NexyMessage) {
	h.sendToUser(userID, message, func(c *Client) {
		h.unregister <- c
	})
}

func (h *Hub) broadcastToChatMembers(chatID int, message *NexyMessage) {
	ctx := context.Background()
	memberIDs, err := h.chatRepo.GetChatMembers(ctx, chatID)
	if err != nil {
		log.Printf("Error getting chat members: %v", err)
		return
	}

	data, _ := json.Marshal(message)

	h.mu.RLock()
	onlineClients := make(map[int]*Client)
	for id, client := range h.clients {
		onlineClients[id] = client
	}
	h.mu.RUnlock()

	for _, memberID := range memberIDs {
		if client, ok := onlineClients[memberID]; ok {
			// Member is online, send via WebSocket
			select {
			case client.send <- data:
			default:
				go func(c *Client) {
					h.unregister <- c
				}(client)
			}
		} else {
			// Member is offline, send FCM notification
			if message.Header.Type == TypeChatMessage {
				log.Printf("Member %d is offline, sending FCM notification", memberID)
				go h.sendFcmForMessage(memberID, message)
			}
		}
	}
}

// sendFcmForMessage sends a push notification for a chat message
func (h *Hub) sendFcmForMessage(userID int, message *NexyMessage) {
	ctx := context.Background()

	// Parse message body to get content
	var messageBody ChatMessageBody
	if err := json.Unmarshal(message.Body, &messageBody); err != nil {
		log.Printf("Error unmarshaling message body for FCM: %v", err)
		return
	}

	// Get sender info
	sender, err := h.userRepo.GetByID(ctx, message.Header.SenderID)
	if err != nil {
		log.Printf("Error getting sender info for FCM: %v", err)
		return
	}

	// Prepare notification title and body
	title := sender.DisplayName
	if title == "" {
		title = sender.Username
	}

	notifBody := messageBody.Content
	if messageBody.MessageType == "media" || messageBody.MessageType == "file" {
		notifBody = "Sent a " + messageBody.MessageType
	} else if len(notifBody) > 100 {
		notifBody = notifBody[:100] + "..."
	}

	// Prepare data payload
	data := map[string]string{
		"type":       string(TypeChatMessage),
		"message_id": message.Header.MessageID,
	}

	if message.Header.ChatID != nil {
		data["chat_id"] = string(rune(*message.Header.ChatID))
	}

	// Send FCM notification
	if err := h.fcmService.SendNotification(ctx, userID, title, notifBody, data); err != nil {
		log.Printf("Error sending FCM notification: %v", err)
	}
}
