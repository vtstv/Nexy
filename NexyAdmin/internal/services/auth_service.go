// Copyright (c) 2025 Nexy Project. All rights reserved.
// GitHub: https://github.com/vtstv/Nexy

package services

import (
	"context"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/vtstv/nexy-admin/internal/models"
	"github.com/vtstv/nexy-admin/internal/repositories"
	"golang.org/x/crypto/bcrypt"
)

type AuthService struct {
	userRepo      *repositories.UserRepository
	jwtSecret     string
	expiration    time.Duration
	adminUsername string
	adminPassword string
}

func NewAuthService(userRepo *repositories.UserRepository, jwtSecret string, expiration time.Duration, adminUsername, adminPassword string) *AuthService {
	return &AuthService{
		userRepo:      userRepo,
		jwtSecret:     jwtSecret,
		expiration:    expiration,
		adminUsername: adminUsername,
		adminPassword: adminPassword,
	}
}

func (s *AuthService) Login(ctx context.Context, username, password string) (*models.TokenResponse, error) {
	if username != s.adminUsername || password != s.adminPassword {
		return nil, fmt.Errorf("invalid credentials")
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"admin_id": 1,
		"username": username,
		"exp":      time.Now().Add(s.expiration).Unix(),
		"iat":      time.Now().Unix(),
	})

	tokenString, err := token.SignedString([]byte(s.jwtSecret))
	if err != nil {
		return nil, err
	}

	return &models.TokenResponse{
		Token:     tokenString,
		ExpiresAt: time.Now().Add(s.expiration),
	}, nil
}

func (s *AuthService) ValidatePassword(hashedPassword, password string) bool {
	err := bcrypt.CompareHashAndPassword([]byte(hashedPassword), []byte(password))
	return err == nil
}
