package controllers

import (
	"encoding/json"
	"log"
	"net/http"
	"strconv"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy/internal/middleware"
	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

type FolderController struct {
	folderRepo *repositories.FolderRepository
}

func NewFolderController(folderRepo *repositories.FolderRepository) *FolderController {
	return &FolderController{folderRepo: folderRepo}
}

type CreateFolderRequest struct {
	Name               string `json:"name"`
	Icon               string `json:"icon"`
	Color              string `json:"color"`
	IncludeContacts    bool   `json:"include_contacts"`
	IncludeNonContacts bool   `json:"include_non_contacts"`
	IncludeGroups      bool   `json:"include_groups"`
	IncludeChannels    bool   `json:"include_channels"`
	IncludeBots        bool   `json:"include_bots"`
	IncludedChats      []int  `json:"included_chats"`
}

type UpdateFolderRequest struct {
	Name               string `json:"name"`
	Icon               string `json:"icon"`
	Color              string `json:"color"`
	Position           int    `json:"position"`
	IncludeContacts    *bool  `json:"include_contacts,omitempty"`
	IncludeNonContacts *bool  `json:"include_non_contacts,omitempty"`
	IncludeGroups      *bool  `json:"include_groups,omitempty"`
	IncludeChannels    *bool  `json:"include_channels,omitempty"`
	IncludeBots        *bool  `json:"include_bots,omitempty"`
	IncludedChats      *[]int `json:"included_chats,omitempty"`
}

type ReorderFoldersRequest struct {
	Positions map[int]int `json:"positions"` // folderID -> position
}

// GET /api/folders - Get all folders for current user
func (c *FolderController) GetFolders(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	folders, err := c.folderRepo.GetAllFoldersWithChats(r.Context(), userID)
	if err != nil {
		log.Printf("GetFolders error: %v", err)
		http.Error(w, "Failed to get folders", http.StatusInternalServerError)
		return
	}

	if folders == nil {
		folders = []models.ChatFolderWithChats{}
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(folders)
}

// GET /api/folders/{id} - Get single folder
func (c *FolderController) GetFolder(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	folderID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid folder ID", http.StatusBadRequest)
		return
	}

	folder, err := c.folderRepo.GetFolderWithChats(r.Context(), folderID)
	if err != nil {
		http.Error(w, "Folder not found", http.StatusNotFound)
		return
	}

	if folder.UserID != userID {
		http.Error(w, "Forbidden", http.StatusForbidden)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(folder)
}

// POST /api/folders - Create new folder
func (c *FolderController) CreateFolder(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req CreateFolderRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if req.Name == "" {
		http.Error(w, "Folder name is required", http.StatusBadRequest)
		return
	}

	// Get current folder count for position
	existingFolders, _ := c.folderRepo.GetByUserID(r.Context(), userID)
	position := len(existingFolders)

	folder := &models.ChatFolder{
		UserID:             userID,
		Name:               req.Name,
		Icon:               req.Icon,
		Color:              req.Color,
		Position:           position,
		IncludeContacts:    req.IncludeContacts,
		IncludeNonContacts: req.IncludeNonContacts,
		IncludeGroups:      req.IncludeGroups,
		IncludeChannels:    req.IncludeChannels,
		IncludeBots:        req.IncludeBots,
	}

	if folder.Icon == "" {
		folder.Icon = "folder"
	}
	if folder.Color == "" {
		folder.Color = "default"
	}

	if err := c.folderRepo.Create(r.Context(), folder); err != nil {
		log.Printf("CreateFolder error: %v", err)
		http.Error(w, "Failed to create folder", http.StatusInternalServerError)
		return
	}

	// Set included chats
	if len(req.IncludedChats) > 0 {
		c.folderRepo.SetIncludedChats(r.Context(), folder.ID, req.IncludedChats)
	}

	// Return folder with chats
	result, _ := c.folderRepo.GetFolderWithChats(r.Context(), folder.ID)

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(result)
}

// PUT /api/folders/{id} - Update folder
func (c *FolderController) UpdateFolder(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	folderID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid folder ID", http.StatusBadRequest)
		return
	}

	// Check ownership
	existing, err := c.folderRepo.GetByID(r.Context(), folderID)
	if err != nil {
		http.Error(w, "Folder not found", http.StatusNotFound)
		return
	}
	if existing.UserID != userID {
		http.Error(w, "Forbidden", http.StatusForbidden)
		return
	}

	var req UpdateFolderRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	folder := &models.ChatFolder{
		ID:                 folderID,
		UserID:             userID,
		Name:               req.Name,
		Icon:               req.Icon,
		Color:              req.Color,
		Position:           req.Position,
		IncludeContacts:    existing.IncludeContacts,
		IncludeNonContacts: existing.IncludeNonContacts,
		IncludeGroups:      existing.IncludeGroups,
		IncludeChannels:    existing.IncludeChannels,
		IncludeBots:        existing.IncludeBots,
	}

	// Update filter flags if provided
	if req.IncludeContacts != nil {
		folder.IncludeContacts = *req.IncludeContacts
	}
	if req.IncludeNonContacts != nil {
		folder.IncludeNonContacts = *req.IncludeNonContacts
	}
	if req.IncludeGroups != nil {
		folder.IncludeGroups = *req.IncludeGroups
	}
	if req.IncludeChannels != nil {
		folder.IncludeChannels = *req.IncludeChannels
	}
	if req.IncludeBots != nil {
		folder.IncludeBots = *req.IncludeBots
	}

	if err := c.folderRepo.Update(r.Context(), folder); err != nil {
		http.Error(w, "Failed to update folder", http.StatusInternalServerError)
		return
	}

	// Update included chats only if explicitly provided (non-nil)
	// Don't update chats if just updating folder name/icon/color
	if req.IncludedChats != nil {
		c.folderRepo.SetIncludedChats(r.Context(), folderID, *req.IncludedChats)
	}

	// Return updated folder
	result, _ := c.folderRepo.GetFolderWithChats(r.Context(), folderID)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

// DELETE /api/folders/{id} - Delete folder
func (c *FolderController) DeleteFolder(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	folderID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid folder ID", http.StatusBadRequest)
		return
	}

	if err := c.folderRepo.Delete(r.Context(), folderID, userID); err != nil {
		http.Error(w, "Failed to delete folder", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// PUT /api/folders/reorder - Reorder folders
func (c *FolderController) ReorderFolders(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	var req ReorderFoldersRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	if err := c.folderRepo.UpdatePositions(r.Context(), userID, req.Positions); err != nil {
		http.Error(w, "Failed to reorder folders", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}

// POST /api/folders/{id}/chats - Add chats to folder
type AddChatsRequest struct {
	ChatIDs []int `json:"chat_ids"`
}

func (c *FolderController) AddChatsToFolder(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	folderID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid folder ID", http.StatusBadRequest)
		return
	}

	// Check ownership
	existing, err := c.folderRepo.GetByID(r.Context(), folderID)
	if err != nil {
		http.Error(w, "Folder not found", http.StatusNotFound)
		return
	}
	if existing.UserID != userID {
		http.Error(w, "Forbidden", http.StatusForbidden)
		return
	}

	var req AddChatsRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	log.Printf("AddChatsToFolder: folderID=%d, chatIDs=%v", folderID, req.ChatIDs)

	// Set included chats (replaces existing)
	if err := c.folderRepo.SetIncludedChats(r.Context(), folderID, req.ChatIDs); err != nil {
		log.Printf("AddChatsToFolder error: %v", err)
		http.Error(w, "Failed to add chats to folder", http.StatusInternalServerError)
		return
	}

	// Return updated folder
	result, _ := c.folderRepo.GetFolderWithChats(r.Context(), folderID)

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(result)
}

// DELETE /api/folders/{id}/chats/{chatId} - Remove chat from folder
func (c *FolderController) RemoveChatFromFolder(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	folderID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid folder ID", http.StatusBadRequest)
		return
	}

	chatID, err := strconv.Atoi(vars["chatId"])
	if err != nil {
		http.Error(w, "Invalid chat ID", http.StatusBadRequest)
		return
	}

	// Check ownership
	existing, err := c.folderRepo.GetByID(r.Context(), folderID)
	if err != nil {
		http.Error(w, "Folder not found", http.StatusNotFound)
		return
	}
	if existing.UserID != userID {
		http.Error(w, "Forbidden", http.StatusForbidden)
		return
	}

	if err := c.folderRepo.RemoveChatFromFolder(r.Context(), folderID, chatID); err != nil {
		http.Error(w, "Failed to remove chat from folder", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusNoContent)
}
