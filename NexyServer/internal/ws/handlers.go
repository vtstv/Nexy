package nexy

import (
	"context"
	"encoding/json"
	"log"
	"strings"
	"time"

	"github.com/vtstv/nexy/internal/models"
)

func (h *Hub) handleChatMessage(message *NexyMessage, unregisterFunc func(*Client)) {
	ctx := context.Background()

	// Check if this is a private message without existing chat
	if message.Header.ChatID == nil && message.Header.RecipientID != nil {
		// Try to find existing private chat
		existingChat, err := h.chatRepo.GetPrivateChatBetween(ctx, message.Header.SenderID, *message.Header.RecipientID)
		if err == nil && existingChat != nil {
			message.Header.ChatID = &existingChat.ID
			log.Printf("Found existing private chat: chatID=%d", existingChat.ID)
		} else {
			// Create new private chat
			newChat := &models.Chat{
				Type:           "private",
				ParticipantIds: []int{message.Header.SenderID, *message.Header.RecipientID},
			}

			if err := h.chatRepo.Create(ctx, newChat); err != nil {
				log.Printf("Error creating private chat: %v", err)
				return
			}

			member1 := &models.ChatMember{
				ChatID: newChat.ID,
				UserID: message.Header.SenderID,
				Role:   "owner",
			}
			member2 := &models.ChatMember{
				ChatID: newChat.ID,
				UserID: *message.Header.RecipientID,
				Role:   "member",
			}

			if err := h.chatRepo.AddMember(ctx, member1); err != nil {
				log.Printf("Error adding member1: %v", err)
				return
			}
			if err := h.chatRepo.AddMember(ctx, member2); err != nil {
				log.Printf("Error adding member2: %v", err)
				return
			}

			log.Printf("Created new private chat: chatID=%d", newChat.ID)
			message.Header.ChatID = &newChat.ID

			// Notify both participants about the new chat
			chatCreatedBody := ChatCreatedBody{
				ChatID:         newChat.ID,
				ChatType:       newChat.Type,
				ParticipantIDs: []int{message.Header.SenderID, *message.Header.RecipientID},
				CreatedBy:      message.Header.SenderID,
			}

			chatCreatedMsg, _ := NewNexyMessage(TypeChatCreated, message.Header.SenderID, nil, chatCreatedBody)
			log.Printf("Notifying users about new chat: %d and %d", message.Header.SenderID, *message.Header.RecipientID)
			h.sendToUser(message.Header.SenderID, chatCreatedMsg, unregisterFunc)
			h.sendToUser(*message.Header.RecipientID, chatCreatedMsg, unregisterFunc)
		}
	}

	if message.Header.ChatID == nil {
		log.Printf("No chat ID in message, dropping")
		return
	}

	// Check for voice message restriction
	var body ChatMessageBody
	if err := json.Unmarshal(message.Body, &body); err == nil && body.MessageType == "voice" {
		chat, err := h.chatRepo.GetByID(ctx, *message.Header.ChatID)
		if err == nil && chat != nil && chat.Type == "private" {
			// Get members to find the recipient
			members, err := h.chatRepo.GetChatMembers(ctx, chat.ID)
			if err == nil {
				for _, memberID := range members {
					if memberID != message.Header.SenderID {
						// Check if this user has voice messages enabled
						user, err := h.userRepo.GetByID(ctx, memberID)
						if err == nil && user != nil && !user.VoiceMessagesEnabled {
							log.Printf("Voice message rejected: recipient %d has disabled voice messages", memberID)
							errorAck, _ := NewNexyMessage(TypeAck, 0, nil, AckBody{
								MessageID: message.Header.MessageID,
								Status:    "error",
								Error:     "Voice messages are disabled by the recipient",
							})
							h.sendToUser(message.Header.SenderID, errorAck, unregisterFunc)
							return
						}
					}
				}
			}
		}
	}

	// Save message to database
	if err := h.messageRepo.CreateMessageFromWebSocket(ctx, message.Header.MessageID, *message.Header.ChatID, message.Header.SenderID, message.Body); err != nil {
		log.Printf("Error saving message to database: %v", err)

		// If it's a duplicate key error, the message was already saved - send OK ACK
		if strings.Contains(err.Error(), "duplicate key") {
			log.Printf("Message %s already exists, sending OK ACK", message.Header.MessageID)
			ack, _ := NewNexyMessage(TypeAck, 0, nil, AckBody{MessageID: message.Header.MessageID, Status: "ok"})
			h.sendToUser(message.Header.SenderID, ack, unregisterFunc)
			return
		}

		// For other errors, send error ACK
		errorAck, _ := NewNexyMessage(TypeAck, 0, nil, AckBody{MessageID: message.Header.MessageID, Status: "error"})
		h.sendToUser(message.Header.SenderID, errorAck, unregisterFunc)
		return
	}

	log.Printf("Message saved to database: messageID=%s, chatID=%d", message.Header.MessageID, *message.Header.ChatID)

	// Send ACK to sender confirming message was saved
	ack, _ := NewNexyMessage(TypeAck, 0, nil, AckBody{MessageID: message.Header.MessageID, Status: "ok"})
	h.sendToUser(message.Header.SenderID, ack, unregisterFunc)
	log.Printf("ACK sent to sender %d for message %s", message.Header.SenderID, message.Header.MessageID)

	// Broadcast to chat members
	h.broadcastToChatMembers(*message.Header.ChatID, message)
	log.Printf("Message broadcasted to chat members: chatID=%d", *message.Header.ChatID)
}

