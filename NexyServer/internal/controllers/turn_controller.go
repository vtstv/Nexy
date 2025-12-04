package controllers

import (
	"encoding/json"
	"net/http"

	"github.com/vtstv/nexy/internal/config"
)

type TURNController struct {
	config *config.Config
}

func NewTURNController(cfg *config.Config) *TURNController {
	return &TURNController{
		config: cfg,
	}
}

type ICEServer struct {
	URLs       []string `json:"urls"`
	Username   string   `json:"username,omitempty"`
	Credential string   `json:"credential,omitempty"`
}

type ICEConfigResponse struct {
	ICEServers []ICEServer `json:"iceServers"`
}

func (c *TURNController) GetICEServers(w http.ResponseWriter, r *http.Request) {
	iceServers := []ICEServer{
		{
			URLs: []string{c.config.TURN.STUNServerURL},
		},
		{
			URLs:       []string{c.config.TURN.ServerURL},
			Username:   c.config.TURN.Username,
			Credential: c.config.TURN.Password,
		},
	}

	response := ICEConfigResponse{
		ICEServers: iceServers,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}
