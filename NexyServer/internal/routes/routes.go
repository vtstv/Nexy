package routes

import (
	"net/http"

	"github.com/gorilla/mux"
	"github.com/vtstv/nexy/internal/controllers"
	"github.com/vtstv/nexy/internal/middleware"
)

type Router struct {
	authController     *controllers.AuthController
	userController     *controllers.UserController
	groupController    *controllers.GroupController
	inviteController   *controllers.InviteController
	messageController  *controllers.MessageController
	fileController     *controllers.FileController
	wsController       *controllers.WSController
	e2eController      *controllers.E2EController
	contactController  *controllers.ContactController
	turnController     *controllers.TURNController
	sessionController  *controllers.SessionController
	folderController   *controllers.FolderController
	syncController     *controllers.SyncController
	fcmController      *controllers.FcmController
	reactionController *controllers.ReactionController
	authMiddleware     *middleware.AuthMiddleware
	corsMiddleware     *middleware.CORSMiddleware
	rateLimiter        *middleware.RateLimiter
}

func NewRouter(
	authController *controllers.AuthController,
	userController *controllers.UserController,
	groupController *controllers.GroupController,
	inviteController *controllers.InviteController,
	messageController *controllers.MessageController,
	fileController *controllers.FileController,
	wsController *controllers.WSController,
	e2eController *controllers.E2EController,
	contactController *controllers.ContactController,
	turnController *controllers.TURNController,
	sessionController *controllers.SessionController,
	folderController *controllers.FolderController,
	syncController *controllers.SyncController,
	fcmController *controllers.FcmController,
	reactionController *controllers.ReactionController,
	authMiddleware *middleware.AuthMiddleware,
	corsMiddleware *middleware.CORSMiddleware,
	rateLimiter *middleware.RateLimiter,
) *Router {
	return &Router{
		authController:     authController,
		userController:     userController,
		groupController:    groupController,
		inviteController:   inviteController,
		messageController:  messageController,
		fileController:     fileController,
		wsController:       wsController,
		e2eController:      e2eController,
		contactController:  contactController,
		turnController:     turnController,
		sessionController:  sessionController,
		folderController:   folderController,
		syncController:     syncController,
		fcmController:      fcmController,
		reactionController: reactionController,
		authMiddleware:     authMiddleware,
		corsMiddleware:     corsMiddleware,
		rateLimiter:        rateLimiter,
	}
}

