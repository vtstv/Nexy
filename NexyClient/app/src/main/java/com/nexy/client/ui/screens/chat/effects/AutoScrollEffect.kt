package com.nexy.client.ui.screens.chat.effects

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import kotlinx.coroutines.launch

@Composable
fun rememberAutoScrollState(
    listState: LazyListState,
    messageCount: Int
): AutoScrollState {
    val isUserScrolling = remember { mutableStateOf(false) }
    val isAtBottom = remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index == listState.layoutInfo.totalItemsCount - 1
        }
    }
    
    val coroutineScope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messageCount) {
        if (messageCount > 0) {
            val shouldAutoScroll = isAtBottom.value || listState.firstVisibleItemIndex == 0
            if (shouldAutoScroll && !isUserScrolling.value) {
                coroutineScope.launch {
                    listState.animateScrollToItem(messageCount - 1)
                }
            }
        }
    }
    
    // Reset user scrolling flag when they reach bottom
    LaunchedEffect(isAtBottom.value) {
        if (isAtBottom.value) {
            isUserScrolling.value = false
        }
    }
    
    // Detect manual scrolling
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAtBottom.value) {
            isUserScrolling.value = true
        }
    }
    
    return AutoScrollState(
        isUserScrolling = isUserScrolling,
        isAtBottom = isAtBottom
    )
}

data class AutoScrollState(
    val isUserScrolling: MutableState<Boolean>,
    val isAtBottom: State<Boolean>
)
