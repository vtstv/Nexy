package services

import (
	"context"
	"database/sql"
	"runtime"
	"time"

	"github.com/redis/go-redis/v9"
	"github.com/vtstv/nexy-admin/internal/models"
)

type DiagnosticService struct {
	db          *sql.DB
	redisClient *redis.Client
	startTime   time.Time
}

func NewDiagnosticService(db *sql.DB, redisClient *redis.Client) *DiagnosticService {
	return &DiagnosticService{
		db:          db,
		redisClient: redisClient,
		startTime:   time.Now(),
	}
}

func (s *DiagnosticService) HealthCheck(ctx context.Context) (*models.DiagnosticInfo, error) {
	info := &models.DiagnosticInfo{
		Status:    "healthy",
		Timestamp: time.Now(),
	}

	if err := s.db.PingContext(ctx); err != nil {
		info.Status = "unhealthy"
		return info, nil
	}

	if err := s.redisClient.Ping(ctx).Err(); err != nil {
		info.Status = "unhealthy"
		return info, nil
	}

	return info, nil
}

func (s *DiagnosticService) GetDatabaseDiagnostics(ctx context.Context) (*models.DatabaseDiagnostics, error) {
	diag := &models.DatabaseDiagnostics{
		Connected:  false,
		TableSizes: make(map[string]int64),
	}

	if err := s.db.PingContext(ctx); err != nil {
		return diag, nil
	}
	diag.Connected = true

	stats := s.db.Stats()
	diag.OpenConnections = stats.OpenConnections
	diag.MaxConnections = stats.MaxOpenConnections

	tables := []string{"users", "chats", "messages", "chat_members"}
	for _, table := range tables {
		var count int64
		query := "SELECT COUNT(*) FROM " + table
		err := s.db.QueryRowContext(ctx, query).Scan(&count)
		if err == nil {
			diag.TableSizes[table] = count
		}
	}

	return diag, nil
}

func (s *DiagnosticService) GetRedisDiagnostics(ctx context.Context) (*models.RedisDiagnostics, error) {
	diag := &models.RedisDiagnostics{
		Connected: false,
	}

	if err := s.redisClient.Ping(ctx).Err(); err != nil {
		return diag, nil
	}
	diag.Connected = true

	info, err := s.redisClient.Info(ctx, "memory").Result()
	if err == nil {
		diag.UsedMemory = info
	}

	clients, err := s.redisClient.ClientList(ctx).Result()
	if err == nil {
		diag.ConnectedClients = len(clients)
	}

	dbSize, err := s.redisClient.DBSize(ctx).Result()
	if err == nil {
		diag.KeyCount = dbSize
	}

	return diag, nil
}

func (s *DiagnosticService) GetSystemInfo(ctx context.Context) (*models.SystemDiagnostics, error) {
	var m runtime.MemStats
	runtime.ReadMemStats(&m)

	return &models.SystemDiagnostics{
		Uptime:       time.Since(s.startTime).String(),
		GoVersion:    runtime.Version(),
		NumGoroutine: runtime.NumGoroutine(),
		MemAllocMB:   m.Alloc / 1024 / 1024,
		MemTotalMB:   m.TotalAlloc / 1024 / 1024,
	}, nil
}
