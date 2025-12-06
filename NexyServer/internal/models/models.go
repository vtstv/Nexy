/*
 * © 2025 Murr | https://github.com/vtstv
 */
package models

import "time"

type User struct {
	ID                      int        `json:"id"`
	Username                string     `json:"username"`
	Email                   string     `json:"email"`
	PasswordHash            string     `json:"-"`
	DisplayName             string     `json:"display_name"`
	AvatarURL               string     `json:"avatar_url"`
	Bio                     string     `json:"bio"`
	FcmToken                string     `json:"-"`
	ReadReceiptsEnabled     bool       `json:"read_receipts_enabled"`
	TypingIndicatorsEnabled bool       `json:"typing_indicators_enabled"`
	ShowOnlineStatus        bool       `json:"show_online_status"`
	LastSeen                *time.Time `json:"last_seen,omitempty"`
	OnlineStatus            string     `json:"online_status,omitempty"`
	CreatedAt               time.Time  `json:"created_at"`
	UpdatedAt               time.Time  `json:"updated_at"`
}

type RefreshToken struct {
	ID        int       `json:"id"`
	UserID    int       `json:"user_id"`
	Token     string    `json:"token"`
	ExpiresAt time.Time `json:"expires_at"`
	CreatedAt time.Time `json:"created_at"`
}

type UserSession struct {
	ID                int       `json:"id"`
	UserID            int       `json:"user_id"`
	RefreshTokenID    *int      `json:"refresh_token_id,omitempty"`
	DeviceID          string    `json:"device_id"`
	DeviceName        string    `json:"device_name"`
	DeviceType        string    `json:"device_type"`
	IPAddress         string    `json:"ip_address"`
	UserAgent         string    `json:"user_agent,omitempty"`
	LastActive        time.Time `json:"last_active"`
	CreatedAt         time.Time `json:"created_at"`
	IsCurrent         bool      `json:"is_current"`
	AcceptSecretChats bool      `json:"accept_secret_chats"`
	AcceptCalls       bool      `json:"accept_calls"`
}

type InviteLink struct {
	ID        int        `json:"id"`
	Code      string     `json:"code"`
	CreatorID int        `json:"creator_id"`
	MaxUses   int        `json:"max_uses"`
	UsesCount int        `json:"uses_count"`
	ExpiresAt *time.Time `json:"expires_at,omitempty"`
	CreatedAt time.Time  `json:"created_at"`
}

type Chat struct {
	ID                   int              `json:"id"`
	Type                 string           `json:"type"`
	GroupType            string           `json:"group_type,omitempty"`
	Name                 string           `json:"name,omitempty"`
	Username             string           `json:"username,omitempty"`
	Description          string           `json:"description,omitempty"`
	AvatarURL            string           `json:"avatar_url,omitempty"`
	CreatedBy            *int             `json:"created_by,omitempty"`
	CreatedAt            time.Time        `json:"created_at"`
	UpdatedAt            time.Time        `json:"updated_at"`
	ParticipantIds       []int            `json:"participant_ids,omitempty"`
	DefaultPermissions   *ChatPermissions `json:"default_permissions,omitempty"`
	MemberCount          int              `json:"member_count,omitempty"`
	IsMember             bool             `json:"is_member,omitempty"`
	MutedUntil           *time.Time       `json:"muted_until,omitempty"`
	UnreadCount          int              `json:"unread_count"`
	LastReadMessageId    int              `json:"last_read_message_id"`
	FirstUnreadMessageId string           `json:"first_unread_message_id,omitempty"`
	IsPinned             bool             `json:"is_pinned"`
	PinnedAt             *time.Time       `json:"pinned_at,omitempty"`
}

type ChatPermissions struct {
	SendMessages bool `json:"send_messages"`
	SendMedia    bool `json:"send_media"`
	AddUsers     bool `json:"add_users"`
	PinMessages  bool `json:"pin_messages"`
	ChangeInfo   bool `json:"change_info"`
}

type ChatMember struct {
	ID                int              `json:"id"`
	ChatID            int              `json:"chat_id"`
	UserID            int              `json:"user_id"`
	Role              string           `json:"role"`
	Permissions       *ChatPermissions `json:"permissions,omitempty"`
	JoinedAt          time.Time        `json:"joined_at"`
	User              *User            `json:"user,omitempty"`
	MutedUntil        *time.Time       `json:"muted_until,omitempty"`
	LastReadMessageId int              `json:"last_read_message_id"`
	IsPinned          bool             `json:"is_pinned"`
	PinnedAt          *time.Time       `json:"pinned_at,omitempty"`
}

