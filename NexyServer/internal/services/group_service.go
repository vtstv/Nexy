/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"github.com/vtstv/nexy/internal/repositories"
)

type GroupService struct {
	chatRepo *repositories.ChatRepository
	userRepo *repositories.UserRepository
}

func NewGroupService(chatRepo *repositories.ChatRepository, userRepo *repositories.UserRepository) *GroupService {
	return &GroupService{
		chatRepo: chatRepo,
		userRepo: userRepo,
	}
}
