package nexy

import (
	"encoding/json"
	"time"
)

const (
	ProtocolVersion = "1.0"
)

type MessageType string

const (
	TypeChatMessage  MessageType = "chat_message"
	TypeTyping       MessageType = "typing"
	TypeDelivered    MessageType = "delivered"
	TypeRead         MessageType = "read"
	TypeOnline       MessageType = "online"
	TypeOffline      MessageType = "offline"
	TypeHeartbeat    MessageType = "heartbeat"
	TypeAck          MessageType = "ack"
	TypeError        MessageType = "error"
	TypeCallOffer    MessageType = "call_offer"
	TypeCallAnswer   MessageType = "call_answer"
	TypeICECandidate MessageType = "ice_candidate"
	TypeCallCancel   MessageType = "call_cancel"
	TypeCallEnd      MessageType = "call_end"
	TypeCallBusy     MessageType = "call_busy"
)

type NexyMessage struct {
	Header NexyHeader      `json:"header"`
	Body   json.RawMessage `json:"body"`
}

type NexyHeader struct {
	Version     string      `json:"version"`
	Type        MessageType `json:"type"`
	MessageID   string      `json:"message_id"`
	Timestamp   int64       `json:"timestamp"`
	SenderID    int         `json:"sender_id,omitempty"`
	RecipientID *int        `json:"recipient_id,omitempty"`
	ChatID      *int        `json:"chat_id,omitempty"`
}

type ChatMessageBody struct {
	Content     string      `json:"content,omitempty"`
	MessageType string      `json:"message_type"`
	MediaURL    string      `json:"media_url,omitempty"`
	MediaType   string      `json:"media_type,omitempty"`
	FileSize    *int64      `json:"file_size,omitempty"`
	ReplyToID   *int        `json:"reply_to_id,omitempty"`
	Encryption  *Encryption `json:"encryption,omitempty"`
}

type Encryption struct {
	Algorithm string `json:"algorithm"`
	KeyID     string `json:"key_id,omitempty"`
}

type TypingBody struct {
	IsTyping bool `json:"is_typing"`
}

type DeliveredBody struct {
	MessageID string `json:"message_id"`
}

type ReadBody struct {
	MessageID string `json:"message_id"`
}

type OnlineBody struct {
	UserID int `json:"user_id"`
}

type AckBody struct {
	MessageID string `json:"message_id"`
	Status    string `json:"status"`
}

type ErrorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type CallOfferBody struct {
	CallID string `json:"call_id"`
	SDP    string `json:"sdp"`
	Video  bool   `json:"video"`
	Audio  bool   `json:"audio"`
}

type CallAnswerBody struct {
	CallID string `json:"call_id"`
	SDP    string `json:"sdp"`
}

type ICECandidateBody struct {
	CallID    string `json:"call_id"`
	Candidate string `json:"candidate"`
}

type CallCancelBody struct {
	CallID string `json:"call_id"`
	Reason string `json:"reason,omitempty"`
}

func NewNexyMessage(msgType MessageType, senderID int, chatID *int, body interface{}) (*NexyMessage, error) {
	bodyBytes, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}

	header := NexyHeader{
		Version:   ProtocolVersion,
		Type:      msgType,
		MessageID: generateMessageID(),
		Timestamp: time.Now().Unix(),
		SenderID:  senderID,
		ChatID:    chatID,
	}

	return &NexyMessage{
		Header: header,
		Body:   bodyBytes,
	}, nil
}

func (m *NexyMessage) ParseBody(v interface{}) error {
	return json.Unmarshal(m.Body, v)
}

func generateMessageID() string {
	return time.Now().Format("20060102150405") + randomString(8)
}

func randomString(n int) string {
	const letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	b := make([]byte, n)
	for i := range b {
		b[i] = letters[time.Now().UnixNano()%int64(len(letters))]
	}
	return string(b)
}
