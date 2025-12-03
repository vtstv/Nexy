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
	"github.com/vtstv/nexy/internal/services"
)

type UserController struct {
	userService *services.UserService
	qrService   *services.QRService
}

func NewUserController(userService *services.UserService, qrService *services.QRService) *UserController {
	return &UserController{
		userService: userService,
		qrService:   qrService,
	}
}

type UpdateProfileRequest struct {
	DisplayName string `json:"display_name"`
	Bio         string `json:"bio"`
	AvatarURL   string `json:"avatar_url"`
	Email       string `json:"email"`
	Password    string `json:"password"`
}

func (c *UserController) GetMe(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	user, err := c.userService.GetUserByID(r.Context(), userID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(user)
}

func (c *UserController) GetUserByID(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	userID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid user ID", http.StatusBadRequest)
		return
	}

	user, err := c.userService.GetUserByID(r.Context(), userID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(user)
}

func (c *UserController) UpdateProfile(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req UpdateProfileRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	user, err := c.userService.UpdateProfile(r.Context(), userID, req.DisplayName, req.Bio, req.AvatarURL, req.Email, req.Password)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(user)
}

func (c *UserController) SearchUsers(w http.ResponseWriter, r *http.Request) {
	query := r.URL.Query().Get("query")
	if query == "" {
		http.Error(w, "Missing query parameter", http.StatusBadRequest)
		return
	}

	limit := 20
	if limitStr := r.URL.Query().Get("limit"); limitStr != "" {
		if l, err := strconv.Atoi(limitStr); err == nil {
			limit = l
		}
	}

	users, err := c.userService.SearchUsers(r.Context(), query, limit)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(users)
}

func (c *UserController) GetMyQRCode(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	qrData := "nexy://user/" + strconv.Itoa(userID)
	qrCode, err := c.qrService.GenerateQRCode(qrData)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	response := map[string]string{
		"qr_code": qrCode,
		"data":    qrData,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

type CreateChatRequest struct {
	RecipientID int `json:"recipient_id"`
}

type CreateGroupChatRequest struct {
	Name      string `json:"name"`
	MemberIDs []int  `json:"member_ids"`
}

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
