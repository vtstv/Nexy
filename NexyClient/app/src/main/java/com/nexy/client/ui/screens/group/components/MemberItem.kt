package com.nexy.client.ui.screens.group.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexy.client.ServerConfig
import com.nexy.client.data.models.ChatMember
import com.nexy.client.data.models.MemberRole
import com.nexy.client.ui.components.OnlineStatusIndicator

@Composable
fun MemberItem(
    member: ChatMember,
    canManage: Boolean,
    isOwner: Boolean,
    currentUserIsOwner: Boolean,
    onRemove: () -> Unit,
    onUpdateRole: (String) -> Unit,
    onTransferOwnership: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }

    if (showTransferDialog) {
        TransferOwnershipDialog(
            memberName = member.user?.displayName ?: member.user?.username ?: "this user",
            onConfirm = {
                onTransferOwnership()
                showTransferDialog = false
            },
            onDismiss = { showTransferDialog = false }
        )
    }

    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                MemberAvatar(member = member)
                Spacer(modifier = Modifier.width(12.dp))
                MemberInfo(member = member)
            }

            if (canManage && !isOwner) {
                MemberOptionsMenu(
                    showMenu = showMenu,
                    onShowMenu = { showMenu = true },
                    onDismiss = { showMenu = false },
                    member = member,
                    currentUserIsOwner = currentUserIsOwner,
                    onUpdateRole = onUpdateRole,
                    onRemove = onRemove,
                    onShowTransferDialog = { showTransferDialog = true }
                )
            }
        }
    }
}

@Composable
private fun MemberAvatar(member: ChatMember) {
    val avatarUrl = ServerConfig.getFileUrl(member.user?.avatarUrl)
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Avatar",
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
                    text = getInitial(member),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private fun getInitial(member: ChatMember): String {
    return (member.user?.displayName?.firstOrNull()?.uppercaseChar()
        ?: member.user?.username?.firstOrNull()?.uppercaseChar()
        ?: '?').toString()
}

@Composable
private fun MemberInfo(member: ChatMember) {
    Column {
        Text(
            text = member.user?.displayName ?: member.user?.username ?: "User ${member.userId}",
            style = MaterialTheme.typography.bodyLarge
        )
        // Show online status if available, otherwise show role
        val onlineStatus = member.user?.onlineStatus
        if (!onlineStatus.isNullOrEmpty()) {
            OnlineStatusIndicator(
                onlineStatus = onlineStatus,
                showDot = true,
                showText = true
            )
        } else {
            Text(
                text = member.role.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MemberOptionsMenu(
    showMenu: Boolean,
    onShowMenu: () -> Unit,
    onDismiss: () -> Unit,
    member: ChatMember,
    currentUserIsOwner: Boolean,
    onUpdateRole: (String) -> Unit,
    onRemove: () -> Unit,
    onShowTransferDialog: () -> Unit
) {
    IconButton(onClick = onShowMenu) {
        Icon(Icons.Default.MoreVert, "Options")
    }

    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = onDismiss
    ) {
        if (member.role != MemberRole.ADMIN) {
            DropdownMenuItem(
                text = { Text("Make Admin") },
                onClick = {
                    onUpdateRole("admin")
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) }
            )
        } else {
            DropdownMenuItem(
                text = { Text("Remove Admin") },
                onClick = {
                    onUpdateRole("member")
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) }
            )
        }

        if (currentUserIsOwner) {
            DropdownMenuItem(
                text = { Text("Transfer Ownership") },
                onClick = {
                    onShowTransferDialog()
                    onDismiss()
                },
                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) }
            )
        }

        DropdownMenuItem(
            text = { Text("Remove from Group") },
            onClick = {
                onRemove()
                onDismiss()
            },
            leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null) }
        )
    }
}

@Composable
private fun TransferOwnershipDialog(
    memberName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer Ownership") },
        text = { 
            Text("Are you sure you want to transfer ownership to $memberName? You will become an admin.") 
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Transfer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
