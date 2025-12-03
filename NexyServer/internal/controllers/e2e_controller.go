/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/vtstv/nexy/internal/middleware"
	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/services"
)

type E2EController struct {
	e2eService *services.E2EService
}

func NewE2EController(e2eService *services.E2EService) *E2EController {
	return &E2EController{e2eService: e2eService}
}

type UploadKeysRequest struct {
	IdentityKey  string           `json:"identity_key"`
	SignedPreKey SignedPreKeyData `json:"signed_pre_key"`
	PreKeys      []PreKeyData     `json:"pre_keys"`
}

type SignedPreKeyData struct {
	KeyID     int    `json:"key_id"`
	PublicKey string `json:"public_key"`
	Signature string `json:"signature"`
}

type PreKeyData struct {
	KeyID     int    `json:"key_id"`
	PublicKey string `json:"public_key"`
}

type KeyBundleResponse struct {
	Bundle *models.KeyBundle `json:"bundle"`
}

// Upload E2E encryption keys
func (c *E2EController) UploadKeys(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req UploadKeysRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	ctx := r.Context()

	// Upload identity key
	if req.IdentityKey != "" {
		if err := c.e2eService.UploadIdentityKey(ctx, userID, req.IdentityKey); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}

	// Upload signed prekey
	if req.SignedPreKey.PublicKey != "" {
		err := c.e2eService.UploadSignedPreKey(
			ctx,
			userID,
			req.SignedPreKey.KeyID,
			req.SignedPreKey.PublicKey,
			req.SignedPreKey.Signature,
		)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}

	// Upload one-time prekeys
	if len(req.PreKeys) > 0 {
		preKeys := make([]models.PreKey, len(req.PreKeys))
		for i, pk := range req.PreKeys {
			preKeys[i] = models.PreKey{
				UserID:    userID,
				KeyID:     pk.KeyID,
				PublicKey: pk.PublicKey,
			}
		}
		if err := c.e2eService.UploadPreKeys(ctx, userID, preKeys); err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"status":  "ok",
		"message": "Keys uploaded successfully",
	})
}

// Get key bundle for a user
func (c *E2EController) GetKeyBundle(w http.ResponseWriter, r *http.Request) {
	_, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	targetUserIDStr := r.URL.Query().Get("user_id")
	if targetUserIDStr == "" {
		http.Error(w, "user_id is required", http.StatusBadRequest)
		return
	}

	targetUserID, err := strconv.Atoi(targetUserIDStr)
	if err != nil {
		http.Error(w, "Invalid user_id", http.StatusBadRequest)
		return
	}

	deviceID := 1 // Default device ID, can be extended for multi-device
	deviceIDStr := r.URL.Query().Get("device_id")
	if deviceIDStr != "" {
		if did, err := strconv.Atoi(deviceIDStr); err == nil {
			deviceID = did
		}
	}

	bundle, err := c.e2eService.GetKeyBundle(r.Context(), targetUserID, deviceID)
	if err != nil {
		http.Error(w, "Key bundle not found", http.StatusNotFound)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(bundle)
}

// Check prekey count
func (c *E2EController) GetPreKeyCount(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	count, needsMore, err := c.e2eService.CheckPreKeyCount(r.Context(), userID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"count":      count,
		"needs_more": needsMore,
	})
}
