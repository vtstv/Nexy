package controllers

import (
	"encoding/json"
	"log"
	"net/http"
	"strconv"
	"strings"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy/internal/middleware"
	"github.com/vtstv/nexy/internal/repositories"
)

type SessionNotifier interface {
	NotifySessionTerminated(userID int, sessionID int, deviceID string, reason string)
}

type SessionController struct {
	sessionRepo      *repositories.SessionRepository
	refreshTokenRepo *repositories.RefreshTokenRepository
	notifier         SessionNotifier
}

func NewSessionController(sessionRepo *repositories.SessionRepository, refreshTokenRepo *repositories.RefreshTokenRepository) *SessionController {
	return &SessionController{
		sessionRepo:      sessionRepo,
		refreshTokenRepo: refreshTokenRepo,
	}
}

func (c *SessionController) SetNotifier(notifier SessionNotifier) {
	c.notifier = notifier
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

	// Get current device ID from request header
	currentDeviceID := r.Header.Get("X-Device-ID")
	ipAddress := r.RemoteAddr
	if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
		ipAddress = strings.Split(forwarded, ",")[0]
	}

	// Fallback to IP if no device ID provided
	if currentDeviceID == "" {
		currentDeviceID = ipAddress
	}

	log.Printf("GetSessions: user_id=%d, currentDeviceID=%s, sessionCount=%d", userID, currentDeviceID, len(sessions))

	// Check if session exists for the current device
	currentDeviceSessionExists := false
	for _, session := range sessions {
		if session.DeviceID == currentDeviceID {
			currentDeviceSessionExists = true
			break
		}
	}

	// If no session exists for current device, create one (for authenticated users without session)
	if !currentDeviceSessionExists {
		userAgent := r.Header.Get("User-Agent")
		deviceName, deviceType := parseSessionUserAgent(userAgent)
		c.sessionRepo.CreateFromLogin(r.Context(), userID, currentDeviceID, deviceName, deviceType, ipAddress, userAgent, 0)

		// Fetch the updated sessions
		sessions, err = c.sessionRepo.GetByUserID(r.Context(), userID)
		if err != nil {
			http.Error(w, "Failed to get sessions", http.StatusInternalServerError)
			return
		}
	}

	// Mark the session matching current device ID as "is_current"
	for i := range sessions {
		sessions[i].IsCurrent = sessions[i].DeviceID == currentDeviceID
		log.Printf("  Session %d: DeviceID=%s, currentDeviceID=%s, isCurrent=%v", sessions[i].ID, sessions[i].DeviceID, currentDeviceID, sessions[i].IsCurrent)
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
	var targetDeviceID string
	for _, s := range sessions {
		if s.ID == sessionID {
			sessionBelongsToUser = true
			refreshTokenID = s.RefreshTokenID
			targetDeviceID = s.DeviceID
			break
		}
	}

	if !sessionBelongsToUser {
		http.Error(w, "Session not found", http.StatusNotFound)
		return
	}

	// Notify the session before deleting
	if c.notifier != nil {
		c.notifier.NotifySessionTerminated(userID, sessionID, targetDeviceID, "session_terminated_by_user")
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

	// Get current device ID
	currentDeviceID := r.Header.Get("X-Device-ID")
	ipAddress := r.RemoteAddr
	if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
		ipAddress = strings.Split(forwarded, ",")[0]
	}
	if currentDeviceID == "" {
		currentDeviceID = ipAddress
	}

	sessions, err := c.sessionRepo.GetByUserID(r.Context(), userID)
	if err != nil {
		http.Error(w, "Failed to get sessions", http.StatusInternalServerError)
		return
	}

	var currentSessionID int
	for _, s := range sessions {
		isCurrent := s.DeviceID == currentDeviceID
		if isCurrent {
			currentSessionID = s.ID
		} else {
			// Notify each session before deleting
			if c.notifier != nil {
				c.notifier.NotifySessionTerminated(userID, s.ID, s.DeviceID, "all_sessions_terminated")
			}
			if s.RefreshTokenID != nil {
				c.refreshTokenRepo.DeleteByID(r.Context(), *s.RefreshTokenID)
			}
		}
	}

	if err := c.sessionRepo.DeleteAllExceptCurrent(r.Context(), userID, currentSessionID); err != nil {
		http.Error(w, "Failed to delete sessions", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

type UpdateSessionSettingsRequest struct {
	AcceptSecretChats *bool `json:"accept_secret_chats"`
	AcceptCalls       *bool `json:"accept_calls"`
}

func (c *SessionController) UpdateSessionSettings(w http.ResponseWriter, r *http.Request) {
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

	// Verify session belongs to user
	session, err := c.sessionRepo.GetByID(r.Context(), sessionID)
	if err != nil {
		http.Error(w, "Failed to get session", http.StatusInternalServerError)
		return
	}
	if session == nil || session.UserID != userID {
		http.Error(w, "Session not found", http.StatusNotFound)
		return
	}

	var req UpdateSessionSettingsRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	acceptSecretChats := session.AcceptSecretChats
	acceptCalls := session.AcceptCalls

	if req.AcceptSecretChats != nil {
		acceptSecretChats = *req.AcceptSecretChats
	}
	if req.AcceptCalls != nil {
		acceptCalls = *req.AcceptCalls
	}

	if err := c.sessionRepo.UpdateSettings(r.Context(), sessionID, acceptSecretChats, acceptCalls); err != nil {
		http.Error(w, "Failed to update session settings", http.StatusInternalServerError)
		return
	}

	// Return updated session
	session.AcceptSecretChats = acceptSecretChats
	session.AcceptCalls = acceptCalls

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(session)
}
