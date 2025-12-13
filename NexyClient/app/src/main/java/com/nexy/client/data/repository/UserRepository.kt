package com.nexy.client.data.repository

import android.util.Log
import com.nexy.client.data.api.NexyApiService
import com.nexy.client.data.local.dao.UserDao
import com.nexy.client.data.local.entity.UserEntity
import com.nexy.client.data.models.User
import com.nexy.client.data.models.UserStatus
import com.nexy.client.data.models.UpdateProfileRequest
import com.nexy.client.data.network.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val apiService: NexyApiService,
    private val userDao: UserDao,
    private val networkMonitor: NetworkMonitor
) {
    
    suspend fun getUserById(userId: Int, forceRefresh: Boolean = false): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                // Always check cache first for faster response
                val cachedUser = userDao.getUserById(userId)
                
                // If offline, return cache immediately (don't wait for network timeout)
                if (!networkMonitor.isConnected.value) {
                    return@withContext if (cachedUser != null) {
                        Result.success(cachedUser.toModel())
                    } else {
                        Result.failure(Exception("User not found (offline)"))
                    }
                }
                
                // Online: decide based on forceRefresh and cache availability
                if (forceRefresh || cachedUser == null) {
                    val response = apiService.getUserById(userId)
                    if (response.isSuccessful && response.body() != null) {
                        val user = response.body()!!
                        userDao.insertUser(user.toEntity())
                        Result.success(user)
                    } else {
                        // API failed, return cache if available
                        if (cachedUser != null) {
                            Result.success(cachedUser.toModel())
                        } else {
                            Result.failure(Exception("User not found"))
                        }
                    }
                } else {
                    // Have cache and not forcing refresh - return cache
                    Result.success(cachedUser.toModel())
                }
            } catch (e: Exception) {
                // Fallback to cache on network error
                val cachedUser = userDao.getUserById(userId)
                if (cachedUser != null) {
                    Result.success(cachedUser.toModel())
                } else {
                    Result.failure(e)
                }
            }
        }
    }
    
    fun getUserByIdFlow(userId: Int): Flow<User?> {
        return userDao.getUserByIdFlow(userId).map { it?.toModel() }
    }
    
    suspend fun searchUsers(query: String): Result<List<User>> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("UserRepository", "Searching users with query: '$query'")
                val response = apiService.searchUsers(query)
                Log.d("UserRepository", "Search API response: code=${response.code()}, isSuccessful=${response.isSuccessful}")
                
                if (response.isSuccessful) {
                    val users = response.body() ?: emptyList()
                    Log.d("UserRepository", "API returned ${users.size} users")
                    if (users.isNotEmpty()) {
                        userDao.insertUsers(users.map { it.toEntity() })
                        Log.d("UserRepository", "Cached ${users.size} users to database")
                    }
                    Result.success(users)
                } else {
                    Log.w("UserRepository", "API failed, trying cache")
                    val cachedUsers = userDao.searchUsers("%$query%")
                    Log.d("UserRepository", "Found ${cachedUsers.size} cached users")
                    Result.success(cachedUsers.map { it.toModel() })
                }
            } catch (e: Exception) {
                Log.e("UserRepository", "Search exception: ${e.message}", e)
                val cachedUsers = userDao.searchUsers("%$query%")
                Log.d("UserRepository", "Exception fallback: found ${cachedUsers.size} cached users")
                Result.success(cachedUsers.map { it.toModel() })
            }
        }
    }
    
    suspend fun updateUserStatus(userId: Int, status: UserStatus) {
        withContext(Dispatchers.IO) {
            userDao.updateUserStatus(userId, status.name, System.currentTimeMillis())
        }
    }
    
    suspend fun getCurrentUser(): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getCurrentUser()
                if (response.isSuccessful && response.body() != null) {
                    val user = response.body()!!
                    userDao.insertUser(user.toEntity())
                    Result.success(user)
                } else {
                    Result.failure(Exception("Failed to get current user"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?, email: String?, password: String?, readReceiptsEnabled: Boolean? = null, typingIndicatorsEnabled: Boolean? = null, voiceMessagesEnabled: Boolean? = null, showOnlineStatus: Boolean? = null): Result<User> {
        return withContext(Dispatchers.IO) {
            try {
                val request = UpdateProfileRequest(displayName, bio, avatarUrl, email, password, readReceiptsEnabled, typingIndicatorsEnabled, voiceMessagesEnabled, showOnlineStatus)
                val response = apiService.updateProfile(request)
                if (response.isSuccessful && response.body() != null) {
                    val updatedUser = response.body()!!
                    userDao.insertUser(updatedUser.toEntity())
                    Result.success(updatedUser)
                } else {
                    Result.failure(Exception("Failed to update profile"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun getUsersByIds(userIds: List<Int>): List<User> {
        return withContext(Dispatchers.IO) {
            userDao.getUsersByIds(userIds).map { it.toModel() }
        }
    }
    
    private fun User.toEntity() = UserEntity(
        id = id,
        username = username,
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl,
        status = status?.name ?: UserStatus.OFFLINE.name,
        bio = bio,
        readReceiptsEnabled = readReceiptsEnabled,
        voiceMessagesEnabled = voiceMessagesEnabled,
        showOnlineStatus = showOnlineStatus,
        onlineStatus = onlineStatus,
        publicKey = publicKey,
        createdAt = createdAt
    )
    
    private fun UserEntity.toModel() = User(
        id = id,
        username = username,
        email = email,
        displayName = displayName,
        avatarUrl = avatarUrl,
        status = UserStatus.valueOf(status),
        bio = bio,
        readReceiptsEnabled = readReceiptsEnabled,
        voiceMessagesEnabled = voiceMessagesEnabled,
        showOnlineStatus = showOnlineStatus,
        onlineStatus = onlineStatus,
        publicKey = publicKey,
        createdAt = createdAt
    )
}
