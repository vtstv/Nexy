/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"context"
	"encoding/json"
	"log"
	"net/http"
	"strings"

	"github.com/vtstv/nexy/internal/middleware"
	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
	"github.com/vtstv/nexy/internal/services"
)

type AuthController struct {
	authService *services.AuthService
	sessionRepo *repositories.SessionRepository
	folderRepo  *repositories.FolderRepository
}

func NewAuthController(authService *services.AuthService, sessionRepo *repositories.SessionRepository, folderRepo *repositories.FolderRepository) *AuthController {
	return &AuthController{
		authService: authService,
		sessionRepo: sessionRepo,
		folderRepo:  folderRepo,
	}
}

type RegisterRequest struct {
	Username    string `json:"username"`
	Email       string `json:"email"`
	Password    string `json:"password"`
	DisplayName string `json:"display_name"`
}

type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type RefreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type AuthResponse struct {
	AccessToken  string      `json:"access_token"`
	RefreshToken string      `json:"refresh_token"`
	User         interface{} `json:"user"`
}

func (c *AuthController) Register(w http.ResponseWriter, r *http.Request) {
	var req RegisterRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	if req.Username == "" || req.Email == "" || req.Password == "" {
		http.Error(w, "Missing required fields", http.StatusBadRequest)
		return
	}

	user, err := c.authService.Register(r.Context(), req.Username, req.Email, req.Password, req.DisplayName)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	// Create default folders for new user
	c.createDefaultFolders(r.Context(), user.ID)

	accessToken, refreshToken, refreshTokenID, _, err := c.authService.Login(r.Context(), req.Email, req.Password)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// Create session for this registration/login
	userAgent := r.Header.Get("User-Agent")
	deviceID := r.Header.Get("X-Device-ID")
	ipAddress := r.RemoteAddr
	if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
		ipAddress = strings.Split(forwarded, ",")[0]
	}

	if deviceID == "" {
		deviceID = ipAddress
	}

	deviceName, deviceType := parseUserAgent(userAgent)
	c.sessionRepo.CreateFromLogin(r.Context(), user.ID, deviceID, deviceName, deviceType, ipAddress, userAgent, refreshTokenID)

	response := AuthResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         user,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func (c *AuthController) Login(w http.ResponseWriter, r *http.Request) {
	var req LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}

	accessToken, refreshToken, refreshTokenID, user, err := c.authService.Login(r.Context(), req.Email, req.Password)
	if err != nil {
		http.Error(w, err.Error(), http.StatusUnauthorized)
		return
	}

	// Create session for this login
	userAgent := r.Header.Get("User-Agent")
	deviceID := r.Header.Get("X-Device-ID") // Get unique device ID from client
	ipAddress := r.RemoteAddr
	if forwarded := r.Header.Get("X-Forwarded-For"); forwarded != "" {
		ipAddress = strings.Split(forwarded, ",")[0]
	}

	// Fallback to IP if no device ID provided (backward compatibility)
	if deviceID == "" {
		deviceID = ipAddress
	}

	deviceName, deviceType := parseUserAgent(userAgent)
	c.sessionRepo.CreateFromLogin(r.Context(), user.ID, deviceID, deviceName, deviceType, ipAddress, userAgent, refreshTokenID)

	response := AuthResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         user,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func parseUserAgent(userAgent string) (deviceName, deviceType string) {
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

func (c *AuthController) RefreshToken(w http.ResponseWriter, r *http.Request) {
	var req RefreshRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request", http.StatusBadRequest)
		return
	}
	if strings.TrimSpace(req.RefreshToken) == "" {
		http.Error(w, "Missing refresh_token", http.StatusBadRequest)
		return
	}

	accessToken, newRefreshToken, oldRefreshTokenID, newRefreshTokenID, user, err := c.authService.RefreshAuth(r.Context(), req.RefreshToken)
	if err != nil {
		http.Error(w, err.Error(), http.StatusUnauthorized)
		return
	}

	// Update session mapping if we can find the session by old refresh token.
	if oldRefreshTokenID != 0 && newRefreshTokenID != 0 {
		if s, serr := c.sessionRepo.GetByRefreshTokenID(r.Context(), oldRefreshTokenID); serr == nil && s != nil {
			_ = c.sessionRepo.UpdateRefreshTokenID(r.Context(), s.ID, newRefreshTokenID)
		}
		// Best-effort cleanup: remove the old refresh token row.
		_ = c.authService.LogoutTokenByID(r.Context(), oldRefreshTokenID)
	}

	response := AuthResponse{
		AccessToken:  accessToken,
		RefreshToken: newRefreshToken,
		User:         user,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func (c *AuthController) Logout(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	if err := c.authService.Logout(r.Context(), userID); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// createDefaultFolders creates default folders for a new user
func (c *AuthController) createDefaultFolders(ctx context.Context, userID int) {
	defaultFolders := []models.ChatFolder{
		{
			UserID:          userID,
			Name:            "Private",
			Icon:            "👤",
			Color:           "blue",
			Position:        0,
			IncludeContacts: true,
		},
		{
			UserID:        userID,
			Name:          "Groups",
			Icon:          "👥",
			Color:         "green",
			Position:      1,
			IncludeGroups: true,
		},
	}

	for _, folder := range defaultFolders {
		if err := c.folderRepo.Create(ctx, &folder); err != nil {
			log.Printf("Failed to create default folder '%s' for user %d: %v", folder.Name, userID, err)
		}
	}
}
