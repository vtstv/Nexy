package com.nexy.client.data.api

import com.nexy.client.data.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface NexyApiService {
    
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>
    
    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>
    
    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<AuthResponse>
    
    @POST("auth/logout")
    suspend fun logout(): Response<Unit>
    
    @GET("users/me")
    suspend fun getCurrentUser(): Response<User>
    
    @GET("users/{userId}")
    suspend fun getUserById(@Path("userId") userId: Int): Response<User>
    
    @GET("users/search")
    suspend fun searchUsers(@Query("query") query: String): Response<List<User>>
    
    @PUT("users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<User>
    
    @GET("chats")
    suspend fun getChats(): Response<List<Chat>>
    
    @GET("chats/{chatId}")
    suspend fun getChatById(@Path("chatId") chatId: Int): Response<Chat>
    
    @POST("chats")
    suspend fun createChat(@Body chat: Chat): Response<Chat>
    
    @GET("chats/groups/{chatId}/members")
    suspend fun getGroupMembers(
        @Path("chatId") chatId: Int,
        @Query("q") query: String? = null
    ): Response<List<ChatMember>>
    
    // Message history - server uses query params instead of path
    @GET("messages/history")
    suspend fun getMessages(
        @Query("chat_id") chatId: Int,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<List<Message>>
    
    @GET("chats/{chatId}/messages/search")
    suspend fun searchMessages(
        @Path("chatId") chatId: Int,
        @Query("q") query: String
    ): Response<List<Message>>
    
    @POST("messages/delete")
    suspend fun deleteMessage(@Body request: DeleteMessageRequest): Response<Unit>
    
    @PUT("messages/{id}")
    suspend fun updateMessage(
        @Path("id") messageId: String,
        @Body request: UpdateMessageRequest
    ): Response<Message>

    // Invite endpoints - server uses POST with body instead of path params
    @POST("invites")
    suspend fun createInviteLink(@Body request: CreateInviteRequest): Response<InviteLink>
    
    @POST("invites/validate")
    suspend fun validateInviteCode(@Body request: ValidateInviteRequest): Response<InviteLink>
    
    @POST("invites/use")
    suspend fun useInviteCode(@Body request: UseInviteRequest): Response<JoinChatResponse>
    
    @GET("invites")
    suspend fun getMyInvites(): Response<List<InviteLink>>
    
    // File upload - server expects /files/upload path
    @Multipart
    @POST("files/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("type") type: RequestBody
    ): Response<UploadResponse>
    
    @GET("files/{fileId}")
    @Streaming
    suspend fun downloadFile(@Path("fileId") fileId: String): Response<okhttp3.ResponseBody>
    
    // Contact endpoints
    @POST("contacts")
    suspend fun addContact(@Body request: AddContactRequest): Response<Unit>
    
    @GET("contacts")
    suspend fun getContacts(): Response<List<ContactWithUser>>
    
    @GET("contacts/status")
    suspend fun checkContactStatus(@Query("user_id") userId: Int): Response<ContactStatusResponse>
    
    @PUT("contacts/{contactId}")
    suspend fun updateContactStatus(
        @Path("contactId") contactId: Int,
        @Body request: UpdateContactStatusRequest
    ): Response<Unit>
    
    @DELETE("contacts/{contactUserId}")
    suspend fun deleteContact(@Path("contactUserId") contactUserId: Int): Response<Unit>
    
    // Chat creation endpoint
    @POST("chats/create")
    suspend fun createPrivateChat(@Body request: CreateChatRequest): Response<Chat>
    
    @POST("chats/group/create")
    suspend fun createGroupChat(@Body request: CreateGroupChatRequest): Response<Chat>
    
    @DELETE("chats/{chatId}")
    suspend fun deleteChat(@Path("chatId") chatId: Int): Response<Unit>
    
    @DELETE("chats/{chatId}/messages")
    suspend fun clearChatMessages(@Path("chatId") chatId: Int): Response<Unit>
    
    @POST("chats/groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): Response<Chat>
    
    @GET("chats/groups/{groupId}")
    suspend fun getGroup(@Path("groupId") groupId: Int): Response<Chat>
    
    @PUT("chats/groups/{groupId}")
    suspend fun updateGroup(
        @Path("groupId") groupId: Int,
        @Body request: UpdateGroupRequest
    ): Response<Chat>
    
    @GET("chats/groups/{groupId}/members")
    suspend fun getGroupMembers(@Path("groupId") groupId: Int): Response<List<ChatMember>>
    
    @PUT("chats/groups/{groupId}/members/{userId}/role")
    suspend fun updateMemberRole(
        @Path("groupId") groupId: Int,
        @Path("userId") userId: Int,
        @Body request: UpdateMemberRoleRequest
    ): Response<Unit>
    
    @DELETE("chats/groups/{groupId}/members/{userId}")
    suspend fun removeMember(
        @Path("groupId") groupId: Int,
        @Path("userId") userId: Int
    ): Response<Unit>
    
    @POST("chats/groups/{groupId}/members")
    suspend fun addGroupMember(
        @Path("groupId") groupId: Int,
        @Body request: AddMemberRequest
    ): Response<Unit>
    
    @POST("chats/groups/{groupId}/invites")
    suspend fun createGroupInviteLink(
        @Path("groupId") groupId: Int,
        @Body request: CreateInviteLinkRequest
    ): Response<ChatInviteLink>
    
    @POST("chats/groups/{groupId}/transfer-ownership")
    suspend fun transferOwnership(
        @Path("groupId") groupId: Int,
        @Body request: TransferOwnershipRequest
    ): Response<Unit>
    
    @POST("chats/groups/{groupId}/join")
    suspend fun joinPublicGroup(@Path("groupId") groupId: Int): Response<Chat>
    
    @POST("chats/groups/@{username}")
    suspend fun joinGroupByUsername(@Path("username") username: String): Response<Chat>
    
    @GET("chats/groups/search")
    suspend fun searchPublicGroups(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): Response<List<Chat>>
    
    @POST("chats/groups/join")
    suspend fun joinGroupByInvite(@Body request: JoinByInviteRequest): Response<Chat>
    
    @GET("turn/ice-servers")
    suspend fun getICEServers(): Response<ICEConfigResponse>

    // Session/Device management endpoints
    @GET("sessions")
    suspend fun getSessions(): Response<List<UserSession>>

    @DELETE("sessions/{sessionId}")
    suspend fun deleteSession(@Path("sessionId") sessionId: Int): Response<Unit>

    @DELETE("sessions/others")
    suspend fun deleteAllOtherSessions(): Response<Unit>

    // Chat Folders endpoints
    @GET("folders")
    suspend fun getFolders(): Response<List<ChatFolder>>

    @GET("folders/{folderId}")
    suspend fun getFolder(@Path("folderId") folderId: Int): Response<ChatFolder>

    @POST("folders")
    suspend fun createFolder(@Body request: CreateFolderRequest): Response<ChatFolder>

    @PUT("folders/{folderId}")
    suspend fun updateFolder(
        @Path("folderId") folderId: Int,
        @Body request: UpdateFolderRequest
    ): Response<ChatFolder>

    @DELETE("folders/{folderId}")
    suspend fun deleteFolder(@Path("folderId") folderId: Int): Response<Unit>

    @POST("folders/{folderId}/chats")
    suspend fun addChatsToFolder(
        @Path("folderId") folderId: Int,
        @Body request: AddChatsToFolderRequest
    ): Response<Unit>

    @DELETE("folders/{folderId}/chats/{chatId}")
    suspend fun removeChatFromFolder(
        @Path("folderId") folderId: Int,
        @Path("chatId") chatId: Int
    ): Response<Unit>

    @PUT("folders/reorder")
    suspend fun reorderFolders(@Body request: ReorderFoldersRequest): Response<Unit>

    @POST("chats/{chatId}/mute")
    suspend fun muteChat(
        @Path("chatId") chatId: Int,
        @Body request: MuteChatRequest
    ): Response<Unit>

    @POST("chats/{chatId}/unmute")
    suspend fun unmuteChat(@Path("chatId") chatId: Int): Response<Unit>
}

data class ICEServer(
    @com.google.gson.annotations.SerializedName("urls")
    val urls: List<String>,
    @com.google.gson.annotations.SerializedName("username")
    val username: String? = null,
    @com.google.gson.annotations.SerializedName("credential")
    val credential: String? = null
)

data class ICEConfigResponse(
    @com.google.gson.annotations.SerializedName("iceServers")
    val iceServers: List<ICEServer>
)

data class CreateChatRequest(
    @com.google.gson.annotations.SerializedName("recipient_id")
    val recipientId: Int
)

data class CreateGroupChatRequest(
    @com.google.gson.annotations.SerializedName("name")
    val name: String,
    @com.google.gson.annotations.SerializedName("participant_ids")
    val participantIds: List<Int>
)

data class AddMemberRequest(
    @com.google.gson.annotations.SerializedName("user_id")
    val userId: Int
)

data class TransferOwnershipRequest(
    @com.google.gson.annotations.SerializedName("new_owner_id")
    val newOwnerId: Int
)

data class UploadResponse(
    @com.google.gson.annotations.SerializedName("thumbnailUrl")
    val thumbnailUrl: String? = null,
    @com.google.gson.annotations.SerializedName("url")
    val url: String
)
