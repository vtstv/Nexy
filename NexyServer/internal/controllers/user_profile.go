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
)

type UpdateProfileRequest struct {
	DisplayName             string `json:"display_name"`
	Bio                     string `json:"bio"`
	AvatarURL               string `json:"avatar_url"`
	Email                   string `json:"email"`
	Password                string `json:"password"`
	ReadReceiptsEnabled     *bool  `json:"read_receipts_enabled"`
	TypingIndicatorsEnabled *bool  `json:"typing_indicators_enabled"`
	ShowOnlineStatus        *bool  `json:"show_online_status"`
}

// GetMe returns current user profile
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

// GetUserByID returns user profile by ID
func (c *UserController) GetUserByID(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	userID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid user ID", http.StatusBadRequest)
		return
	}

	// Get requesting user ID for privacy filter
	requestingUserID, _ := middleware.GetUserID(r)

	user, err := c.userService.GetUserByIDWithStatus(r.Context(), userID, requestingUserID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(user)
}

// UpdateProfile updates user profile
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

	user, err := c.userService.UpdateProfile(r.Context(), userID, req.DisplayName, req.Bio, req.AvatarURL, req.Email, req.Password, req.ReadReceiptsEnabled, req.TypingIndicatorsEnabled, req.ShowOnlineStatus)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(user)
}

// SearchUsers searches for users by query
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

// GetMyQRCode generates QR code for current user
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
