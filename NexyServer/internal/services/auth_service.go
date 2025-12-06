/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"

	"github.com/google/uuid"
	"github.com/vtstv/nexy/internal/config"
	"github.com/vtstv/nexy/internal/models"
	"github.com/vtstv/nexy/internal/repositories"
)

type AuthService struct {
	userRepo         *repositories.UserRepository
	refreshTokenRepo *repositories.RefreshTokenRepository
	jwtConfig        *config.JWTConfig
}

func NewAuthService(userRepo *repositories.UserRepository, refreshTokenRepo *repositories.RefreshTokenRepository, jwtConfig *config.JWTConfig) *AuthService {
	return &AuthService{
		userRepo:         userRepo,
		refreshTokenRepo: refreshTokenRepo,
		jwtConfig:        jwtConfig,
	}
}

func (s *AuthService) Register(ctx context.Context, username, email, password, displayName string) (*models.User, error) {
	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return nil, fmt.Errorf("failed to hash password: %w", err)
	}

	user := &models.User{
		Username:     username,
		Email:        email,
		PasswordHash: string(hashedPassword),
		DisplayName:  displayName,
	}

	if err := s.userRepo.Create(ctx, user); err != nil {
		return nil, fmt.Errorf("failed to create user: %w", err)
	}

	return user, nil
}

func (s *AuthService) Login(ctx context.Context, email, password string) (string, string, int, *models.User, error) {
	user, err := s.userRepo.GetByEmail(ctx, email)
	if err != nil {
		return "", "", 0, nil, fmt.Errorf("invalid credentials")
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(password)); err != nil {
		return "", "", 0, nil, fmt.Errorf("invalid credentials")
	}

	accessToken, err := s.GenerateAccessToken(user.ID)
	if err != nil {
		return "", "", 0, nil, err
	}

	refreshToken, refreshTokenID, err := s.GenerateRefreshToken(ctx, user.ID)
	if err != nil {
		return "", "", 0, nil, err
	}

	return accessToken, refreshToken, refreshTokenID, user, nil
}

func (s *AuthService) GenerateAccessToken(userID int) (string, error) {
	claims := jwt.MapClaims{
		"user_id": userID,
		"exp":     time.Now().Add(s.jwtConfig.Expiration).Unix(),
		"iat":     time.Now().Unix(),
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString([]byte(s.jwtConfig.Secret))
}

func (s *AuthService) GenerateRefreshToken(ctx context.Context, userID int) (string, int, error) {
	tokenString := uuid.New().String()

	refreshToken := &models.RefreshToken{
		UserID:    userID,
		Token:     tokenString,
		ExpiresAt: time.Now().Add(s.jwtConfig.RefreshExpiration),
	}

	if err := s.refreshTokenRepo.Create(ctx, refreshToken); err != nil {
		return "", 0, fmt.Errorf("failed to create refresh token: %w", err)
	}

	return tokenString, refreshToken.ID, nil
}

func (s *AuthService) RefreshAccessToken(ctx context.Context, refreshTokenString string) (string, error) {
	refreshToken, err := s.refreshTokenRepo.GetByToken(ctx, refreshTokenString)
	if err != nil {
		return "", fmt.Errorf("invalid refresh token")
	}

	if refreshToken == nil || refreshToken.ExpiresAt.Before(time.Now()) {
		return "", fmt.Errorf("refresh token expired")
	}

	return s.GenerateAccessToken(refreshToken.UserID)
}

func (s *AuthService) ValidateToken(tokenString string) (int, error) {
	token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method")
		}
		return []byte(s.jwtConfig.Secret), nil
	})

	if err != nil || !token.Valid {
		return 0, fmt.Errorf("invalid token")
	}

	claims, ok := token.Claims.(jwt.MapClaims)
	if !ok {
		return 0, fmt.Errorf("invalid token claims")
	}

	userID, ok := claims["user_id"].(float64)
	if !ok {
		return 0, fmt.Errorf("invalid user_id in token")
	}

	// Verify user exists in database
	ctx := context.Background()
	_, err = s.userRepo.GetByID(ctx, int(userID))
	if err != nil {
		return 0, fmt.Errorf("user not found")
	}

	return int(userID), nil
}

func (s *AuthService) Logout(ctx context.Context, userID int) error {
	return s.refreshTokenRepo.DeleteByUserID(ctx, userID)
}
