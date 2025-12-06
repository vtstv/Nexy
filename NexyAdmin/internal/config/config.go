// Copyright (c) 2025 Nexy Project. All rights reserved.
// GitHub: https://github.com/vtstv/Nexy

package config

import (
	"os"
	"time"

	"github.com/joho/godotenv"
)

type Config struct {
	Server   ServerConfig
	Database DatabaseConfig
	Redis    RedisConfig
	JWT      JWTConfig
	Backup   BackupConfig
	Admin    AdminConfig
}

type ServerConfig struct {
	Port string
}

type DatabaseConfig struct {
	URL string
}

type RedisConfig struct {
	URL      string
	Password string
}

type JWTConfig struct {
	Secret     string
	Expiration time.Duration
}

type BackupConfig struct {
	Path string
}

type AdminConfig struct {
	Username string
	Password string
}

func Load() *Config {
	godotenv.Load()

	return &Config{
		Server: ServerConfig{
			Port: getEnv("ADMIN_PORT", "3000"),
		},
		Database: DatabaseConfig{
			URL: getEnv("DATABASE_URL", "postgres://nexy_user:nexy_password@localhost:5432/nexy?sslmode=disable"),
		},
		Redis: RedisConfig{
			URL:      getEnv("REDIS_URL", "localhost:6379"),
			Password: getEnv("REDIS_PASSWORD", ""),
		},
		JWT: JWTConfig{
			Secret:     getEnv("ADMIN_JWT_SECRET", "your-admin-secret-key"),
			Expiration: 24 * time.Hour,
		},
		Backup: BackupConfig{
			Path: getEnv("BACKUP_PATH", "./backups"),
		},
		Admin: AdminConfig{
			Username: getEnv("ADMIN_USERNAME", "admin"),
			Password: getEnv("ADMIN_PASSWORD", "admin123"),
		},
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}
