// Copyright (c) 2025 Nexy Project. All rights reserved.
// GitHub: https://github.com/vtstv/Nexy

package main

import (
	"context"
	"database/sql"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/gorilla/mux"
	_ "github.com/lib/pq"
	"github.com/redis/go-redis/v9"
	"github.com/vtstv/nexy-admin/internal/config"
	"github.com/vtstv/nexy-admin/internal/controllers"
	"github.com/vtstv/nexy-admin/internal/middleware"
	"github.com/vtstv/nexy-admin/internal/repositories"
	"github.com/vtstv/nexy-admin/internal/services"
)

func main() {
	cfg := config.Load()

	db, err := sql.Open("postgres", cfg.Database.URL)
	if err != nil {
		log.Fatal("Failed to connect to database:", err)
	}
	defer db.Close()

	if err := db.Ping(); err != nil {
		log.Fatal("Database ping failed:", err)
	}
	log.Println("✓ Connected to database")

	redisClient := redis.NewClient(&redis.Options{
		Addr:     cfg.Redis.URL,
		Password: cfg.Redis.Password,
		DB:       0,
	})

	ctx := context.Background()
	if err := redisClient.Ping(ctx).Err(); err != nil {
		log.Fatal("Failed to connect to Redis:", err)
	}
	log.Println("✓ Connected to Redis")

	userRepo := repositories.NewUserRepository(db)
	chatRepo := repositories.NewChatRepository(db)
	messageRepo := repositories.NewMessageRepository(db)
	statsRepo := repositories.NewStatsRepository(db)
	backupRepo := repositories.NewBackupRepository(db, cfg.Backup.Path)

	authService := services.NewAuthService(userRepo, cfg.JWT.Secret, cfg.JWT.Expiration, cfg.Admin.Username, cfg.Admin.Password)
	userService := services.NewUserService(userRepo, redisClient)
	chatService := services.NewChatService(chatRepo, userRepo)
	messageService := services.NewMessageService(messageRepo)
	statsService := services.NewStatsService(statsRepo, redisClient)
	backupService := services.NewBackupService(backupRepo)
	diagnosticService := services.NewDiagnosticService(db, redisClient)

	authController := controllers.NewAuthController(authService)
	userController := controllers.NewUserController(userService)
	chatController := controllers.NewChatController(chatService)
	messageController := controllers.NewMessageController(messageService)
	statsController := controllers.NewStatsController(statsService)
	backupController := controllers.NewBackupController(backupService)
	diagnosticController := controllers.NewDiagnosticController(diagnosticService)

	router := mux.NewRouter()

	api := router.PathPrefix("/api").Subrouter()

	api.HandleFunc("/auth/login", authController.Login).Methods("POST", "OPTIONS")

	authMiddleware := middleware.NewAuthMiddleware(cfg.JWT.Secret)
	protected := api.PathPrefix("").Subrouter()
	protected.Use(authMiddleware.Authenticate)

	protected.HandleFunc("/users", userController.GetUsers).Methods("GET")
	protected.HandleFunc("/users/{id:[0-9]+}", userController.GetUser).Methods("GET")
	protected.HandleFunc("/users/{id:[0-9]+}", userController.UpdateUser).Methods("PUT")
	protected.HandleFunc("/users/{id:[0-9]+}", userController.DeleteUser).Methods("DELETE")
	protected.HandleFunc("/users/{id:[0-9]+}/ban", userController.BanUser).Methods("POST")
	protected.HandleFunc("/users/{id:[0-9]+}/unban", userController.UnbanUser).Methods("POST")
	protected.HandleFunc("/users/{id:[0-9]+}/sessions", userController.GetUserSessions).Methods("GET")

	protected.HandleFunc("/chats", chatController.GetChats).Methods("GET")
	protected.HandleFunc("/chats/{id:[0-9]+}", chatController.GetChat).Methods("GET")
	protected.HandleFunc("/chats/{id:[0-9]+}", chatController.UpdateChat).Methods("PUT")
	protected.HandleFunc("/chats/{id:[0-9]+}", chatController.DeleteChat).Methods("DELETE")
	protected.HandleFunc("/chats/{id:[0-9]+}/members", chatController.GetChatMembers).Methods("GET")
	protected.HandleFunc("/chats/{id:[0-9]+}/members/{userId:[0-9]+}", chatController.RemoveChatMember).Methods("DELETE")
	protected.HandleFunc("/chats/{id:[0-9]+}/messages", messageController.GetChatMessages).Methods("GET")

	protected.HandleFunc("/messages", messageController.GetMessages).Methods("GET")
	protected.HandleFunc("/messages/{id:[0-9]+}", messageController.GetMessage).Methods("GET")
	protected.HandleFunc("/messages/{id:[0-9]+}", messageController.DeleteMessage).Methods("DELETE")
	protected.HandleFunc("/messages/search", messageController.SearchMessages).Methods("GET")

	protected.HandleFunc("/stats/overview", statsController.GetOverview).Methods("GET")
	protected.HandleFunc("/stats/users", statsController.GetUserStats).Methods("GET")
	protected.HandleFunc("/stats/chats", statsController.GetChatStats).Methods("GET")
	protected.HandleFunc("/stats/messages", statsController.GetMessageStats).Methods("GET")

	protected.HandleFunc("/backup/create", backupController.CreateBackup).Methods("POST")
	protected.HandleFunc("/backup/list", backupController.ListBackups).Methods("GET")
	protected.HandleFunc("/backup/restore", backupController.RestoreBackup).Methods("POST")
	protected.HandleFunc("/backup/download/{filename}", backupController.DownloadBackup).Methods("GET")
	protected.HandleFunc("/backup/delete/{filename}", backupController.DeleteBackup).Methods("DELETE")

	protected.HandleFunc("/diagnostics/health", diagnosticController.HealthCheck).Methods("GET")
	protected.HandleFunc("/diagnostics/database", diagnosticController.DatabaseDiagnostics).Methods("GET")
	protected.HandleFunc("/diagnostics/redis", diagnosticController.RedisDiagnostics).Methods("GET")
	protected.HandleFunc("/diagnostics/system", diagnosticController.SystemInfo).Methods("GET")

	router.PathPrefix("/").Handler(http.FileServer(http.Dir("./web")))

	corsMiddleware := middleware.NewCORSMiddleware()
	router.Use(corsMiddleware.Handle)

	port := cfg.Server.Port
	srv := &http.Server{
		Addr:         ":" + port,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		log.Printf("🚀 Nexy Admin Panel running on http://localhost:%s\n", port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatal("Server failed:", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down server...")
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		log.Fatal("Server forced to shutdown:", err)
	}

	log.Println("Server stopped gracefully")
}
