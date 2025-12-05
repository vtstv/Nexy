/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"encoding/json"
	"net/http"
	"strconv"
	"time"

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

type MuteRequest struct {
	Until    *time.Time `json:"until,omitempty"`
	Duration string     `json:"duration,omitempty"` // "1h", "1d", "1m", "forever"
}

func (c *UserController) MuteChat(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	chatID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid chat ID", http.StatusBadRequest)
		return
	}

	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req MuteRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	var until *time.Time
	if req.Until != nil {
		until = req.Until
	} else if req.Duration != "" {
		now := time.Now()
		switch req.Duration {
		case "1h":
			t := now.Add(time.Hour)
			until = &t
		case "1d":
			t := now.Add(24 * time.Hour)
			until = &t
		case "1m": // 1 month = 30 days approx
			t := now.Add(30 * 24 * time.Hour)
			until = &t
		case "forever":
			t := now.AddDate(100, 0, 0) // 100 years
			until = &t
		default:
			// Try parsing duration string
			if d, err := time.ParseDuration(req.Duration); err == nil {
				t := now.Add(d)
				until = &t
			}
		}
	}

	if until == nil {
		http.Error(w, "Duration or until time required", http.StatusBadRequest)
		return
	}

	if err := c.userService.MuteChat(r.Context(), userID, chatID, until); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
}

func (c *UserController) UnmuteChat(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	chatID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid chat ID", http.StatusBadRequest)
		return
	}

	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	if err := c.userService.UnmuteChat(r.Context(), userID, chatID); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
}

func (c *UserController) PinChat(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	chatID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid chat ID", http.StatusBadRequest)
		return
	}

	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	if err := c.userService.PinChat(r.Context(), userID, chatID); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
}

func (c *UserController) UnpinChat(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	chatID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid chat ID", http.StatusBadRequest)
		return
	}

	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	if err := c.userService.UnpinChat(r.Context(), userID, chatID); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusOK)
}
