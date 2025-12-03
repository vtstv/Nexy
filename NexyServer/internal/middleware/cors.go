package middleware

import (
	"net/http"
	"strings"

	"github.com/vtstv/nexy/internal/config"
)

type CORSMiddleware struct {
	config *config.CORSConfig
}

func NewCORSMiddleware(config *config.CORSConfig) *CORSMiddleware {
	return &CORSMiddleware{config: config}
}

func (m *CORSMiddleware) Handle(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		origin := r.Header.Get("Origin")

		if m.isAllowedOrigin(origin) {
			w.Header().Set("Access-Control-Allow-Origin", origin)
		}

		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		w.Header().Set("Access-Control-Allow-Credentials", "true")
		w.Header().Set("Access-Control-Max-Age", "3600")

		if r.Method == "OPTIONS" {
			w.WriteHeader(http.StatusNoContent)
			return
		}

		next.ServeHTTP(w, r)
	})
}

func (m *CORSMiddleware) isAllowedOrigin(origin string) bool {
	for _, allowed := range m.config.AllowedOrigins {
		trimmedAllowed := strings.TrimSpace(allowed)
		// Allow wildcard
		if trimmedAllowed == "*" {
			return true
		}
		// Exact match
		if trimmedAllowed == origin {
			return true
		}
	}
	return false
}
