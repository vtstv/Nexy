package models

import "time"

type User struct {
	ID                      int        `json:"id"`
	Username                string     `json:"username"`
	Email                   string     `json:"email"`
	DisplayName             string     `json:"display_name"`
	AvatarURL               string     `json:"avatar_url"`
	Bio                     string     `json:"bio"`
	ReadReceiptsEnabled     bool       `json:"read_receipts_enabled"`
	TypingIndicatorsEnabled bool       `json:"typing_indicators_enabled"`
	VoiceMessagesEnabled    bool       `json:"voice_messages_enabled"`
	ShowOnlineStatus        bool       `json:"show_online_status"`
	LastSeen                *time.Time `json:"last_seen,omitempty"`
	IsBanned                bool       `json:"is_banned"`
	BannedAt                *time.Time `json:"banned_at,omitempty"`
	BannedReason            *string    `json:"banned_reason,omitempty"`
	BannedBy                *int       `json:"banned_by,omitempty"`
	CreatedAt               time.Time  `json:"created_at"`
	UpdatedAt               time.Time  `json:"updated_at"`
}

type Chat struct {
	ID          int       `json:"id"`
	Type        string    `json:"type"`
	GroupType   *string   `json:"group_type,omitempty"`
	Name        *string   `json:"name,omitempty"`
	Username    *string   `json:"username,omitempty"`
	Description *string   `json:"description,omitempty"`
	AvatarURL   *string   `json:"avatar_url,omitempty"`
	CreatedBy   *int      `json:"created_by,omitempty"`
	MemberCount int       `json:"member_count"`
	MemberNames *string   `json:"member_names,omitempty"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

type ChatMember struct {
	ID                int       `json:"id"`
	ChatID            int       `json:"chat_id"`
	UserID            int       `json:"user_id"`
	Role              string    `json:"role"`
	Username          string    `json:"username"`
	DisplayName       string    `json:"display_name"`
	AvatarURL         string    `json:"avatar_url"`
	JoinedAt          time.Time `json:"joined_at"`
	LastReadMessageId int       `json:"last_read_message_id"`
}

type Message struct {
	ID          int       `json:"id"`
	MessageID   string    `json:"message_id"`
	ChatID      int       `json:"chat_id"`
	SenderID    int       `json:"sender_id"`
	SenderName  string    `json:"sender_name"`
	ChatName    string    `json:"chat_name"`
	MessageType string    `json:"message_type"`
	Content     string    `json:"content,omitempty"`
	MediaURL    string    `json:"media_url,omitempty"`
	MediaType   string    `json:"media_type,omitempty"`
	IsEdited    bool      `json:"is_edited"`
	IsDeleted   bool      `json:"is_deleted"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

type UserSession struct {
	ID         int       `json:"id"`
	UserID     int       `json:"user_id"`
	DeviceID   string    `json:"device_id"`
	DeviceName string    `json:"device_name"`
	DeviceType string    `json:"device_type"`
	IPAddress  string    `json:"ip_address"`
	LastActive time.Time `json:"last_active"`
	CreatedAt  time.Time `json:"created_at"`
	IsCurrent  bool      `json:"is_current"`
}

type Stats struct {
	TotalUsers      int              `json:"total_users"`
	ActiveUsers     int              `json:"active_users"`
	TotalChats      int              `json:"total_chats"`
	TotalMessages   int              `json:"total_messages"`
	MessagesToday   int              `json:"messages_today"`
	NewUsersToday   int              `json:"new_users_today"`
	OnlineUsers     int              `json:"online_users"`
	StorageUsed     int64            `json:"storage_used"`
	UserGrowth      []TimeSeriesData `json:"user_growth"`
	MessageActivity []TimeSeriesData `json:"message_activity"`
}

type TimeSeriesData struct {
	Date  string `json:"date"`
	Value int    `json:"value"`
}

type BackupInfo struct {
	Filename  string    `json:"filename"`
	Size      int64     `json:"size"`
	CreatedAt time.Time `json:"created_at"`
}

type DiagnosticInfo struct {
	Status    string              `json:"status"`
	Timestamp time.Time           `json:"timestamp"`
	Database  DatabaseDiagnostics `json:"database,omitempty"`
	Redis     RedisDiagnostics    `json:"redis,omitempty"`
	System    SystemDiagnostics   `json:"system,omitempty"`
}

type DatabaseDiagnostics struct {
	Connected       bool             `json:"connected"`
	OpenConnections int              `json:"open_connections"`
	MaxConnections  int              `json:"max_connections"`
	TableSizes      map[string]int64 `json:"table_sizes"`
	SlowQueries     []SlowQuery      `json:"slow_queries,omitempty"`
}

type RedisDiagnostics struct {
	Connected        bool   `json:"connected"`
	UsedMemory       string `json:"used_memory"`
	ConnectedClients int    `json:"connected_clients"`
	KeyCount         int64  `json:"key_count"`
}

type SystemDiagnostics struct {
	Uptime       string `json:"uptime"`
	GoVersion    string `json:"go_version"`
	NumGoroutine int    `json:"num_goroutine"`
	MemAllocMB   uint64 `json:"mem_alloc_mb"`
	MemTotalMB   uint64 `json:"mem_total_mb"`
}

type SlowQuery struct {
	Query    string  `json:"query"`
	Duration float64 `json:"duration_ms"`
}

type AdminCredentials struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type TokenResponse struct {
	Token     string    `json:"token"`
	ExpiresAt time.Time `json:"expires_at"`
}

type PaginationParams struct {
	Page     int
	PageSize int
	Search   string
	SortBy   string
	SortDir  string
}

type PaginatedResponse struct {
	Data       interface{} `json:"data"`
	Page       int         `json:"page"`
	PageSize   int         `json:"page_size"`
	TotalCount int         `json:"total_count"`
	TotalPages int         `json:"total_pages"`
}

type MessageFilterParams struct {
	Page        int
	PageSize    int
	Search      string
	StartDate   string
	EndDate     string
	SenderID    int
	MessageType string
}

type BanRequest struct {
	Reason string `json:"reason"`
}
