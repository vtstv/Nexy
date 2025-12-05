/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list.components

import androidx.compose.ui.geometry.Offset
import com.nexy.client.data.models.ChatFolder as ApiFolderModel

sealed class FolderTab {
    object All : FolderTab()
    data class Custom(val folder: ApiFolderModel) : FolderTab()
}

data class DragState(
    val isDragging: Boolean = false,
    val draggedChatId: Int? = null,
    val draggedFolderIndex: Int = -1,
    val dragOffset: Offset = Offset.Zero,
    val startPosition: Offset = Offset.Zero
)
