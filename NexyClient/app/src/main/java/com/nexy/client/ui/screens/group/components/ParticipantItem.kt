package com.nexy.client.ui.screens.group.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexy.client.ServerConfig
import com.nexy.client.data.models.ChatMember
import com.nexy.client.data.models.MemberRole

@Composable
fun ParticipantItem(
    member: ChatMember,
    currentUserId: Int?,
    currentUserRole: MemberRole?,
    canModerate: Boolean,
    onClick: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit
) {
    val user = member.user
    val displayName = if (!user?.displayName.isNullOrEmpty()) user?.displayName!! else user?.username ?: "User"
    val isOnline = user?.onlineStatus == "online"

    val roleBadge = when (member.role) {
        MemberRole.OWNER -> "Owner"
        MemberRole.ADMIN -> "Admin"
        else -> null
    }

    val canModerateThisMember = canModerate &&
            member.userId != currentUserId &&
            member.role != MemberRole.OWNER &&
            (currentUserRole == MemberRole.OWNER || member.role != MemberRole.ADMIN)

    var showMenu by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(displayName) },
        supportingContent = {
            user?.onlineStatus?.let { status ->
                if (status.isNotEmpty()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        leadingContent = {
            ParticipantAvatar(
                avatarUrl = user?.avatarUrl,
                displayName = displayName
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                roleBadge?.let { badge ->
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (canModerateThisMember) {
                    ParticipantOptionsMenu(
                        showMenu = showMenu,
                        onShowMenu = { showMenu = true },
                        onDismiss = { showMenu = false },
                        onKick = onKick,
                        onBan = onBan
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun ParticipantAvatar(
    avatarUrl: String?,
    displayName: String
) {
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
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = displayName.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ParticipantOptionsMenu(
    showMenu: Boolean,
    onShowMenu: () -> Unit,
    onDismiss: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit
) {
    Box {
        IconButton(onClick = onShowMenu) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismiss
        ) {
            DropdownMenuItem(
                text = { Text("Kick") },
                onClick = {
                    onDismiss()
                    onKick()
                },
                leadingIcon = { Icon(Icons.Default.PersonRemove, null) }
            )
            DropdownMenuItem(
                text = { Text("Ban") },
                onClick = {
                    onDismiss()
                    onBan()
                },
                leadingIcon = { Icon(Icons.Default.Block, null) },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error
                )
            )
        }
    }
}
