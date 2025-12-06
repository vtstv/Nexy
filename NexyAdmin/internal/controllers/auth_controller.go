package controllers

import (
	"encoding/json"
	"net/http"

	"github.com/vtstv/nexy-admin/internal/models"
	"github.com/vtstv/nexy-admin/internal/services"
)

type AuthController struct {
	service *services.AuthService
}

func NewAuthController(service *services.AuthService) *AuthController {
	return &AuthController{service: service}
}

func (c *AuthController) Login(w http.ResponseWriter, r *http.Request) {
	var creds models.AdminCredentials
	if err := json.NewDecoder(r.Body).Decode(&creds); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}

	tokenResp, err := c.service.Login(r.Context(), creds.Username, creds.Password)
	if err != nil {
		http.Error(w, "invalid credentials", http.StatusUnauthorized)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(tokenResp)
}
