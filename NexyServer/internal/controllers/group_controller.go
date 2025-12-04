/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"github.com/vtstv/nexy/internal/services"
)

type GroupController struct {
	groupService *services.GroupService
}

func NewGroupController(groupService *services.GroupService) *GroupController {
	return &GroupController{groupService: groupService}
}
