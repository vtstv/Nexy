package middleware

import (
	"context"
	"fmt"
	"net/http"
	"strings"

	"github.com/redis/go-redis/v9"
	"github.com/vtstv/nexy/internal/services"
)

type contextKey string

const UserIDKey contextKey = "user_id"

type AuthMiddleware struct {
	authService *services.AuthService
	redisClient *redis.Client
}

func NewAuthMiddleware(authService *services.AuthService, redisClient *redis.Client) *AuthMiddleware {
	return &AuthMiddleware{
		authService: authService,
		redisClient: redisClient,
	}
}

func (m *AuthMiddleware) Authenticate(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		if authHeader == "" {
			http.Error(w, "Missing authorization header", http.StatusUnauthorized)
			return
		}

		parts := strings.Split(authHeader, " ")
		if len(parts) != 2 || parts[0] != "Bearer" {
			http.Error(w, "Invalid authorization header", http.StatusUnauthorized)
			return
		}

		userID, err := m.authService.ValidateToken(parts[1])
		if err != nil {
			http.Error(w, "Invalid token", http.StatusUnauthorized)
			return
		}

		// Check if user is banned
		if m.redisClient != nil {
			banKey := fmt.Sprintf("banned:user:%d", userID)
			result, err := m.redisClient.Get(r.Context(), banKey).Result()
			if err == nil && result == "1" {
				http.Error(w, "Account has been banned", http.StatusForbidden)
				return
			}
		}

		ctx := context.WithValue(r.Context(), UserIDKey, userID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func GetUserID(r *http.Request) (int, bool) {
	userID, ok := r.Context().Value(UserIDKey).(int)
	return userID, ok
}
