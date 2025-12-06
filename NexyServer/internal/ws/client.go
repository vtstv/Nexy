package nexy

import (
	"encoding/json"
	"log"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

type Client struct {
	hub      *Hub
	conn     *websocket.Conn
	send     chan []byte
	userID   int
	deviceID string
	mu       sync.Mutex
	isClosed bool
}

func newClient(hub *Hub, conn *websocket.Conn, userID int, deviceID string) *Client {
	return &Client{
		hub:      hub,
		conn:     conn,
		send:     make(chan []byte, 256),
		userID:   userID,
		deviceID: deviceID,
	}
}

func (c *Client) readPump() {
	defer func() {
		c.hub.unregister <- c
	}()

	c.conn.SetReadDeadline(time.Now().Add(pongWait))
	c.conn.SetPongHandler(func(string) error {
		c.conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	c.conn.SetReadLimit(maxMessageSize)

	for {
		_, data, err := c.conn.ReadMessage()
		if err != nil {
			break
		}

		log.Printf("Received raw WebSocket data from user %d: %s", c.userID, string(data))

		var msg NexyMessage
		if err := json.Unmarshal(data, &msg); err != nil {
			log.Printf("Error unmarshaling message: %v", err)
			continue
		}

		log.Printf("Parsed message type: %s, messageID: %s", msg.Header.Type, msg.Header.MessageID)

		if msg.Header.Type == TypeHeartbeat {
			ack, _ := NewNexyMessage(TypeAck, 0, nil, AckBody{MessageID: msg.Header.MessageID, Status: "ok"})
			ackData, _ := json.Marshal(ack)
			c.send <- ackData
			continue
		}

		msg.Header.SenderID = c.userID
		log.Printf("Broadcasting message to hub: type=%s, senderID=%d", msg.Header.Type, c.userID)
		c.hub.broadcast <- &msg
	}
}

func (c *Client) writePump() {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		c.closeConnection()
	}()

	for {
		select {
		case message, ok := <-c.send:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				c.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			if err := c.conn.WriteMessage(websocket.TextMessage, message); err != nil {
				return
			}

		case <-ticker.C:
			c.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := c.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

func (c *Client) closeConnection() {
	c.mu.Lock()
	defer c.mu.Unlock()

	if !c.isClosed {
		close(c.send)
		c.conn.Close()
		c.isClosed = true
	}
}
