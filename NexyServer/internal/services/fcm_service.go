/*
 * © 2025 Murr | https://github.com/vtstv
 */
package services

import (
	"context"
	"fmt"
	"log"

	firebase "firebase.google.com/go/v4"
	"firebase.google.com/go/v4/messaging"
	"github.com/vtstv/nexy/internal/config"
	"github.com/vtstv/nexy/internal/repositories"
	"google.golang.org/api/option"
)

type FcmService struct {
	userRepo        *repositories.UserRepository
	messagingClient *messaging.Client
	enabled         bool
}

func NewFcmService(cfg *config.Config, userRepo *repositories.UserRepository) *FcmService {
	service := &FcmService{
		userRepo: userRepo,
		enabled:  cfg.FCM.Enabled,
	}

	if !cfg.FCM.Enabled {
		log.Println("FCM is disabled")
		return service
	}

	// Initialize Firebase Admin SDK
	ctx := context.Background()
	opt := option.WithCredentialsFile(cfg.FCM.ServiceAccountKeyPath)

	app, err := firebase.NewApp(ctx, nil, opt)
	if err != nil {
		log.Printf("Error initializing Firebase app: %v\n", err)
		service.enabled = false
		return service
	}

	client, err := app.Messaging(ctx)
	if err != nil {
		log.Printf("Error getting Firebase messaging client: %v\n", err)
		service.enabled = false
		return service
	}

	service.messagingClient = client
	log.Println("FCM service initialized successfully")

	return service
}

func (s *FcmService) UpdateUserFcmToken(ctx context.Context, userID int, fcmToken string) error {
	return s.userRepo.UpdateFcmToken(ctx, userID, fcmToken)
}

func (s *FcmService) DeleteUserFcmToken(ctx context.Context, userID int) error {
	return s.userRepo.UpdateFcmToken(ctx, userID, "")
}

func (s *FcmService) GetUserFcmToken(ctx context.Context, userID int) (string, error) {
	return s.userRepo.GetFcmToken(ctx, userID)
}

// SendNotification sends a push notification to a user
func (s *FcmService) SendNotification(ctx context.Context, userID int, title, body string, data map[string]string) error {
	if !s.enabled || s.messagingClient == nil {
		log.Println("FCM is disabled or not initialized, skipping notification")
		return nil
	}

	// Get user's FCM token
	fcmToken, err := s.userRepo.GetFcmToken(ctx, userID)
	if err != nil {
		return fmt.Errorf("failed to get FCM token: %w", err)
	}

	if fcmToken == "" {
		// User doesn't have FCM token registered, skip silently
		return nil
	}

	// Prepare notification
	message := &messaging.Message{
		Token: fcmToken,
		Notification: &messaging.Notification{
			Title: title,
			Body:  body,
		},
		Data: data,
		Android: &messaging.AndroidConfig{
			Priority: "high",
			Notification: &messaging.AndroidNotification{
				Sound:     "default",
				Priority:  messaging.PriorityHigh,
				ChannelID: "messages",
			},
		},
	}

	// Send notification
	response, err := s.messagingClient.Send(ctx, message)
	if err != nil {
		return fmt.Errorf("failed to send FCM notification: %w", err)
	}

	log.Printf("Successfully sent FCM notification to user %d: %s\n", userID, response)
	return nil
}
