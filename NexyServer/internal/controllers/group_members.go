/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy/internal/middleware"
	nexy "github.com/vtstv/nexy/internal/ws"
)

type UpdateMemberRoleRequest struct {
	Role string `json:"role"`
}

type TransferOwnershipRequest struct {
	NewOwnerID int `json:"new_owner_id"`
}

type AddMemberRequest struct {
	UserID int `json:"user_id"`
}

// GetGroupMembers retrieves list of group members
func (c *GroupController) GetGroupMembers(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	groupID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid group ID", http.StatusBadRequest)
		return
	}

	query := r.URL.Query().Get("q")
	members, err := c.groupService.GetGroupMembers(r.Context(), groupID, userID, query)
	if err != nil {
		http.Error(w, err.Error(), http.StatusForbidden)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(members)
}

// AddMember adds a new member to the group
func (c *GroupController) AddMember(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	groupID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid group ID", http.StatusBadRequest)
		return
	}

	var req AddMemberRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	err = c.groupService.AddMember(r.Context(), groupID, userID, req.UserID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// Get group info for notification
	group, err := c.groupService.GetGroup(r.Context(), groupID, userID)
	if err == nil && group != nil && c.hub != nil {
		// Send notification to added user
		addedMsg, _ := nexy.NewNexyMessage(nexy.TypeAddedToGroup, userID, &groupID, nexy.AddedToGroupBody{
			ChatID:    groupID,
			ChatName:  group.Name,
			ChatType:  string(group.Type),
			GroupType: string(group.GroupType),
			AddedBy:   userID,
		})
		c.hub.SendToUser(req.UserID, addedMsg)
	}

	w.WriteHeader(http.StatusOK)
}

// RemoveMember removes a member from the group
func (c *GroupController) RemoveMember(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	groupID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid group ID", http.StatusBadRequest)
		return
	}
	targetUserID, err := strconv.Atoi(vars["userId"])
	if err != nil {
		http.Error(w, "Invalid user ID", http.StatusBadRequest)
		return
	}

	err = c.groupService.RemoveMember(r.Context(), groupID, userID, targetUserID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusForbidden)
		return
	}

	w.WriteHeader(http.StatusOK)
}

// UpdateMemberRole updates member's role
func (c *GroupController) UpdateMemberRole(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	groupID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid group ID", http.StatusBadRequest)
		return
	}
	targetUserID, err := strconv.Atoi(vars["userId"])
	if err != nil {
		http.Error(w, "Invalid user ID", http.StatusBadRequest)
		return
	}

	var req UpdateMemberRoleRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	err = c.groupService.UpdateMemberRole(r.Context(), groupID, userID, targetUserID, req.Role)
	if err != nil {
		http.Error(w, err.Error(), http.StatusForbidden)
		return
	}

	w.WriteHeader(http.StatusOK)
}

// TransferOwnership transfers group ownership
func (c *GroupController) TransferOwnership(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	groupID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid group ID", http.StatusBadRequest)
		return
	}

	var req TransferOwnershipRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}

	err = c.groupService.TransferOwnership(r.Context(), groupID, userID, req.NewOwnerID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusForbidden)
		return
	}

	w.WriteHeader(http.StatusOK)
}

// KickMember kicks a member from the group
func (c *GroupController) KickMember(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	groupID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid group ID", http.StatusBadRequest)
		return
	}
	targetUserID, err := strconv.Atoi(vars["userId"])
	if err != nil {
		http.Error(w, "Invalid user ID", http.StatusBadRequest)
		return
	}

	err = c.groupService.RemoveMember(r.Context(), groupID, userID, targetUserID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusForbidden)
		return
	}

	// Send notification to kicked user
	if c.hub != nil {
		kickedMsg, _ := nexy.NewNexyMessage(nexy.TypeKickedFromGroup, userID, &groupID, nexy.KickedFromGroupBody{
			ChatID:   groupID,
			KickedBy: userID,
		})
		c.hub.SendToUser(targetUserID, kickedMsg)
	}

	w.WriteHeader(http.StatusOK)
}

type BanMemberRequest struct {
	Reason string `json:"reason"`
}

// BanMember bans a member from the group
func (c *GroupController) BanMember(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	groupID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid group ID", http.StatusBadRequest)
		return
	}
	targetUserID, err := strconv.Atoi(vars["userId"])
	if err != nil {
		http.Error(w, "Invalid user ID", http.StatusBadRequest)
		return
	}

	var req BanMemberRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		// Allow empty body (no reason)
		req.Reason = ""
	}

	err = c.groupService.BanMember(r.Context(), groupID, userID, targetUserID, req.Reason)
	if err != nil {
		http.Error(w, err.Error(), http.StatusForbidden)
		return
	}

	// Send notification to banned user
	if c.hub != nil {
		bannedMsg, _ := nexy.NewNexyMessage(nexy.TypeBannedFromGroup, userID, &groupID, nexy.BannedFromGroupBody{
			ChatID:   groupID,
			BannedBy: userID,
			Reason:   req.Reason,
		})
		c.hub.SendToUser(targetUserID, bannedMsg)
	}

	w.WriteHeader(http.StatusOK)
}

// UnbanMember removes a ban from a user
func (c *GroupController) UnbanMember(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	groupID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid group ID", http.StatusBadRequest)
		return
	}
	targetUserID, err := strconv.Atoi(vars["userId"])
	if err != nil {
		http.Error(w, "Invalid user ID", http.StatusBadRequest)
		return
	}

	err = c.groupService.UnbanMember(r.Context(), groupID, userID, targetUserID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusForbidden)
		return
	}

	w.WriteHeader(http.StatusOK)
}

// GetBannedMembers returns list of banned users
func (c *GroupController) GetBannedMembers(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	vars := mux.Vars(r)
	groupID, err := strconv.Atoi(vars["id"])
	if err != nil {
		http.Error(w, "Invalid group ID", http.StatusBadRequest)
		return
	}

	bans, err := c.groupService.GetBannedMembers(r.Context(), groupID, userID)
	if err != nil {
		http.Error(w, err.Error(), http.StatusForbidden)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(bans)
}
