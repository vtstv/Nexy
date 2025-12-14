package nexy

import (
	"context"
	"encoding/json"
	"log"
)

func (h *Hub) sendToUser(userID int, message *NexyMessage, unregisterFunc func(*Client)) {
	h.mu.RLock()
	clients, ok := h.clients[userID]
	h.mu.RUnlock()

	if !ok || len(clients) == 0 {
		log.Printf("User %d not connected, will send FCM notification", userID)

		// Send FCM notification for chat messages when user is offline
		// Skip if this is the sender (they shouldn't get notification for their own message)
		if message.Header.Type == TypeChatMessage && userID != message.Header.SenderID {
			go h.sendFcmForMessage(userID, message)
		}
		return
	}

	data, err := json.Marshal(message)
	if err != nil {
		log.Printf("Error marshaling message: %v", err)
		return
	}

	// Send to all connections for this user
	for _, client := range clients {
		select {
		case client.send <- data:
			log.Printf("Message sent to user %d, deviceID=%s", userID, client.deviceID)
		default:
			log.Printf("Send channel full for user %d, deviceID=%s, unregistering", userID, client.deviceID)
			if unregisterFunc != nil {
				go unregisterFunc(client)
			}
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
	var allClients []*Client
	for _, userClients := range h.clients {
		allClients = append(allClients, userClients...)
	}
	h.mu.RUnlock()

	for _, client := range allClients {
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
	onlineClients := make(map[int][]*Client)
	for id, clients := range h.clients {
		onlineClients[id] = clients
	}
	h.mu.RUnlock()

	for _, memberID := range memberIDs {
		// Skip the sender - they don't need notification for their own message
		if memberID == message.Header.SenderID {
			continue
		}
		
		if clients, ok := onlineClients[memberID]; ok && len(clients) > 0 {
			// Member is online, send via WebSocket to all their devices
			for _, client := range clients {
				select {
				case client.send <- data:
				default:
					go func(c *Client) {
						h.unregister <- c
					}(client)
				}
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

	if userID == message.Header.SenderID {
		log.Printf("Skipping FCM notification for sender %d", userID)
		return
	}

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
