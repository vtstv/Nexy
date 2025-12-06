package com.nexy.client.ui.screens.group.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexy.client.ServerConfig
import com.nexy.client.data.api.GroupBan

@Composable
fun BannedMemberItem(
    ban: GroupBan,
    onUnban: () -> Unit
) {
    val user = ban.user
    val displayName = if (!user?.displayName.isNullOrEmpty()) {
        user?.displayName!!
    } else {
        user?.username ?: "User #${ban.userId}"
    }
    val bannedByName = ban.bannedByUser?.let {
        if (!it.displayName.isNullOrEmpty()) it.displayName else it.username
    } ?: "Admin"

    ListItem(
        headlineContent = { Text(displayName) },
        supportingContent = {
            Column {
                Text(
                    text = "Banned by $bannedByName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!ban.reason.isNullOrEmpty()) {
                    Text(
                        text = "Reason: ${ban.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        leadingContent = {
            BannedMemberAvatar(
                avatarUrl = user?.avatarUrl
            )
        },
        trailingContent = {
            TextButton(onClick = onUnban) {
                Text("Unban", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun BannedMemberAvatar(avatarUrl: String?) {
    val url = ServerConfig.getFileUrl(avatarUrl)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