func (rt *Router) Setup() *mux.Router {
	r := mux.NewRouter()

	r.Use(middleware.Logger)
	r.Use(rt.corsMiddleware.Handle)
	r.Use(rt.rateLimiter.Limit)

	// Handle OPTIONS for all routes (CORS preflight)
	r.Methods("OPTIONS").HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	})

	api := r.PathPrefix("/api").Subrouter()

	auth := api.PathPrefix("/auth").Subrouter()
	auth.HandleFunc("/register", rt.authController.Register).Methods("POST")
	auth.HandleFunc("/login", rt.authController.Login).Methods("POST")
	auth.HandleFunc("/refresh", rt.authController.RefreshToken).Methods("POST")
	auth.Handle("/logout", rt.authMiddleware.Authenticate(
		http.HandlerFunc(rt.authController.Logout))).Methods("POST")

	users := api.PathPrefix("/users").Subrouter()
	users.Use(rt.authMiddleware.Authenticate)
	users.HandleFunc("/me", rt.userController.GetMe).Methods("GET")
	users.HandleFunc("/me", rt.userController.UpdateProfile).Methods("PUT")
	users.HandleFunc("/me/qr", rt.userController.GetMyQRCode).Methods("GET")
	users.HandleFunc("/search", rt.userController.SearchUsers).Methods("GET")
	users.HandleFunc("/{id:[0-9]+}", rt.userController.GetUserByID).Methods("GET")

	// FCM token management
	users.HandleFunc("/fcm-token", rt.fcmController.UpdateFcmToken).Methods("POST")
	users.HandleFunc("/fcm-token", rt.fcmController.DeleteFcmToken).Methods("DELETE")

	// Chats endpoints
	chats := api.PathPrefix("/chats").Subrouter()
	chats.Use(rt.authMiddleware.Authenticate)
	chats.HandleFunc("", rt.userController.GetUserChats).Methods("GET")
	chats.HandleFunc("/{id:[0-9]+}", rt.userController.GetChat).Methods("GET")
	chats.HandleFunc("/{id:[0-9]+}/mute", rt.userController.MuteChat).Methods("POST")
	chats.HandleFunc("/{id:[0-9]+}/unmute", rt.userController.UnmuteChat).Methods("POST")
	chats.HandleFunc("/{id:[0-9]+}/pin", rt.userController.PinChat).Methods("POST")
	chats.HandleFunc("/{id:[0-9]+}/unpin", rt.userController.UnpinChat).Methods("POST")
	chats.HandleFunc("/{id:[0-9]+}/messages/search", rt.messageController.SearchMessages).Methods("GET")
	chats.HandleFunc("/create", rt.userController.CreatePrivateChat).Methods("POST")

	// Group endpoints
	chats.HandleFunc("/groups", rt.groupController.CreateGroup).Methods("POST")
	chats.HandleFunc("/groups/search", rt.groupController.SearchPublicGroups).Methods("GET")
	chats.HandleFunc("/groups/join", rt.groupController.JoinGroupByInvite).Methods("POST")
	chats.HandleFunc("/groups/validate-invite", rt.groupController.ValidateGroupInvite).Methods("POST")
	chats.HandleFunc("/groups/{id:[0-9]+}", rt.groupController.GetGroup).Methods("GET")
	chats.HandleFunc("/groups/{id:[0-9]+}", rt.groupController.UpdateGroup).Methods("PUT")
	chats.HandleFunc("/groups/{id:[0-9]+}/join", rt.groupController.JoinPublicGroup).Methods("POST")
	chats.HandleFunc("/groups/{id:[0-9]+}/members", rt.groupController.AddMember).Methods("POST")
	chats.HandleFunc("/groups/{id:[0-9]+}/members", rt.groupController.GetGroupMembers).Methods("GET")
	chats.HandleFunc("/groups/{id:[0-9]+}/members/{userId:[0-9]+}/role", rt.groupController.UpdateMemberRole).Methods("PUT")
	chats.HandleFunc("/groups/{id:[0-9]+}/members/{userId:[0-9]+}", rt.groupController.RemoveMember).Methods("DELETE")
	chats.HandleFunc("/groups/{id:[0-9]+}/members/{userId:[0-9]+}/kick", rt.groupController.KickMember).Methods("POST")
	chats.HandleFunc("/groups/{id:[0-9]+}/members/{userId:[0-9]+}/ban", rt.groupController.BanMember).Methods("POST")
	chats.HandleFunc("/groups/{id:[0-9]+}/members/{userId:[0-9]+}/unban", rt.groupController.UnbanMember).Methods("POST")
	chats.HandleFunc("/groups/{id:[0-9]+}/bans", rt.groupController.GetBannedMembers).Methods("GET")
	chats.HandleFunc("/groups/{id:[0-9]+}/transfer-ownership", rt.groupController.TransferOwnership).Methods("POST")
	chats.HandleFunc("/groups/{id:[0-9]+}/invites", rt.groupController.CreateInviteLink).Methods("POST")
	chats.HandleFunc("/groups/@{username}", rt.groupController.JoinGroupByUsername).Methods("POST")

	// Legacy or simple group create (can be deprecated or redirected)
	chats.HandleFunc("/group/create", rt.userController.CreateGroupChat).Methods("POST")

	chats.HandleFunc("/{chatId:[0-9]+}", rt.userController.DeleteChat).Methods("DELETE")
	chats.HandleFunc("/{chatId:[0-9]+}/messages", rt.userController.ClearChatMessages).Methods("DELETE")

	invites := api.PathPrefix("/invites").Subrouter()
	invites.Use(rt.authMiddleware.Authenticate)
	invites.HandleFunc("", rt.inviteController.CreateInvite).Methods("POST")
	invites.HandleFunc("", rt.inviteController.GetMyInvites).Methods("GET")
	invites.HandleFunc("/validate", rt.inviteController.ValidateInvite).Methods("POST")
	invites.HandleFunc("/use", rt.inviteController.UseInvite).Methods("POST")

	messages := api.PathPrefix("/messages").Subrouter()
	messages.Use(rt.authMiddleware.Authenticate)
	messages.HandleFunc("/history", rt.messageController.GetChatHistory).Methods("GET")
	messages.HandleFunc("/search", rt.messageController.SearchMessages).Methods("GET")
	messages.HandleFunc("/delete", rt.messageController.DeleteMessage).Methods("POST")
	messages.HandleFunc("/{id}", rt.messageController.UpdateMessage).Methods("PUT")
	messages.HandleFunc("/{messageId:[0-9]+}/reactions", rt.reactionController.GetReactions).Methods("GET")
	messages.HandleFunc("/reactions", rt.reactionController.AddReaction).Methods("POST")
	messages.HandleFunc("/reactions", rt.reactionController.RemoveReaction).Methods("DELETE")

	files := api.PathPrefix("/files").Subrouter()
	// Upload requires authentication
	filesAuth := files.PathPrefix("").Subrouter()
	filesAuth.Use(rt.authMiddleware.Authenticate)
	filesAuth.HandleFunc("/upload", rt.fileController.UploadFile).Methods("POST")

	// Download is public (no auth) for better compatibility with browsers/apps
	files.HandleFunc("/{fileId}", rt.fileController.GetFile).Methods("GET")
	files.HandleFunc("", rt.fileController.GetFile).Methods("GET")

	// E2E encryption endpoints
	e2e := api.PathPrefix("/e2e").Subrouter()
	e2e.Use(rt.authMiddleware.Authenticate)
	e2e.HandleFunc("/keys", rt.e2eController.UploadKeys).Methods("POST")
	e2e.HandleFunc("/keys/bundle", rt.e2eController.GetKeyBundle).Methods("GET")
	e2e.HandleFunc("/keys/prekey-count", rt.e2eController.GetPreKeyCount).Methods("GET")

	// Contacts endpoints
	contacts := api.PathPrefix("/contacts").Subrouter()
	contacts.Use(rt.authMiddleware.Authenticate)
	contacts.HandleFunc("", rt.contactController.AddContact).Methods("POST")
	contacts.HandleFunc("", rt.contactController.GetContacts).Methods("GET")
	contacts.HandleFunc("/status", rt.contactController.CheckContactStatus).Methods("GET")
	contacts.HandleFunc("/{id}", rt.contactController.UpdateContactStatus).Methods("PUT")
	contacts.HandleFunc("/{id}", rt.contactController.DeleteContact).Methods("DELETE")

	// TURN/STUN server configuration endpoint
	turn := api.PathPrefix("/turn").Subrouter()
	turn.Use(rt.authMiddleware.Authenticate)
	turn.HandleFunc("/ice-servers", rt.turnController.GetICEServers).Methods("GET")

	// Sessions endpoints (device management)
	sessions := api.PathPrefix("/sessions").Subrouter()
	sessions.Use(rt.authMiddleware.Authenticate)
	sessions.HandleFunc("", rt.sessionController.GetSessions).Methods("GET")
	sessions.HandleFunc("/{id:[0-9]+}", rt.sessionController.DeleteSession).Methods("DELETE")
	sessions.HandleFunc("/{id:[0-9]+}/settings", rt.sessionController.UpdateSessionSettings).Methods("PUT")
	sessions.HandleFunc("/others", rt.sessionController.DeleteAllOtherSessions).Methods("DELETE")

	// Folders endpoints (chat folders)
	folders := api.PathPrefix("/folders").Subrouter()
	folders.Use(rt.authMiddleware.Authenticate)
	folders.HandleFunc("", rt.folderController.GetFolders).Methods("GET")
	folders.HandleFunc("", rt.folderController.CreateFolder).Methods("POST")
	folders.HandleFunc("/reorder", rt.folderController.ReorderFolders).Methods("PUT")
	folders.HandleFunc("/{id:[0-9]+}", rt.folderController.GetFolder).Methods("GET")
	folders.HandleFunc("/{id:[0-9]+}", rt.folderController.UpdateFolder).Methods("PUT")
	folders.HandleFunc("/{id:[0-9]+}", rt.folderController.DeleteFolder).Methods("DELETE")
	folders.HandleFunc("/{id:[0-9]+}/chats", rt.folderController.AddChatsToFolder).Methods("POST")
	folders.HandleFunc("/{id:[0-9]+}/chats/{chatId:[0-9]+}", rt.folderController.RemoveChatFromFolder).Methods("DELETE")

	// Sync endpoints
	sync := api.PathPrefix("/sync").Subrouter()
	sync.Use(rt.authMiddleware.Authenticate)
	sync.HandleFunc("/state", rt.syncController.GetState).Methods("GET")
	sync.HandleFunc("/difference", rt.syncController.GetDifference).Methods("GET")
	sync.HandleFunc("/channel/{id:[0-9]+}/difference", rt.syncController.GetChannelDifference).Methods("GET")

	// WebSocket endpoint - authentication handled in controller via query param
	r.HandleFunc("/ws", rt.wsController.HandleWebSocket)

	return r
}
