/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy/internal/middleware"
	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/services"
	nexy "github.com/vtstv/nexy/internal/ws"
)

type MessageController struct {
	messageService *services.MessageService
	hub            *nexy.Hub
}

func NewMessageController(messageService *services.MessageService, hub *nexy.Hub) *MessageController {
	return &MessageController{
		messageService: messageService,
		hub:            hub,
	}
}

func (c *MessageController) GetChatHistory(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	chatIDStr := r.URL.Query().Get("chat_id")
	if chatIDStr == "" {
		http.Error(w, "Missing chat_id parameter", http.StatusBadRequest)
		return
	}

	chatID, err := strconv.Atoi(chatIDStr)
	if err != nil {
		http.Error(w, "Invalid chat_id", http.StatusBadRequest)
		return
	}

	limit := 50
	if limitStr := r.URL.Query().Get("limit"); limitStr != "" {
		if l, err := strconv.Atoi(limitStr); err == nil {
			limit = l
		}
	}

	offset := 0
	if offsetStr := r.URL.Query().Get("offset"); offsetStr != "" {
		if o, err := strconv.Atoi(offsetStr); err == nil {
			offset = o
		}
	}

	messages, err := c.messageService.GetChatHistory(r.Context(), chatID, userID, limit, offset)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// Ensure messages is not nil to avoid JSON null instead of []
	if messages == nil {
		messages = []*models.Message{}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(messages)
}

func (c *MessageController) DeleteMessage(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req struct {
		MessageID string `json:"message_id"`
	}

	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.MessageID == "" {
		http.Error(w, "Missing message_id", http.StatusBadRequest)
		return
	}

	msg, err := c.messageService.DeleteMessage(r.Context(), req.MessageID, userID)
	if err != nil {
		if err.Error() == "message not found" {
			http.Error(w, "Message not found", http.StatusNotFound)
			return
		}
		if err.Error() == "unauthorized" {
			http.Error(w, "Unauthorized", http.StatusForbidden)
			return
		}
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	if c.hub != nil {
		c.hub.BroadcastDelete(msg)
	}

	w.WriteHeader(http.StatusNoContent)
}

func (c *MessageController) UpdateMessage(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	messageID := vars["id"]
	if messageID == "" {
		http.Error(w, "Message ID is required", http.StatusBadRequest)
		return
	}

	var req struct {
		Content string `json:"content"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.Content == "" {
		http.Error(w, "Content cannot be empty", http.StatusBadRequest)
		return
	}

	msg, err := c.messageService.UpdateMessage(r.Context(), messageID, userID, req.Content)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	if c.hub != nil {
		c.hub.BroadcastEdit(msg)
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(msg)
}

func (c *MessageController) SearchMessages(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	chatID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid chat ID", http.StatusBadRequest)
		return
	}

	query := r.URL.Query().Get("q")
	if query == "" {
		http.Error(w, "Query parameter 'q' is required", http.StatusBadRequest)
		return
	}

	messages, err := c.messageService.SearchMessages(r.Context(), chatID, userID, query)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// Ensure messages is not nil
	if messages == nil {
		messages = []*models.Message{}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(messages)
}

func (c *MessageController) GetMessageByID(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	messageID := vars["messageId"]
	if messageID == "" {
		http.Error(w, "Message ID is required", http.StatusBadRequest)
		return
	}

	var message *models.Message
	var err error

	// Try to parse as server ID (integer)
	if serverID, parseErr := strconv.Atoi(messageID); parseErr == nil {
		message, err = c.messageService.GetMessageByServerID(r.Context(), serverID, userID)
	} else {
		// Fall back to UUID lookup
		message, err = c.messageService.GetMessageByID(r.Context(), messageID, userID)
	}

	if err != nil {
		if err.Error() == "message not found" {
			http.Error(w, "Message not found", http.StatusNotFound)
			return
		}
		if err.Error() == "unauthorized" {
			http.Error(w, "Unauthorized", http.StatusForbidden)
			return
		}
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(message)
}
