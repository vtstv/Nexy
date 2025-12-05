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
	"github.com/vtstv/nexy/internal/services"
)

type SyncController struct {
	syncService *services.SyncService
}

func NewSyncController(syncService *services.SyncService) *SyncController {
	return &SyncController{syncService: syncService}
}

// GetState returns the current sync state for the user
// GET /api/sync/state
func (c *SyncController) GetState(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	state, err := c.syncService.GetState(r.Context(), userID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(state)
}

// GetDifference returns updates since the given pts
// GET /api/sync/difference?pts=123&limit=100
// Similar to Telegram's updates.getDifference
func (c *SyncController) GetDifference(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	ptsStr := r.URL.Query().Get("pts")
	if ptsStr == "" {
		http.Error(w, "Missing pts parameter", http.StatusBadRequest)
		return
	}

	pts, err := strconv.Atoi(ptsStr)
	if err != nil {
		http.Error(w, "Invalid pts parameter", http.StatusBadRequest)
		return
	}

	limit := 100
	if limitStr := r.URL.Query().Get("limit"); limitStr != "" {
		if l, err := strconv.Atoi(limitStr); err == nil && l > 0 {
			limit = l
		}
	}

	diff, err := c.syncService.GetDifference(r.Context(), userID, pts, limit)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(diff)
}

// GetChannelDifference returns updates for a specific channel
// GET /api/sync/channel/{id}/difference?pts=123&limit=100
func (c *SyncController) GetChannelDifference(w http.ResponseWriter, r *http.Request) {
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

	ptsStr := r.URL.Query().Get("pts")
	if ptsStr == "" {
		http.Error(w, "Missing pts parameter", http.StatusBadRequest)
		return
	}

	pts, err := strconv.Atoi(ptsStr)
	if err != nil {
		http.Error(w, "Invalid pts parameter", http.StatusBadRequest)
		return
	}

	limit := 100
	if limitStr := r.URL.Query().Get("limit"); limitStr != "" {
		if l, err := strconv.Atoi(limitStr); err == nil && l > 0 {
			limit = l
		}
	}

	diff, err := c.syncService.GetChannelDifference(r.Context(), userID, chatID, pts, limit)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(diff)
}
