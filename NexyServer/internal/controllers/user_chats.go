/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"encoding/json"
	"log"
	"net/http"
	"strconv"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy/internal/middleware"
)

type CreateChatRequest struct {
	RecipientID int `json:"recipient_id"`
}

type CreateGroupChatRequest struct {
	Name      string `json:"name"`
	MemberIDs []int  `json:"member_ids"`
}

// CreatePrivateChat creates or retrieves a private chat
func (c *UserController) CreatePrivateChat(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req CreateChatRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.RecipientID == 0 {
		http.Error(w, "recipient_id is required", http.StatusBadRequest)
		return
	}

	log.Printf("CreatePrivateChat: user1ID=%d, user2ID=%d", userID, req.RecipientID)
	chat, err := c.userService.GetOrCreatePrivateChat(r.Context(), userID, req.RecipientID)
	if err != nil {
		log.Printf("CreatePrivateChat ERROR: %v", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	log.Printf("CreatePrivateChat SUCCESS: chatID=%d, participants=%v", chat.ID, chat.ParticipantIds)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(chat)
}

// CreateGroupChat creates a new group chat
func (c *UserController) CreateGroupChat(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req CreateGroupChatRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.Name == "" {
		http.Error(w, "group name is required", http.StatusBadRequest)
		return
	}

	// Allow empty member list for personal notepad
	memberIDs := req.MemberIDs
	if len(memberIDs) == 0 {
		memberIDs = []int{userID} // Add creator as the only member
	}

	chat, err := c.userService.CreateGroupChat(r.Context(), userID, req.Name, memberIDs)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(chat)
}

// GetUserChats returns all user's chats
func (c *UserController) GetUserChats(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	chats, err := c.userService.GetUserChats(r.Context(), userID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(chats)
}

// GetChat returns specific chat details
func (c *UserController) GetChat(w http.ResponseWriter, r *http.Request) {
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

	log.Printf("GetChat: UserID=%d, ChatID=%d", userID, chatID)

	chat, err := c.userService.GetChat(r.Context(), userID, chatID)
	if err != nil {
		log.Printf("GetChat error: %v", err)
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(chat)
}

// DeleteChat deletes a chat for the user
func (c *UserController) DeleteChat(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	chatID, err := strconv.Atoi(vars["chatId"])
	if err != nil {
		http.Error(w, "Invalid chat ID", http.StatusBadRequest)
		return
	}

	if err := c.userService.DeleteChat(r.Context(), userID, chatID); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// ClearChatMessages clears all messages in a chat
func (c *UserController) ClearChatMessages(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	chatID, err := strconv.Atoi(vars["chatId"])
	if err != nil {
		http.Error(w, "Invalid chat ID", http.StatusBadRequest)
		return
	}

	if err := c.userService.ClearChatMessages(r.Context(), userID, chatID); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}
