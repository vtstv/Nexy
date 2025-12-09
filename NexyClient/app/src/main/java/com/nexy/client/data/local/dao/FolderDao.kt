package com.nexy.client.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nexy.client.data.local.entity.ChatFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM chat_folders ORDER BY position ASC, id ASC")
    fun observeFolders(): Flow<List<ChatFolderEntity>>

    @Query("SELECT * FROM chat_folders ORDER BY position ASC, id ASC")
    suspend fun getFoldersOnce(): List<ChatFolderEntity>

    @Query("SELECT * FROM chat_folders WHERE id = :folderId LIMIT 1")
    suspend fun getFolderById(folderId: Int): ChatFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: ChatFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolders(folders: List<ChatFolderEntity>)

    @Query("DELETE FROM chat_folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Int)

    @Query("DELETE FROM chat_folders")
    suspend fun clearFolders()

    @Query("UPDATE chat_folders SET position = :position WHERE id = :folderId")
    suspend fun updateFolderPosition(folderId: Int, position: Int)

    @Transaction
    suspend fun replaceAll(folders: List<ChatFolderEntity>) {
        clearFolders()
        if (folders.isNotEmpty()) {
            upsertFolders(folders)
        }
    }
}
