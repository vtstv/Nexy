/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list.selection

import androidx.compose.runtime.Stable
import com.nexy.client.ui.screens.chat.list.state.ChatWithInfo

@Stable
data class ChatSelectionState(
    val isSelectionMode: Boolean = false,
    val selectedChatIds: Set<Int> = emptySet()
) {
    val selectedCount: Int get() = selectedChatIds.size
    
    fun isSelected(chatId: Int): Boolean = selectedChatIds.contains(chatId)
    
    fun toggleSelection(chatId: Int): ChatSelectionState {
        val newSet = if (selectedChatIds.contains(chatId)) {
            selectedChatIds - chatId
        } else {
            selectedChatIds + chatId
        }
        return copy(
            selectedChatIds = newSet,
            isSelectionMode = newSet.isNotEmpty()
        )
    }
    
    fun selectChat(chatId: Int): ChatSelectionState {
        return copy(
            selectedChatIds = selectedChatIds + chatId,
            isSelectionMode = true
        )
    }
    
    fun deselectChat(chatId: Int): ChatSelectionState {
        val newSet = selectedChatIds - chatId
        return copy(
            selectedChatIds = newSet,
            isSelectionMode = newSet.isNotEmpty()
        )
    }
    
    fun clearSelection(): ChatSelectionState {
        return copy(
            selectedChatIds = emptySet(),
            isSelectionMode = false
        )
    }
    
    fun enterSelectionMode(chatId: Int): ChatSelectionState {
        return copy(
            isSelectionMode = true,
            selectedChatIds = setOf(chatId)
        )
    }
}

enum class ChatAction {
    PIN,
    ADD_TO_FOLDER,
    MARK_AS_READ,
    DELETE,
    MUTE,
    UNMUTE
}

enum class MuteDuration(val displayName: String, val apiValue: String?) {
    ONE_HOUR("1 hour", "1h"),
    EIGHT_HOURS("8 hours", "8h"),
    TWO_DAYS("2 days", "48h"),
    FOREVER("Forever", null)
}
