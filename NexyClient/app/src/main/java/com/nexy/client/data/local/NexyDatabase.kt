package com.nexy.client.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 4,
    exportSchema = false
)
abstract class NexyDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
}
