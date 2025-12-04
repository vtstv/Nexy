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
		log.Printf("User %d not connected, skipping message", userID)
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

func (h *Hub) broadcastToChatMembers(chatID int, message *NexyMessage) {
	ctx := context.Background()
	memberIDs, err := h.chatRepo.GetChatMembers(ctx, chatID)
	if err != nil {
		log.Printf("Error getting chat members: %v", err)
		return
	}

	data, _ := json.Marshal(message)

	h.mu.RLock()
	defer h.mu.RUnlock()

	for _, memberID := range memberIDs {
		if client, ok := h.clients[memberID]; ok {
			select {
			case client.send <- data:
			default:
				go func(c *Client) {
					h.unregister <- c
				}(client)
			}
		}
	}
}
