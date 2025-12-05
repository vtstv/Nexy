package com.nexy.client.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.local.dao.PendingMessageDao
import com.nexy.client.data.local.dao.UserDao
import com.nexy.client.data.local.entity.ChatEntity
import com.nexy.client.data.local.entity.MessageEntity
import com.nexy.client.data.local.entity.PendingMessageEntity
import com.nexy.client.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        MessageEntity::class,
        ChatEntity::class,
        PendingMessageEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class NexyDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun pendingMessageDao(): PendingMessageDao
    
    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add online status columns to users table
                db.execSQL("ALTER TABLE users ADD COLUMN onlineStatus TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN lastSeen TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN showOnlineStatus INTEGER NOT NULL DEFAULT 1")
            }
        }
        
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add lastReadMessageId and firstUnreadMessageId columns to chats table (unread tracking)
                db.execSQL("ALTER TABLE chats ADD COLUMN lastReadMessageId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN firstUnreadMessageId TEXT")
            }
        }
        
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_messages (
                        messageId TEXT PRIMARY KEY NOT NULL,
                        chatId INTEGER NOT NULL,
                        senderId INTEGER NOT NULL,
                        content TEXT NOT NULL,
                        messageType TEXT NOT NULL DEFAULT 'text',
                        recipientId INTEGER,
                        replyToId INTEGER,
                        encrypted INTEGER NOT NULL DEFAULT 0,
                        encryptionAlgorithm TEXT,
                        mediaUrl TEXT,
                        mediaType TEXT,
                        sendState TEXT NOT NULL DEFAULT 'QUEUED',
                        retryCount INTEGER NOT NULL DEFAULT 0,
                        maxRetries INTEGER NOT NULL DEFAULT 5,
                        createdAt INTEGER NOT NULL,
                        lastAttemptAt INTEGER,
                        errorMessage TEXT
                    )
                """)
            }
        }
    }
}
