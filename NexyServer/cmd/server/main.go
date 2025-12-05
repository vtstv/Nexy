/*
 * © 2025 Murr | https://github.com/vtstv
 */
package main

import (
	"log"
	"net/http"

	"github.com/vtstv/nexy/internal/config"
	"github.com/vtstv/nexy/internal/controllers"
	"github.com/vtstv/nexy/internal/database"
	"github.com/vtstv/nexy/internal/middleware"
	"github.com/vtstv/nexy/internal/repositories"
	"github.com/vtstv/nexy/internal/routes"
	"github.com/vtstv/nexy/internal/services"
	nexy "github.com/vtstv/nexy/internal/ws"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	db, err := database.NewDB(&cfg.Database)
	if err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer db.Close()

	redisClient, err := database.NewRedisClient(&cfg.Redis)
	if err != nil {
		log.Fatalf("Failed to connect to Redis: %v", err)
	}
	defer redisClient.Close()

	userRepo := repositories.NewUserRepository(db)
	refreshTokenRepo := repositories.NewRefreshTokenRepository(db)
	inviteRepo := repositories.NewInviteRepository(db)
	messageRepo := repositories.NewMessageRepository(db)
	chatRepo := repositories.NewChatRepository(db)
	fileRepo := repositories.NewFileRepository(db)
	e2eRepo := repositories.NewE2ERepository(db)
	contactRepo := repositories.NewContactRepository(db.DB)
	sessionRepo := repositories.NewSessionRepository(db)
	folderRepo := repositories.NewFolderRepository(db)

	authService := services.NewAuthService(userRepo, refreshTokenRepo, &cfg.JWT)
	userService := services.NewUserService(userRepo, chatRepo)
	groupService := services.NewGroupService(chatRepo, userRepo)
	inviteService := services.NewInviteService(inviteRepo)
	fileService := services.NewFileService(fileRepo, &cfg.Upload)
	messageService := services.NewMessageService(messageRepo, chatRepo, userRepo, fileService)
	qrService := services.NewQRService()
	e2eService := services.NewE2EService(e2eRepo)
	onlineStatusService := services.NewOnlineStatusService(userRepo)
	contactService := services.NewContactService(contactRepo, userRepo)

	nexyChatRepo := nexy.NewNexyChatRepo(chatRepo)
	hub := nexy.NewHub(redisClient.Client, messageRepo, nexyChatRepo, userRepo)
	go hub.Run()

	// Wire up online status service and hub to contact service
	contactService.SetOnlineStatusService(onlineStatusService)
	contactService.SetOnlineChecker(hub)

	// Wire up online status service and hub to group service
	groupService.SetOnlineStatusService(onlineStatusService)
	groupService.SetOnlineChecker(hub)

	// Wire up online status service and hub to user service
	userService.SetOnlineStatusService(onlineStatusService)
	userService.SetOnlineChecker(hub)

	authController := controllers.NewAuthController(authService, sessionRepo, folderRepo)
	userController := controllers.NewUserController(userService, qrService)
	groupController := controllers.NewGroupController(groupService)
	inviteController := controllers.NewInviteController(inviteService)
	messageController := controllers.NewMessageController(messageService, hub)
	fileController := controllers.NewFileController(fileService)
	e2eController := controllers.NewE2EController(e2eService)
	contactController := controllers.NewContactController(contactService)
	turnController := controllers.NewTURNController(cfg)
	sessionController := controllers.NewSessionController(sessionRepo, refreshTokenRepo)
	folderController := controllers.NewFolderController(folderRepo)

	wsHandler := nexy.NewWSHandler(hub)
	wsController := controllers.NewWSController(wsHandler, authService)

	authMiddleware := middleware.NewAuthMiddleware(authService)
	corsMiddleware := middleware.NewCORSMiddleware(&cfg.CORS)
	rateLimiter := middleware.NewRateLimiter(&cfg.RateLimit)

	router := routes.NewRouter(
		authController,
		userController,
		groupController,
		inviteController,
		messageController,
		fileController,
		wsController,
		e2eController,
		contactController,
		turnController,
		sessionController,
		folderController,
		authMiddleware,
		corsMiddleware,
		rateLimiter,
	)

	r := router.Setup()

	addr := ":" + cfg.Server.Port
	log.Printf("Server starting on %s", addr)

	if err := http.ListenAndServe(addr, r); err != nil {
		log.Fatalf("Server failed to start: %v", err)
	}
}
