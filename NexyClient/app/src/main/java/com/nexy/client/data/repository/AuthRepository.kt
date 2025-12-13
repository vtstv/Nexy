package com.nexy.client.data.repository

import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.AuthTokenManager
import com.nexy.client.data.local.NexyDatabase
import com.nexy.client.data.models.*
import com.nexy.client.data.websocket.NexyWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: NexyApiService,
    private val tokenManager: AuthTokenManager,
    private val webSocketClient: NexyWebSocketClient,
    private val database: NexyDatabase
) {
    
    suspend fun register(username: String, email: String, password: String, displayName: String, phoneNumber: String? = null): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.register(
                    RegisterRequest(username, email, password, displayName, phoneNumber)
                )
                
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    tokenManager.saveTokens(authResponse.accessToken, authResponse.refreshToken)
                    tokenManager.saveUserId(authResponse.user.id)
                    Result.success(authResponse)
                } else {
                    Result.failure(Exception("Registration failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun login(email: String, password: String, rememberMe: Boolean = false): Result<AuthResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.login(AuthRequest(email, password))
                
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    tokenManager.saveTokens(authResponse.accessToken, authResponse.refreshToken)
                    tokenManager.saveUserId(authResponse.user.id)
                    
                    if (rememberMe) {
                        tokenManager.saveCredentials(email)
                    } else {
                        tokenManager.clearCredentials()
                    }
                    
                    Result.success(authResponse)
                } else {
                    Result.failure(Exception("Login failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun logout(clearCredentials: Boolean = false): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Disconnect WebSocket first
                webSocketClient.disconnect()
                
                try {
                    apiService.logout()
                } catch (e: Exception) {
                    // Ignore API logout failure, proceed with local cleanup
                }
                
                tokenManager.clearTokens()
                
                // Explicitly clear all tables to ensure no data leaks
                database.chatDao().deleteAllChats()
                database.messageDao().deleteAllMessages()
                database.userDao().deleteAllUsers()
                database.pendingMessageDao().deleteAll()
                database.searchHistoryDao().clearHistory()
                
                // Fallback to clearAllTables to catch any other tables
                try {
                    database.clearAllTables()
                } catch (e: Exception) {
                    // Ignore if clearAllTables fails (e.g. open transaction)
                }
                
                if (clearCredentials) {
                    tokenManager.clearCredentials()
                }
                Result.success(Unit)
            } catch (e: Exception) {
                // Ensure cleanup happens even on unexpected errors
                webSocketClient.disconnect()
                tokenManager.clearTokens()
                
                try {
                    database.chatDao().deleteAllChats()
                    database.messageDao().deleteAllMessages()
                    database.userDao().deleteAllUsers()
                    database.pendingMessageDao().deleteAll()
                    database.searchHistoryDao().clearHistory()
                    database.clearAllTables()
                } catch (cleanupError: Exception) {
                    // Log error but continue
                }
                
                if (clearCredentials) {
                    tokenManager.clearCredentials()
                }
                Result.success(Unit)
            }
        }
    }
    
    suspend fun getSavedCredentials(): String? {
        return tokenManager.getSavedEmail()
    }
    
    suspend fun isRememberMeEnabled(): Boolean {
        return tokenManager.isRememberMeEnabled()
    }
    
    suspend fun getCurrentUser(): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getCurrentUser()
                
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to get current user"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }
}
