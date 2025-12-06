package controllers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy-admin/internal/models"
	"github.com/vtstv/nexy-admin/internal/services"
)

type ChatController struct {
	service *services.ChatService
}

func NewChatController(service *services.ChatService) *ChatController {
	return &ChatController{service: service}
}

func (c *ChatController) GetChats(w http.ResponseWriter, r *http.Request) {
	params := parsePaginationParamsForChats(r)

	chats, err := c.service.GetChats(r.Context(), params)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(chats)
}

func (c *ChatController) GetChat(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	id, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "invalid chat id", http.StatusBadRequest)
		return
	}

	chat, err := c.service.GetChat(r.Context(), id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(chat)
}

func (c *ChatController) UpdateChat(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	id, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "invalid chat id", http.StatusBadRequest)
		return
	}

	var chat models.Chat
	if err := json.NewDecoder(r.Body).Decode(&chat); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}

	chat.ID = id
	if err := c.service.UpdateChat(r.Context(), &chat); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "success"})
}

func (c *ChatController) DeleteChat(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	id, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "invalid chat id", http.StatusBadRequest)
		return
	}

	if err := c.service.DeleteChat(r.Context(), id); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "success"})
}

func (c *ChatController) GetChatMembers(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	id, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "invalid chat id", http.StatusBadRequest)
		return
	}

	members, err := c.service.GetChatMembers(r.Context(), id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(members)
}

func (c *ChatController) RemoveChatMember(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	chatID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "invalid chat id", http.StatusBadRequest)
		return
	}

	userID, err := strconv.Atoi(vars["userId"])
	if err != nil {
		http.Error(w, "invalid user id", http.StatusBadRequest)
		return
	}

	if err := c.service.RemoveChatMember(r.Context(), chatID, userID); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "success"})
}

func parsePaginationParamsForChats(r *http.Request) models.PaginationParams {
	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	if page < 1 {
		page = 1
	}

	pageSize, _ := strconv.Atoi(r.URL.Query().Get("page_size"))
	if pageSize < 1 {
		pageSize = 20
	}

	sortDir := r.URL.Query().Get("sort_dir")
	if sortDir != "asc" && sortDir != "desc" {
		sortDir = "desc"
	}

	return models.PaginationParams{
		Page:     page,
		PageSize: pageSize,
		Search:   r.URL.Query().Get("search"),
		SortBy:   r.URL.Query().Get("sort_by"),
		SortDir:  sortDir,
	}
}
