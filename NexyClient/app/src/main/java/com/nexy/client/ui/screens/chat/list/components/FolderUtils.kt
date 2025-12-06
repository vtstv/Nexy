/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexy.client.data.models.ChatFolder as ApiFolderModel
import com.nexy.client.data.models.ChatType
import com.nexy.client.ui.screens.chat.list.state.ChatWithInfo

val FolderColors = listOf(
    Color(0xFF3390EC), // Blue (default)
    Color(0xFFEB7B39), // Orange
    Color(0xFFDD4B4E), // Red
    Color(0xFF9D5BD0), // Purple
    Color(0xFF00A884), // Green
    Color(0xFFDB437E), // Pink
    Color(0xFF3FAAE2), // Cyan
    Color(0xFFCCA336)  // Yellow
)

fun getFolderColor(folder: ApiFolderModel, index: Int): Color {
    return if (folder.color.isNotBlank()) {
        try {
            Color(android.graphics.Color.parseColor(folder.color))
        } catch (e: Exception) {
            FolderColors[index % FolderColors.size]
        }
    } else {
        FolderColors[index % FolderColors.size]
    }
}

fun getFolderIcon(folder: ApiFolderModel): ImageVector {
    return when {
        folder.icon.isNotBlank() -> {
            when (folder.icon.lowercase()) {
                "person", "user", "contact" -> Icons.Default.Person
                "people", "group", "groups" -> Icons.Default.Groups
                "chat", "message" -> Icons.Default.Chat
                "work", "business" -> Icons.Default.Work
                "star", "favorite" -> Icons.Default.Star
                "bookmark" -> Icons.Default.Bookmark
                "label" -> Icons.Default.Label
                "robot", "bot" -> Icons.Default.SmartToy
                "channel" -> Icons.Default.Campaign
                "unread" -> Icons.Default.MarkUnreadChatAlt
                else -> Icons.Default.Folder
            }
        }
        folder.includeGroups && !folder.includeContacts -> Icons.Default.Groups
        folder.includeContacts && !folder.includeGroups -> Icons.Default.Person
        folder.includeBots -> Icons.Default.SmartToy
        folder.includeChannels -> Icons.Default.Campaign
        else -> Icons.Default.Folder
    }
}

fun parseTimestampForSort(timestamp: String?): Long {
    if (timestamp == null) return 0L
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        val withoutMillis = timestamp.substringBefore('.')
        sdf.parse(withoutMillis)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }
}

fun countChatsInFolder(chats: List<ChatWithInfo>, folder: ApiFolderModel): Int {
    return chats.count { chatWithInfo ->
        val chatId = chatWithInfo.chat.id
        val chatType = chatWithInfo.chat.type
        
        if (folder.excludedChatIds?.contains(chatId) == true) {
            false
        } else if (folder.includedChatIds?.contains(chatId) == true) {
            true
        } else {
            val isGroup = chatType == ChatType.GROUP
            val isPrivate = chatType == ChatType.PRIVATE
            
            (folder.includeGroups && isGroup) ||
            (folder.includeContacts && isPrivate) ||
            (folder.includeNonContacts && isPrivate)
        }
    }
}

fun chatMatchesFolder(chatWithInfo: ChatWithInfo, folder: ApiFolderModel): Boolean {
    val chatId = chatWithInfo.chat.id
    val chatType = chatWithInfo.chat.type
    
    if (folder.excludedChatIds?.contains(chatId) == true) {
        return false
    }
    if (folder.includedChatIds?.contains(chatId) == true) {
        return true
    }
    
    val isGroup = chatType == ChatType.GROUP
    val isPrivate = chatType == ChatType.PRIVATE
    
    return (folder.includeGroups && isGroup) ||
           (folder.includeContacts && isPrivate) ||
           (folder.includeNonContacts && isPrivate)
}
