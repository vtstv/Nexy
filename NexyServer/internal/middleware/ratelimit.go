package middleware

import (
	"log"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/vtstv/nexy/internal/config"
)

type visitor struct {
	lastSeen       time.Time
	count          int
	burstCount     int
	lastBurstReset time.Time
	errorCount     int
	lastError      time.Time
}

type RateLimiter struct {
	visitors map[string]*visitor
	mu       sync.RWMutex
	config   *config.RateLimitConfig
}

func NewRateLimiter(config *config.RateLimitConfig) *RateLimiter {
	rl := &RateLimiter{
		visitors: make(map[string]*visitor),
		config:   config,
	}

	go rl.cleanupVisitors()

	return rl
}

func (rl *RateLimiter) Limit(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ip := r.RemoteAddr
		path := r.URL.Path
		key := ip + ":" + path

		rl.mu.Lock()
		v, exists := rl.visitors[key]

		now := time.Now()

		if !exists {
			rl.visitors[key] = &visitor{
				lastSeen:       now,
				count:          1,
				burstCount:     1,
				lastBurstReset: now,
			}
			rl.mu.Unlock()
			next.ServeHTTP(w, r)
			return
		}

		// Reset burst counter every second
		if now.Sub(v.lastBurstReset) > time.Second {
			v.burstCount = 0
			v.lastBurstReset = now
		}

		// Burst protection: max 10 requests per second for same endpoint
		v.burstCount++
		if v.burstCount > 10 {
			rl.mu.Unlock()
			log.Printf("Rate limit (burst): %s exceeded burst limit for %s", ip, path)
			http.Error(w, "Too many requests, please slow down", http.StatusTooManyRequests)
			return
		}

		// Special protection for error-prone endpoints (404s, etc)
		if strings.Contains(path, "/info") || strings.Contains(path, "/messages/") {
			// If this is a repeated error pattern (multiple requests to non-existent resource)
			if now.Sub(v.lastError) < 5*time.Second {
				v.errorCount++
				if v.errorCount > 5 {
					rl.mu.Unlock()
					log.Printf("Rate limit (error loop): %s blocked due to repeated errors on %s", ip, path)
					http.Error(w, "Too many failed requests detected. Please check your client.", http.StatusTooManyRequests)
					return
				}
			} else {
				v.errorCount = 0
			}
		}

		// Standard rate limiting (window-based)
		if now.Sub(v.lastSeen) > time.Duration(rl.config.Window)*time.Second {
			v.lastSeen = now
			v.count = 1
			rl.mu.Unlock()
			next.ServeHTTP(w, r)
			return
		}

		if v.count >= rl.config.Requests {
			rl.mu.Unlock()
			log.Printf("Rate limit (standard): %s exceeded limit for %s", ip, path)
			http.Error(w, "Rate limit exceeded", http.StatusTooManyRequests)
			return
		}

		v.count++
		v.lastSeen = now
		rl.mu.Unlock()

		// Wrap response writer to detect errors
		wrw := &responseWriterWrapper{ResponseWriter: w, statusCode: 200}
		next.ServeHTTP(wrw, r)

		// Track errors for error loop detection
		if wrw.statusCode >= 400 {
			rl.mu.Lock()
			if v, exists := rl.visitors[key]; exists {
				v.lastError = now
			}
			rl.mu.Unlock()
		}
	})
}

// responseWriterWrapper captures the status code
type responseWriterWrapper struct {
	http.ResponseWriter
	statusCode int
}

func (w *responseWriterWrapper) WriteHeader(statusCode int) {
	w.statusCode = statusCode
	w.ResponseWriter.WriteHeader(statusCode)
}

func (rl *RateLimiter) cleanupVisitors() {
	for {
		time.Sleep(time.Minute)

		rl.mu.Lock()
		now := time.Now()
		for key, v := range rl.visitors {
			// Clean up visitors that haven't been seen in 2x the window time
			if now.Sub(v.lastSeen) > time.Duration(rl.config.Window*2)*time.Second {
				delete(rl.visitors, key)
			}
		}
		rl.mu.Unlock()
	}
}
