/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"encoding/json"
	"net/http"

	"github.com/vtstv/nexy/internal/middleware"
	"github.com/vtstv/nexy/internal/services"
)

type InviteController struct {
	inviteService *services.InviteService
}

func NewInviteController(inviteService *services.InviteService) *InviteController {
	return &InviteController{inviteService: inviteService}
}

type CreateInviteRequest struct {
	MaxUses        int `json:"max_uses"`
	ExpiresInHours int `json:"expires_in_hours"`
}

type ValidateInviteRequest struct {
	Code string `json:"code"`
}

func (c *InviteController) CreateInvite(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req CreateInviteRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	if req.MaxUses <= 0 {
		req.MaxUses = 1
	}

	invite, err := c.inviteService.CreateInvite(r.Context(), userID, req.MaxUses, req.ExpiresInHours)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(invite)
}

func (c *InviteController) ValidateInvite(w http.ResponseWriter, r *http.Request) {
	var req ValidateInviteRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	invite, err := c.inviteService.ValidateInvite(r.Context(), req.Code)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(invite)
}

func (c *InviteController) UseInvite(w http.ResponseWriter, r *http.Request) {
	var req ValidateInviteRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	if err := c.inviteService.UseInvite(r.Context(), req.Code); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (c *InviteController) GetMyInvites(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	invites, err := c.inviteService.GetUserInvites(r.Context(), userID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(invites)
}
