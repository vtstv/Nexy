package controllers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy-admin/internal/models"
	"github.com/vtstv/nexy-admin/internal/services"
)

type MessageController struct {
	service *services.MessageService
}

func NewMessageController(service *services.MessageService) *MessageController {
	return &MessageController{service: service}
}

func (c *MessageController) GetMessages(w http.ResponseWriter, r *http.Request) {
	params := parseMessagePaginationParams(r)

	messages, err := c.service.GetMessages(r.Context(), params)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(messages)
}

func (c *MessageController) GetMessage(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	id, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "invalid message id", http.StatusBadRequest)
		return
	}

	message, err := c.service.GetMessage(r.Context(), id)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(message)
}

func (c *MessageController) DeleteMessage(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	id, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "invalid message id", http.StatusBadRequest)
		return
	}

	if err := c.service.DeleteMessage(r.Context(), id); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "success"})
}

func (c *MessageController) SearchMessages(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query().Get("q")
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit <= 0 {
		limit = 50
	}

	messages, err := c.service.SearchMessages(r.Context(), query, limit)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(messages)
}

func (c *MessageController) GetChatMessages(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	chatID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "invalid chat id", http.StatusBadRequest)
		return
	}

	params := parseMessageFilterParams(r)
	messages, err := c.service.GetChatMessages(r.Context(), chatID, params)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(messages)
}

func parseMessagePaginationParams(r *http.Request) models.PaginationParams {
	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	if page < 1 {
		page = 1
	}

	pageSize, _ := strconv.Atoi(r.URL.Query().Get("page_size"))
	if pageSize < 1 {
		pageSize = 50
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

func parseMessageFilterParams(r *http.Request) models.MessageFilterParams {
	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	if page < 1 {
		page = 1
	}

	pageSize, _ := strconv.Atoi(r.URL.Query().Get("page_size"))
	if pageSize < 1 {
		pageSize = 50
	}

	senderID, _ := strconv.Atoi(r.URL.Query().Get("sender_id"))

	return models.MessageFilterParams{
		Page:        page,
		PageSize:    pageSize,
		Search:      r.URL.Query().Get("search"),
		StartDate:   r.URL.Query().Get("start_date"),
		EndDate:     r.URL.Query().Get("end_date"),
		SenderID:    senderID,
		MessageType: r.URL.Query().Get("message_type"),
	}
}
