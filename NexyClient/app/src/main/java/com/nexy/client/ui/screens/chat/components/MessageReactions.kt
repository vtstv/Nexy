package com.nexy.client.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import com.nexy.client.data.models.ReactionCount
import com.nexy.client.ui.screens.chat.components.bubble.MessageAvatar
import coil.compose.AsyncImage
import com.nexy.client.data.models.User
import com.nexy.client.ServerConfig

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
    modifier: Modifier = Modifier,
    participants: Map<Int, User> = emptyMap()
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
                    userIds = reaction.userIds,
                    onClick = { onReactionClick(reaction.emoji) },
                    participants = participants
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
    userIds: List<Int>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    participants: Map<Int, User>
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

            val reactors = userIds.distinct().ifEmpty {
                // Fallback placeholders when backend does not send userIds
                List(count.coerceAtMost(3)) { -(it + 1) }
            }

            val totalReactors = if (userIds.isNotEmpty()) userIds.distinct().size else count

            if (totalReactors > 3) {
                Text(
                    text = totalReactors.toString(),
                    fontSize = 12.sp,
                    color = if (isReactedBy) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            } else if (reactors.isNotEmpty()) {
                AvatarStack(
                    userIds = reactors.take(3),
                    avatarSize = 20.dp,
                    overlapFraction = 0.2f,
                    participants = participants
                )
            }
        }
    }
}

@Composable
private fun AvatarStack(
    userIds: List<Int>,
    avatarSize: Dp,
    overlapFraction: Float,
    participants: Map<Int, User>
) {
    val overlap = avatarSize * overlapFraction
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-overlap))
    ) {
        userIds.forEachIndexed { index, id ->
            ReactionAvatar(
                userId = id,
                user = participants[id],
                size = avatarSize,
                z = index.toFloat()
            )
        }
    }
}

@Composable
private fun ReactionAvatar(userId: Int, user: User?, size: Dp, z: Float) {
    val color = remember(userId) { colorFromId(userId) }
    val avatarUrl = remember(user) { user?.avatarUrl?.let { ServerConfig.getFileUrl(it) } }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (avatarUrl != null) Color.Transparent else color)
            .zIndex(z),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(3.dp).fillMaxSize()
            )
        }
    }
}

private fun colorFromId(id: Int): Color {
    val hue = (kotlin.math.abs(id) % 360).toFloat()
    val saturation = 0.45f
    val value = 0.85f
    return Color.hsv(hue, saturation, value)
}