type ChatInviteLink struct {
	ID         int        `json:"id"`
	ChatID     int        `json:"chat_id"`
	CreatorID  int        `json:"creator_id"`
	Code       string     `json:"code"`
	IsRevoked  bool       `json:"is_revoked"`
	ExpiresAt  *time.Time `json:"expires_at,omitempty"`
	UsageLimit *int       `json:"usage_limit,omitempty"`
	UsageCount int        `json:"usage_count"`
	CreatedAt  time.Time  `json:"created_at"`
}

type Message struct {
	ID          int       `json:"id"`
	MessageID   string    `json:"message_id"`
	ChatID      int       `json:"chat_id"`
	SenderID    int       `json:"sender_id"`
	Sender      *User     `json:"sender,omitempty"`
	MessageType string    `json:"message_type"`
	Content     string    `json:"content,omitempty"`
	MediaURL    string    `json:"media_url,omitempty"`
	MediaType   string    `json:"media_type,omitempty"`
	FileSize    *int64    `json:"file_size,omitempty"`
	Duration    *int      `json:"duration,omitempty"` // Duration in seconds for voice messages
	ReplyToID   *int      `json:"reply_to_id,omitempty"`
	IsEdited    bool      `json:"is_edited"`
	IsDeleted   bool      `json:"is_deleted"`
	Status      string    `json:"status,omitempty"`
	Pts         int       `json:"pts,omitempty"` // sequence number for sync
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

type MessageStatus struct {
	ID        int       `json:"id"`
	MessageID int       `json:"message_id"`
	UserID    int       `json:"user_id"`
	Status    string    `json:"status"`
	Timestamp time.Time `json:"timestamp"`
}

type File struct {
	ID               int       `json:"id"`
	FileID           string    `json:"file_id"`
	UserID           int       `json:"user_id"`
	Filename         string    `json:"filename"`
	OriginalFilename string    `json:"original_filename"`
	MimeType         string    `json:"mime_type"`
	FileSize         int64     `json:"file_size"`
	StorageType      string    `json:"storage_type"`
	StoragePath      string    `json:"storage_path"`
	URL              string    `json:"url"`
	CreatedAt        time.Time `json:"created_at"`
}

type Contact struct {
	ID            int       `json:"id"`
	UserID        int       `json:"user_id"`
	ContactUserID int       `json:"contact_user_id"`
	Status        string    `json:"status"`
	CreatedAt     time.Time `json:"created_at"`
	UpdatedAt     time.Time `json:"updated_at"`
}

type ContactWithUser struct {
	Contact
	ContactUser User `json:"contact_user"`
}

// Chat Folders
type ChatFolder struct {
	ID                 int       `json:"id"`
	UserID             int       `json:"user_id"`
	Name               string    `json:"name"`
	Icon               string    `json:"icon"`
	Color              string    `json:"color"`
	Position           int       `json:"position"`
	IncludeContacts    bool      `json:"include_contacts"`
	IncludeNonContacts bool      `json:"include_non_contacts"`
	IncludeGroups      bool      `json:"include_groups"`
	IncludeChannels    bool      `json:"include_channels"`
	IncludeBots        bool      `json:"include_bots"`
	CreatedAt          time.Time `json:"created_at"`
	UpdatedAt          time.Time `json:"updated_at"`
}

type ChatFolderWithChats struct {
	ChatFolder
	IncludedChats []int `json:"included_chats"`
	ExcludedChats []int `json:"excluded_chats,omitempty"`
}

// synchronization state
type SyncState struct {
	Pts  int       `json:"pts"`
	Date time.Time `json:"date"`
}

type ChannelSyncState struct {
	ChatID int `json:"chat_id"`
	Pts    int `json:"pts"`
}

// Update types for getDifference
type Update struct {
	Type     string      `json:"type"`      // "new_message", "edit_message", "delete_message", "read"
	Pts      int         `json:"pts"`       // Sequence number
	PtsCount int         `json:"pts_count"` // Number of events in this update
	ChatID   int         `json:"chat_id"`
	Data     interface{} `json:"data"` // Actual update data (Message, etc)
}

// Response for getDifference API
type UpdatesDifference struct {
	NewMessages     []*Message `json:"new_messages"`
	EditedMessages  []*Message `json:"edited_messages,omitempty"`
	DeletedMessages []string   `json:"deleted_messages,omitempty"` // message_ids
	State           SyncState  `json:"state"`
}

// Response when difference is too large
type UpdatesDifferenceSlice struct {
	NewMessages       []*Message `json:"new_messages"`
	IntermediateState SyncState  `json:"intermediate_state"`
}

// Response for channel getDifference
type ChannelDifference struct {
	Final           bool       `json:"final"`
	NewMessages     []*Message `json:"new_messages"`
	EditedMessages  []*Message `json:"edited_messages,omitempty"`
	DeletedMessages []string   `json:"deleted_messages,omitempty"`
	Pts             int        `json:"pts"`
}
