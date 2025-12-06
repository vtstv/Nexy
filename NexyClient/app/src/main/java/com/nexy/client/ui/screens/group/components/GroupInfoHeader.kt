package com.nexy.client.ui.screens.group.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexy.client.ServerConfig
import com.nexy.client.data.models.Chat
import com.nexy.client.data.models.ChatMember

@Composable
fun GroupInfoHeader(
    chat: Chat?,
    members: List<ChatMember>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GroupAvatar(
            avatarUrl = chat?.avatarUrl,
            name = chat?.name
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = chat?.name ?: "Unknown Group",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (!chat?.description.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = chat?.description!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        MemberCountText(members = members)
    }
}

@Composable
private fun GroupAvatar(
    avatarUrl: String?,
    name: String?,
    modifier: Modifier = Modifier
) {
    if (avatarUrl != null) {
        AsyncImage(
            model = ServerConfig.getFileUrl(avatarUrl),
            contentDescription = "Group Avatar",
            modifier = modifier
                .size(100.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = modifier.size(100.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name?.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun MemberCountText(members: List<ChatMember>) {
    val onlineCount = members.count { it.user?.onlineStatus == "online" }
    val membersText = if (onlineCount > 0) {
        "${members.size} members, $onlineCount online"
    } else {
        "${members.size} members"
    }
    Text(
        text = membersText,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
