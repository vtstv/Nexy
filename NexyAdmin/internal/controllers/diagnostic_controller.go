package controllers

import (
	"encoding/json"
	"net/http"

	"github.com/vtstv/nexy-admin/internal/services"
)

type DiagnosticController struct {
	service *services.DiagnosticService
}

func NewDiagnosticController(service *services.DiagnosticService) *DiagnosticController {
	return &DiagnosticController{service: service}
}

func (c *DiagnosticController) HealthCheck(w http.ResponseWriter, r *http.Request) {
	health, err := c.service.HealthCheck(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(health)
}

func (c *DiagnosticController) DatabaseDiagnostics(w http.ResponseWriter, r *http.Request) {
	diag, err := c.service.GetDatabaseDiagnostics(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(diag)
}

func (c *DiagnosticController) RedisDiagnostics(w http.ResponseWriter, r *http.Request) {
	diag, err := c.service.GetRedisDiagnostics(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(diag)
}

func (c *DiagnosticController) SystemInfo(w http.ResponseWriter, r *http.Request) {
	info, err := c.service.GetSystemInfo(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(info)
}