func (h *Hub) handleEditMessage(message *NexyMessage) {
	ctx := context.Background()

	var editBody EditMessageBody
	if err := json.Unmarshal(message.Body, &editBody); err != nil {
		log.Printf("Error unmarshaling edit body: %v", err)
		return
	}

	dbMsg, err := h.messageRepo.GetByUUID(ctx, editBody.MessageID)
	if err != nil {
		log.Printf("Error getting message by UUID: %v", err)
		return
	}

	if dbMsg.SenderID != message.Header.SenderID {
		log.Printf("User %d attempted to edit message from user %d", message.Header.SenderID, dbMsg.SenderID)
		return
	}

	dbMsg.Content = editBody.Content
	dbMsg.IsEdited = true
	dbMsg.UpdatedAt = time.Now()

	if err := h.messageRepo.Update(ctx, dbMsg); err != nil {
		log.Printf("Error updating message: %v", err)
		return
	}

	message.Header.ChatID = &dbMsg.ChatID
	h.broadcastToChatMembers(dbMsg.ChatID, message)
	log.Printf("Edit broadcasted to chat members: chatID=%d, messageID=%s", dbMsg.ChatID, editBody.MessageID)
}

func (h *Hub) handleTypingMessage(message *NexyMessage, unregisterFunc func(*Client)) {
	ctx := context.Background()

	var typingBody TypingBody
	if err := json.Unmarshal(message.Body, &typingBody); err != nil {
		log.Printf("Error unmarshaling typing body: %v", err)
		return
	}

	senderID := message.Header.SenderID

	// Check if sender has typing indicators enabled
	sender, err := h.userRepo.GetByID(ctx, senderID)
	if err != nil {
		log.Printf("Error getting sender for typing check: %v", err)
		return
	}

	if !sender.TypingIndicatorsEnabled {
		log.Printf("Typing indicator suppressed by sender %d settings", senderID)
		return
	}

	// Update typing status
	h.typingMu.Lock()
	if h.typingStatus[senderID] == nil {
		h.typingStatus[senderID] = make(map[int]bool)
	}
	h.typingStatus[senderID][typingBody.ChatID] = typingBody.IsTyping
	h.typingMu.Unlock()

	// Broadcast to chat members who also have typing indicators enabled
	members, err := h.chatRepo.GetChatMembers(ctx, typingBody.ChatID)
	if err != nil {
		log.Printf("Error getting chat members for typing broadcast: %v", err)
		return
	}

	data, _ := json.Marshal(message)

	for _, memberID := range members {
		// Skip sender
		if memberID == senderID {
			continue
		}

		// Check if recipient is connected
		h.mu.RLock()
		clients, ok := h.clients[memberID]
		h.mu.RUnlock()

		if !ok || len(clients) == 0 {
			continue
		}

		// Check if recipient has typing indicators enabled
		recipient, err := h.userRepo.GetByID(ctx, memberID)
		if err != nil {
			log.Printf("Error getting recipient %d for typing check: %v", memberID, err)
			continue
		}

		if !recipient.TypingIndicatorsEnabled {
			continue
		}

		// Send to all devices
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
}

func (h *Hub) handleStatusMessage(message *NexyMessage, unregisterFunc func(*Client)) {
	ctx := context.Background()

	if message.Header.Type == TypeDelivered {
		if message.Header.RecipientID != nil {
			h.sendToUser(*message.Header.RecipientID, message, unregisterFunc)
		}
		return
	}

	if message.Header.Type == TypeRead {
		if message.Header.ChatID != nil {
			var readBody ReadBody
			if err := json.Unmarshal(message.Body, &readBody); err == nil {
				log.Printf("Processing read receipt: chatID=%d, senderID=%d, messageID=%s",
					*message.Header.ChatID, message.Header.SenderID, readBody.MessageID)

				// Get message to mark as read
				msg, err := h.messageRepo.GetByUUID(ctx, readBody.MessageID)
				if err != nil {
					log.Printf("Error getting message by UUID for read receipt: %v", err)
				} else if msg != nil {
					// Mark this message and all previous unread messages in this chat as read
					if err := h.messageRepo.MarkMessagesAsRead(ctx, msg.ChatID, message.Header.SenderID, msg.ID); err != nil {
						log.Printf("Failed to mark messages as read in DB: %v", err)
					}
				}

				// Check if sender has read receipts enabled
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
				members, err := h.chatRepo.GetChatMembers(ctx, *message.Header.ChatID)
				if err != nil {
					log.Printf("Error getting chat members for read receipt broadcast: %v", err)
					return
				}

				log.Printf("Broadcasting read receipt to %d members", len(members))

				data, _ := json.Marshal(message)

				for _, memberID := range members {
					// Skip sender (the one who read it)
					if memberID == message.Header.SenderID {
						log.Printf("Skipping sender %d", memberID)
						continue
					}

					// Check if recipient is connected
					h.mu.RLock()
					clients, ok := h.clients[memberID]
					h.mu.RUnlock()

					if !ok || len(clients) == 0 {
						log.Printf("Recipient %d not connected", memberID)
						continue
					}

					// Check if recipient has read receipts enabled (Reciprocal privacy)
					recipient, err := h.userRepo.GetByID(ctx, memberID)
					if err != nil {
						log.Printf("Error getting recipient %d: %v", memberID, err)
						continue
					}

					if !recipient.ReadReceiptsEnabled {
						log.Printf("Read receipt suppressed by recipient %d settings", memberID)
						continue
					}

					log.Printf("Sending read receipt to user %d", memberID)
					// Send to all devices
					for _, client := range clients {
						select {
						case client.send <- data:
							log.Printf("Read receipt sent to user %d, deviceID=%s", memberID, client.deviceID)
						default:
							log.Printf("Failed to send read receipt to user %d (channel full)", memberID)
							if unregisterFunc != nil {
								go unregisterFunc(client)
							}
						}
					}
				}
			}
		}
		return
	}

	if message.Header.RecipientID != nil {
		h.sendToUser(*message.Header.RecipientID, message, unregisterFunc)
	}
}

func (h *Hub) handleSignalingMessage(message *NexyMessage, unregisterFunc func(*Client)) {
	if message.Header.RecipientID != nil {
		h.sendToUser(*message.Header.RecipientID, message, unregisterFunc)
	}
}

func (h *Hub) clearTypingStatusForUser(userID int) []int {
	h.typingMu.Lock()
	defer h.typingMu.Unlock()

	chatIDs := make([]int, 0)
	if userChats, ok := h.typingStatus[userID]; ok {
		for chatID := range userChats {
			chatIDs = append(chatIDs, chatID)
		}
		delete(h.typingStatus, userID)
	}

	return chatIDs
}
