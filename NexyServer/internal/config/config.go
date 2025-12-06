package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/joho/godotenv"
)

type Config struct {
	Server    ServerConfig
	Database  DatabaseConfig
	Redis     RedisConfig
	JWT       JWTConfig
	Upload    UploadConfig
	S3        S3Config
	CORS      CORSConfig
	RateLimit RateLimitConfig
	TURN      TURNConfig
	FCM       FCMConfig
}

type ServerConfig struct {
	Port string
}

type DatabaseConfig struct {
	Host     string
	Port     string
	User     string
	Password string
	Name     string
	SSLMode  string
}

type RedisConfig struct {
	Host     string
	Port     string
	Password string
	DB       int
}

type JWTConfig struct {
	Secret            string
	Expiration        time.Duration
	RefreshExpiration time.Duration
}

type UploadConfig struct {
	Path             string
	MaxSize          int64
	AllowedMimeTypes []string
}

type S3Config struct {
	UseS3     bool
	Endpoint  string
	Region    string
	Bucket    string
	AccessKey string
	SecretKey string
}

type CORSConfig struct {
	AllowedOrigins []string
}

type RateLimitConfig struct {
	Requests int
	Window   int
}

type TURNConfig struct {
	ServerURL     string
	Username      string
	Password      string
	STUNServerURL string
}

type FCMConfig struct {
	Enabled               bool
	ServiceAccountKeyPath string
}

func Load() (*Config, error) {
	godotenv.Load()

	jwtExp, err := time.ParseDuration(getEnv("JWT_EXPIRATION", "24h"))
	if err != nil {
		jwtExp = 24 * time.Hour
	}

	jwtRefreshExp, err := time.ParseDuration(getEnv("JWT_REFRESH_EXPIRATION", "168h"))
	if err != nil {
		jwtRefreshExp = 168 * time.Hour
	}

	maxUploadSize, err := strconv.ParseInt(getEnv("MAX_UPLOAD_SIZE", "10485760"), 10, 64)
	if err != nil {
		maxUploadSize = 10485760
	}

	redisDB, err := strconv.Atoi(getEnv("REDIS_DB", "0"))
	if err != nil {
		redisDB = 0
	}

	useS3, _ := strconv.ParseBool(getEnv("USE_S3", "false"))

	rateLimitRequests, err := strconv.Atoi(getEnv("RATE_LIMIT_REQUESTS", "100"))
	if err != nil {
		rateLimitRequests = 100
	}

	rateLimitWindow, err := strconv.Atoi(getEnv("RATE_LIMIT_WINDOW", "60"))
	if err != nil {
		rateLimitWindow = 60
	}

	allowedMimeTypes := strings.Split(getEnv("ALLOWED_MIME_TYPES", "image/jpeg,image/png,image/gif,image/webp,video/mp4,audio/mpeg,application/pdf"), ",")
	allowedOrigins := strings.Split(getEnv("CORS_ALLOWED_ORIGINS", "http://localhost:3000"), ",")

	return &Config{
		Server: ServerConfig{
			Port: getEnv("SERVER_PORT", "8080"),
		},
		Database: DatabaseConfig{
			Host:     getEnv("DB_HOST", "localhost"),
			Port:     getEnv("DB_PORT", "5432"),
			User:     getEnv("DB_USER", "messenger"),
			Password: getEnv("DB_PASSWORD", "messenger_password"),
			Name:     getEnv("DB_NAME", "messenger_db"),
			SSLMode:  getEnv("DB_SSLMODE", "disable"),
		},
		Redis: RedisConfig{
			Host:     getEnv("REDIS_HOST", "localhost"),
			Port:     getEnv("REDIS_PORT", "6379"),
			Password: getEnv("REDIS_PASSWORD", ""),
			DB:       redisDB,
		},
		JWT: JWTConfig{
			Secret:            getEnv("JWT_SECRET", "your-secret-key"),
			Expiration:        jwtExp,
			RefreshExpiration: jwtRefreshExp,
		},
		Upload: UploadConfig{
			Path:             getEnv("UPLOAD_PATH", "./uploads"),
			MaxSize:          maxUploadSize,
			AllowedMimeTypes: allowedMimeTypes,
		},
		S3: S3Config{
			UseS3:     useS3,
			Endpoint:  getEnv("S3_ENDPOINT", ""),
			Region:    getEnv("S3_REGION", "us-east-1"),
			Bucket:    getEnv("S3_BUCKET", ""),
			AccessKey: getEnv("S3_ACCESS_KEY", ""),
			SecretKey: getEnv("S3_SECRET_KEY", ""),
		},
		CORS: CORSConfig{
			AllowedOrigins: allowedOrigins,
		},
		RateLimit: RateLimitConfig{
			Requests: rateLimitRequests,
			Window:   rateLimitWindow,
		},
		TURN: TURNConfig{
			ServerURL:     getEnv("TURN_SERVER_URL", "turn:localhost:3478"),
			Username:      getEnv("TURN_USERNAME", "nexy"),
			Password:      getEnv("TURN_PASSWORD", "nexy_turn_password"),
			STUNServerURL: getEnv("STUN_SERVER_URL", "stun:localhost:3478"),
		},
		FCM: FCMConfig{
			Enabled:               getEnv("FCM_ENABLED", "false") == "true",
			ServiceAccountKeyPath: getEnv("FCM_SERVICE_ACCOUNT_KEY", "./firebase-service-account.json"),
		},
	}, nil
}

func (c *DatabaseConfig) ConnectionString() string {
	return fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=%s",
		c.Host, c.Port, c.User, c.Password, c.Name, c.SSLMode)
}

func (c *RedisConfig) Address() string {
	return fmt.Sprintf("%s:%s", c.Host, c.Port)
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}
