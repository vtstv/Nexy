package nexy

import (
	"context"
	"log"
	"net/http"

	"github.com/gorilla/websocket"
	"github.com/vtstv/nexy/internal/repositories"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		return true
	},
}

type WSHandler struct {
	hub *Hub
}

func NewWSHandler(hub *Hub) *WSHandler {
	return &WSHandler{hub: hub}
}

func (h *WSHandler) ServeWS(w http.ResponseWriter, r *http.Request, userID int) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("WebSocket upgrade error: %v", err)
		return
	}

	client := &Client{
		hub:    h.hub,
		conn:   conn,
		send:   make(chan []byte, 256),
		userID: userID,
	}

	client.hub.register <- client

	go client.writePump()
	go client.readPump()
}

type NexyChatRepo struct {
	repo *repositories.ChatRepository
}

func NewNexyChatRepo(repo *repositories.ChatRepository) *NexyChatRepo {
	return &NexyChatRepo{repo: repo}
}

func (r *NexyChatRepo) IsMember(ctx context.Context, chatID, userID int) (bool, error) {
	return r.repo.IsMember(ctx, chatID, userID)
}
