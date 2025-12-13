package com.nexy.client.ui.screens.chat.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Floating reaction panel that appears above a message
 */
@Composable
fun ReactionFloatingPanel(
    onDismiss: () -> Unit,
    onReactionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    contextMenuContent: @Composable () -> Unit = {}
) {
    val quickReactions = listOf("❤️", "👍", "😂", "😮", "😢", "🙏", "👏")
    val moreReactions = listOf(
        "🔥", "🎉", "😍", "😊", "😎", "🤔", "😕", "😡", "💯", "✨", "🚀", "🥳", "🤯", "🤝", "💔", "🤩", "😴", "🥶", "🤒", "🤗",
        "👌", "🙌", "🤷", "😉", "😇", "😈", "🤓", "😬", "😋", "🤤", "😭", "😅", "🤪", "🤡", "👀", "🤢", "🤮", "🤧", "😱", "🤬",
        "😤", "😳", "🤠", "🫡", "🫶", "🤝", "🤘", "✌️", "👊", "👏", "👐"
    )
    
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            var expanded by remember { mutableStateOf(false) }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Surface(
                    modifier = modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .clickable(enabled = false) { }, // Prevent dismiss when clicking on panel
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Top row with arrow - use LazyRow for scrolling on smaller screens
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Reactions row - takes available space minus arrow button
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                quickReactions.forEach { emoji ->
                                    ReactionCircle(emoji = emoji, size = 42.dp, fontSize = 24.sp) {
                                        onReactionSelected(emoji)
                                        onDismiss()
                                    }
                                }
                            }
                            
                            // Arrow button with fixed size - always visible
                            Surface(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clickable { expanded = !expanded },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "More reactions",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Expandable scrollable row
                        AnimatedVisibility(visible = expanded) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(moreReactions) { emoji ->
                                    ReactionCircle(emoji = emoji, size = 44.dp, fontSize = 24.sp) {
                                        onReactionSelected(emoji)
                                        onDismiss()
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !expanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.7f) // Make context menu slightly narrower like in screenshot
                                .scale(scale)
                                .clickable(enabled = false) { },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            shadowElevation = 8.dp
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                contextMenuContent()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReactionCircle(
    emoji: String,
    size: Dp = 48.dp,
    fontSize: TextUnit = 28.sp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable { onClick() }
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = fontSize,
            textAlign = TextAlign.Center
        )
    }
}
