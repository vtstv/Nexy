package com.nexy.client.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nexy.client.data.local.dao.ChatDao
import com.nexy.client.data.local.dao.MessageDao
import com.nexy.client.data.local.dao.UserDao
import com.nexy.client.data.local.entity.ChatEntity
import com.nexy.client.data.local.entity.MessageEntity
import com.nexy.client.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        MessageEntity::class,
        ChatEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class NexyDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    
    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add online status columns to users table
                db.execSQL("ALTER TABLE users ADD COLUMN onlineStatus TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN lastSeen TEXT")
                db.execSQL("ALTER TABLE users ADD COLUMN showOnlineStatus INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
