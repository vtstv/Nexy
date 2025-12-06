/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"github.com/vtstv/nexy/internal/services"
	nexy "github.com/vtstv/nexy/internal/ws"
)

type GroupController struct {
	groupService *services.GroupService
	hub          *nexy.Hub
}

func NewGroupController(groupService *services.GroupService, hub *nexy.Hub) *GroupController {
	return &GroupController{groupService: groupService, hub: hub}
}
