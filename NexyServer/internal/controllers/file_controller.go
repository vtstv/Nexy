/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"encoding/json"
	"net/http"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy/internal/middleware"
	"github.com/vtstv/nexy/internal/services"
)

type FileController struct {
	fileService *services.FileService
}

func NewFileController(fileService *services.FileService) *FileController {
	return &FileController{fileService: fileService}
}

func (c *FileController) UploadFile(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	if err := r.ParseMultipartForm(32 << 20); err != nil {
		http.Error(w, "Failed to parse form", http.StatusBadRequest)
		return
	}

	file, header, err := r.FormFile("file")
	if err != nil {
		http.Error(w, "Failed to get file", http.StatusBadRequest)
		return
	}
	defer file.Close()

	fileType := r.FormValue("type")
	uploadedFile, err := c.fileService.UploadFile(r.Context(), userID, header, fileType)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	// Return format expected by client: { url: string, thumbnailUrl?: string }
	response := map[string]interface{}{
		"url":          uploadedFile.URL,
		"thumbnailUrl": nil,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(response)
}

func (c *FileController) GetFile(w http.ResponseWriter, r *http.Request) {
	// Try to get fileID from path variable first, then from query param
	vars := mux.Vars(r)
	fileID := vars["fileId"]
	if fileID == "" {
		fileID = r.URL.Query().Get("file_id")
	}

	if fileID == "" {
		http.Error(w, "Missing file_id parameter", http.StatusBadRequest)
		return
	}

	file, err := c.fileService.GetFileByID(r.Context(), fileID)
	if err != nil {
		http.Error(w, "File not found", http.StatusNotFound)
		return
	}

	// Set proper content type and headers for download
	w.Header().Set("Content-Type", file.MimeType)
	w.Header().Set("Content-Disposition", "attachment; filename=\""+file.OriginalFilename+"\"")

	http.ServeFile(w, r, file.StoragePath)
}
