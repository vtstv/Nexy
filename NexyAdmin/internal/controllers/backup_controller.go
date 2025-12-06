package controllers

import (
	"encoding/json"
	"net/http"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy-admin/internal/services"
)

type BackupController struct {
	service *services.BackupService
}

func NewBackupController(service *services.BackupService) *BackupController {
	return &BackupController{service: service}
}

func (c *BackupController) CreateBackup(w http.ResponseWriter, r *http.Request) {
	filename, err := c.service.CreateBackup(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{
		"status":   "success",
		"filename": filename,
	})
}

func (c *BackupController) ListBackups(w http.ResponseWriter, r *http.Request) {
	backups, err := c.service.ListBackups(r.Context())
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(backups)
}

func (c *BackupController) RestoreBackup(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Filename string `json:"filename"`
	}

	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "invalid request body", http.StatusBadRequest)
		return
	}

	if err := c.service.RestoreBackup(r.Context(), req.Filename); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "success"})
}

func (c *BackupController) DownloadBackup(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	filename := vars["filename"]

	path := c.service.GetBackupPath(filename)

	w.Header().Set("Content-Disposition", "attachment; filename="+filename)
	w.Header().Set("Content-Type", "application/gzip")

	http.ServeFile(w, r, path)
}

func (c *BackupController) DeleteBackup(w http.ResponseWriter, r *http.Request) {
	vars := mux.Vars(r)
	filename := vars["filename"]

	if err := c.service.DeleteBackup(r.Context(), filename); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"status": "success"})
}
