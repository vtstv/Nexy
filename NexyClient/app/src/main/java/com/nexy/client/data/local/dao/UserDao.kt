package com.nexy.client.data.local.dao

import androidx.room.*
import com.nexy.client.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Int): UserEntity?
    
    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserByIdFlow(userId: Int): Flow<UserEntity?>
    
    @Query("SELECT * FROM users WHERE username LIKE :query")
    suspend fun searchUsers(query: String): List<UserEntity>
    
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)
    
    @Update
    suspend fun updateUser(user: UserEntity)
    
    @Query("UPDATE users SET status = :status, lastSeen = :lastSeen WHERE id = :userId")
    suspend fun updateUserStatus(userId: Int, status: String, lastSeen: Long)
    
    @Delete
    suspend fun deleteUser(user: UserEntity)
    
    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: Int)
    
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}
