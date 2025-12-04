/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"github.com/vtstv/nexy/internal/repositories"
)

type UserService struct {
	userRepo *repositories.UserRepository
	chatRepo *repositories.ChatRepository
}

func NewUserService(userRepo *repositories.UserRepository, chatRepo *repositories.ChatRepository) *UserService {
	return &UserService{
		userRepo: userRepo,
		chatRepo: chatRepo,
	}
}
