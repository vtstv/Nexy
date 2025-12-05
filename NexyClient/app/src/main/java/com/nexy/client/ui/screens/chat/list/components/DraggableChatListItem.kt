/*
 * © 2025 Murr | https://github.com/vtstv
 */
package com.nexy.client.ui.screens.chat.list.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nexy.client.ui.screens.chat.ChatWithInfo
import com.nexy.client.ui.screens.chat.list.ChatListItem

@Composable
fun DraggableChatListItem(
    chatWithInfo: ChatWithInfo,
    isDragging: Boolean,
    dragOffset: Offset,
    onClick: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    var itemPosition by remember { mutableStateOf(Offset.Zero) }
    
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.05f else 1f,
        label = "itemScale"
    )
    
    val elevation = if (isDragging) 8.dp else 0.dp
    val zIndex = if (isDragging) 10f else 0f
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isDragging) 
            MaterialTheme.colorScheme.surfaceContainerHigh 
        else 
            MaterialTheme.colorScheme.surface,
        label = "itemBg"
    )

    Box(
        modifier = Modifier
            .zIndex(zIndex)
            .graphicsLayer {
                if (isDragging) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = with(LocalDensity) { elevation.toPx() }
                }
            }
            .onGloballyPositioned { coords ->
                itemPosition = coords.positionInRoot()
            }
            .background(backgroundColor)
            .pointerInput(chatWithInfo.chat.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        onDragStart(itemPosition + offset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                )
            }
    ) {
        ChatListItem(
            chatWithInfo = chatWithInfo,
            onClick = onClick
        )
    }
}
