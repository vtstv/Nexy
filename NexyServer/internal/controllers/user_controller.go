/*
 * © 2025 Murr | https://github.com/vtstv
 */
package controllers

import (
	"github.com/vtstv/nexy/internal/services"
)

type UserController struct {
	userService *services.UserService
	qrService   *services.QRService
}

func NewUserController(userService *services.UserService, qrService *services.QRService) *UserController {
	return &UserController{
		userService: userService,
		qrService:   qrService,
	}
}
