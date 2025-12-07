package controllers

import (
	"encoding/json"
	"log"
	"net/http"
	"strconv"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy/internal/middleware"
	"github.com/vtstv/nexy/internal/services"
	nexy "github.com/vtstv/nexy/internal/ws"
)

type ReactionController struct {
	reactionService *services.ReactionService
	hub             *nexy.Hub
}

func NewReactionController(reactionService *services.ReactionService, hub *nexy.Hub) *ReactionController {
	return &ReactionController{
		reactionService: reactionService,
		hub:             hub,
	}
}

type AddReactionRequest struct {
	MessageID int    `json:"message_id"`
	Emoji     string `json:"emoji"`
}

type RemoveReactionRequest struct {
	MessageID int    `json:"message_id"`
	Emoji     string `json:"emoji"`
}

func (c *ReactionController) AddReaction(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}

	var req AddReactionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		log.Printf("Failed to decode reaction request: %v", err)
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}

	log.Printf("AddReaction request: userID=%d, messageID=%d, emoji=%s", userID, req.MessageID, req.Emoji)

	result, err := c.reactionService.AddReaction(r.Context(), req.MessageID, userID, req.Emoji)
	if err != nil {
		log.Printf("AddReaction error: %v", err)
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	// If there was an old reaction (different emoji), broadcast its removal first
	if result.OldEmoji != "" && result.OldEmoji != req.Emoji {
		c.hub.BroadcastReactionRemove(result.ChatID, req.MessageID, userID, result.OldEmoji)
	}

	// If a new reaction was added (not just toggled off), broadcast it
	if result.IsNewReaction {
		c.hub.BroadcastReactionAdd(result.ChatID, req.MessageID, userID, req.Emoji)
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"message": "reaction added"})
	} else {
		// Reaction was toggled off (same emoji clicked)
		c.hub.BroadcastReactionRemove(result.ChatID, req.MessageID, userID, req.Emoji)
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(map[string]string{"message": "reaction removed"})
	}
}

func (c *ReactionController) RemoveReaction(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}

	var req RemoveReactionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}

	chatID, err := c.reactionService.RemoveReaction(r.Context(), req.MessageID, userID, req.Emoji)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	// Broadcast reaction removal to all chat members via WebSocket
	c.hub.BroadcastReactionRemove(chatID, req.MessageID, userID, req.Emoji)

	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{"message": "reaction removed"})
}

func (c *ReactionController) GetReactions(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	messageID, err := strconv.Atoi(vars["messageId"])
	if err != nil {
		http.Error(w, "invalid message ID", http.StatusBadRequest)
		return
	}

	reactions, err := c.reactionService.GetReactionsByMessageID(r.Context(), messageID, userID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(reactions)
}
