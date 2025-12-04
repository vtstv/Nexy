package controllers

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy/internal/middleware"
	"github.com/vtstv/nexy/internal/repositories"
)

type SessionController struct {
	sessionRepo      *repositories.SessionRepository
	refreshTokenRepo *repositories.RefreshTokenRepository
}

func NewSessionController(sessionRepo *repositories.SessionRepository, refreshTokenRepo *repositories.RefreshTokenRepository) *SessionController {
	return &SessionController{
		sessionRepo:      sessionRepo,
		refreshTokenRepo: refreshTokenRepo,
	}
}

func (c *SessionController) GetSessions(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	sessions, err := c.sessionRepo.GetByUserID(r.Context(), userID)
	if err != nil {
		http.Error(w, "Failed to get sessions", http.StatusInternalServerError)
		return
	}

	// If no sessions exist, create one for the current device (for users who logged in before sessions were added)
	if len(sessions) == 0 {
		userAgent := r.Header.Get("User-Agent")
		ipAddress := r.RemoteAddr
		if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
			ipAddress = strings.Split(forwarded, ",")[0]
		}

		deviceName, deviceType := parseSessionUserAgent(userAgent)
		c.sessionRepo.CreateFromLogin(r.Context(), userID, deviceName, deviceType, ipAddress, userAgent)

		// Fetch the newly created session
		sessions, err = c.sessionRepo.GetByUserID(r.Context(), userID)
		if err != nil {
			http.Error(w, "Failed to get sessions", http.StatusInternalServerError)
			return
		}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(sessions)
}

func parseSessionUserAgent(userAgent string) (deviceName, deviceType string) {
	ua := strings.ToLower(userAgent)

	if strings.Contains(ua, "android") {
		deviceType = "Android"
		deviceName = "Android Device"
	} else if strings.Contains(ua, "okhttp") {
		// OkHttp is typically used by Android apps
		deviceType = "Android"
		deviceName = "Android Device"
	} else if strings.Contains(ua, "iphone") {
		deviceType = "iOS"
		deviceName = "iPhone"
	} else if strings.Contains(ua, "ipad") {
		deviceType = "iOS"
		deviceName = "iPad"
	} else if strings.Contains(ua, "windows") {
		deviceType = "Desktop"
		deviceName = "Windows PC"
	} else if strings.Contains(ua, "macintosh") || strings.Contains(ua, "mac os") {
		deviceType = "Desktop"
		deviceName = "Mac"
	} else if strings.Contains(ua, "linux") {
		deviceType = "Desktop"
		deviceName = "Linux PC"
	} else {
		deviceType = "Unknown"
		deviceName = "Unknown Device"
	}

	return
}

func (c *SessionController) DeleteSession(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	sessionIDStr := vars["id"]
	sessionID, err := strconv.Atoi(sessionIDStr)
	if err != nil {
		http.Error(w, "Invalid session ID", http.StatusBadRequest)
		return
	}

	sessions, err := c.sessionRepo.GetByUserID(r.Context(), userID)
	if err != nil {
		http.Error(w, "Failed to verify session ownership", http.StatusInternalServerError)
		return
	}

	var sessionBelongsToUser bool
	var refreshTokenID *int
	for _, s := range sessions {
		if s.ID == sessionID {
			sessionBelongsToUser = true
			refreshTokenID = s.RefreshTokenID
			break
		}
	}

	if !sessionBelongsToUser {
		http.Error(w, "Session not found", http.StatusNotFound)
		return
	}

	if refreshTokenID != nil {
		if err := c.refreshTokenRepo.DeleteByID(r.Context(), *refreshTokenID); err != nil {
			http.Error(w, "Failed to revoke session token", http.StatusInternalServerError)
			return
		}
	}

	if err := c.sessionRepo.Delete(r.Context(), sessionID); err != nil {
		http.Error(w, "Failed to delete session", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

func (c *SessionController) DeleteAllOtherSessions(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	sessions, err := c.sessionRepo.GetByUserID(r.Context(), userID)
	if err != nil {
		http.Error(w, "Failed to get sessions", http.StatusInternalServerError)
		return
	}

	var currentSessionID int
	for _, s := range sessions {
		if s.IsCurrent {
			currentSessionID = s.ID
		} else if s.RefreshTokenID != nil {
			c.refreshTokenRepo.DeleteByID(r.Context(), *s.RefreshTokenID)
		}
	}

	if err := c.sessionRepo.DeleteAllExceptCurrent(r.Context(), userID, currentSessionID); err != nil {
		http.Error(w, "Failed to delete sessions", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}
