/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"encoding/json"
	"log"
	"net/http"

	"github.com/vtstv/nexy/internal/middleware"
	"github.com/vtstv/nexy/internal/services"
)

type FcmController struct {
	fcmService *services.FcmService
}

func NewFcmController(fcmService *services.FcmService) *FcmController {
	return &FcmController{
		fcmService: fcmService,
	}
}

type UpdateFcmTokenRequest struct {
	FcmToken string `json:"fcm_token"`
}

func (c *FcmController) UpdateFcmToken(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req UpdateFcmTokenRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}

	if req.FcmToken == "" {
		http.Error(w, "fcm_token is required", http.StatusBadRequest)
		return
	}

	err := c.fcmService.UpdateUserFcmToken(r.Context(), userID, req.FcmToken)
	if err != nil {
		log.Printf("Error updating FCM token for user %d: %v", userID, err)
		http.Error(w, "failed to update fcm token", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{
		"message": "FCM token updated successfully",
	})
}

func (c *FcmController) DeleteFcmToken(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	err := c.fcmService.DeleteUserFcmToken(r.Context(), userID)
	if err != nil {
		log.Printf("Error deleting FCM token for user %d: %v", userID, err)
		http.Error(w, "failed to delete fcm token", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]string{
		"message": "FCM token deleted successfully",
	})
}
