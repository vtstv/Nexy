/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"net/http"
	"strings"

	"github.com/vtstv/nexy/internal/services"
	nexy "github.com/vtstv/nexy/internal/ws"
)

type WSController struct {
	wsHandler   *nexy.WSHandler
	authService *services.AuthService
}

func NewWSController(wsHandler *nexy.WSHandler, authService *services.AuthService) *WSController {
	return &WSController{
		wsHandler:   wsHandler,
		authService: authService,
	}
}

func (c *WSController) HandleWebSocket(w http.ResponseWriter, r *http.Request) {
	// Try to get token from query parameter (for WebSocket connections)
	token := r.URL.Query().Get("token")

	// Fallback to Authorization header
	if token == "" {
		authHeader := r.Header.Get("Authorization")
		if authHeader != "" {
			parts := strings.Split(authHeader, " ")
			if len(parts) == 2 && parts[0] == "Bearer" {
				token = parts[1]
			}
		}
	}

	if token == "" {
		http.Error(w, "Missing authentication token", http.StatusUnauthorized)
		return
	}

	// Validate token and get user ID
	userID, err := c.authService.ValidateToken(token)
	if err != nil {
		http.Error(w, "Invalid token", http.StatusUnauthorized)
		return
	}

	c.wsHandler.ServeWS(w, r, userID)
}
