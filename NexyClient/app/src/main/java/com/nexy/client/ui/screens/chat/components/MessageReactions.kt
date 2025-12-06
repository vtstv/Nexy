package com.nexy.client.ui.screens.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexy.client.data.models.ReactionCount

val COMMON_EMOJIS = listOf(
    "❤️", "👍", "😂", "😮", "😢", "🙏",
    "👏", "🔥", "🎉", "😍", "😊", "😎",
    "🤔", "😕", "😡", "💯", "✨", "🚀"
)

@Composable
fun MessageReactions(
    reactions: List<ReactionCount>,
    currentUserId: Int,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (reactions.isNotEmpty()) {
        LazyRow(
            modifier = modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(reactions) { reaction ->
                ReactionChip(
                    emoji = reaction.emoji,
                    count = reaction.count,
                    isReactedBy = reaction.reactedBy,
                    onClick = { onReactionClick(reaction.emoji) }
                )
            }
        }
    }
}

@Composable
fun ReactionChip(
    emoji: String,
    count: Int,
    isReactedBy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(28.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isReactedBy) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (isReactedBy) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        } else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = emoji,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = count.toString(),
                fontSize = 12.sp,
                color = if (isReactedBy) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

