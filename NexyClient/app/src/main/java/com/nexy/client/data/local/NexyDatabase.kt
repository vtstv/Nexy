package com.nexy.client.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.local.dao.PendingMessageDao
import com.nexy.client.data.local.dao.SearchHistoryDao
import com.nexy.client.data.local.dao.UserDao
import com.nexy.client.data.local.entity.ChatEntity
import com.nexy.client.data.local.entity.MessageEntity
import com.nexy.client.data.local.entity.PendingMessageEntity
import com.nexy.client.data.local.entity.SearchHistoryEntity
import com.nexy.client.data.local.entity.UserEntity

import androidx.room.TypeConverters

@Database(
    entities = [
        UserEntity::class,
        MessageEntity::class,
        ChatEntity::class,
        PendingMessageEntity::class,
        SearchHistoryEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NexyDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun pendingMessageDao(): PendingMessageDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    
    companion object {
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reactions TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `search_history` (
                        `query` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`query`)
                    )
                """)
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN onlineStatus TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN lastSeen TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN showOnlineStatus INTEGER NOT NULL DEFAULT 1")
            }
        }
        
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
        
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN pinnedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE chats ADD COLUMN isHidden INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN duration INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN voiceMessagesEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
