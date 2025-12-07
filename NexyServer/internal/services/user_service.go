/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"github.com/vtstv/nexy/internal/repositories"
)

type UserService struct {
	userRepo            *repositories.UserRepository
	chatRepo            *repositories.ChatRepository
	messageRepo         *repositories.MessageRepository
	tokenRepo           *repositories.RefreshTokenRepository
	onlineStatusService *OnlineStatusService
	onlineChecker       OnlineChecker
}

func NewUserService(userRepo *repositories.UserRepository, chatRepo *repositories.ChatRepository, messageRepo *repositories.MessageRepository) *UserService {
	return &UserService{
		userRepo:    userRepo,
		chatRepo:    chatRepo,
		messageRepo: messageRepo,
	}
}

func (s *UserService) SetTokenRepository(repo *repositories.RefreshTokenRepository) {
	s.tokenRepo = repo
}

func (s *UserService) SetOnlineStatusService(service *OnlineStatusService) {
	s.onlineStatusService = service
}

func (s *UserService) SetOnlineChecker(checker OnlineChecker) {
	s.onlineChecker = checker
}
