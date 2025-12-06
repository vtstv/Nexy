package controllers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/vtstv/nexy-admin/internal/services"
)

type StatsController struct {
	service *services.StatsService
}

func NewStatsController(service *services.StatsService) *StatsController {
	return &StatsController{service: service}
}

func (c *StatsController) GetOverview(w http.ResponseWriter, r *http.Request) {
	stats, err := c.service.GetOverview(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}

func (c *StatsController) GetUserStats(w http.ResponseWriter, r *http.Request) {
	days, _ := strconv.Atoi(r.URL.Query().Get("days"))
	if days <= 0 {
		days = 30
	}

	stats, err := c.service.GetUserStats(r.Context(), days)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}

func (c *StatsController) GetChatStats(w http.ResponseWriter, r *http.Request) {
	stats, err := c.service.GetChatStats(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}

func (c *StatsController) GetMessageStats(w http.ResponseWriter, r *http.Request) {
	days, _ := strconv.Atoi(r.URL.Query().Get("days"))
	if days <= 0 {
		days = 30
	}

	stats, err := c.service.GetMessageStats(r.Context(), days)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(stats)
}
