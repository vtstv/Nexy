package com.nexy.client.ui.screens.group.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nexy.client.data.api.GroupBan
import com.nexy.client.data.models.GroupType
import com.nexy.client.data.models.MemberRole

@Composable
fun GroupInfoActions(
    isMember: Boolean,
    groupType: GroupType?,
    currentUserRole: MemberRole?,
    showBannedMembers: Boolean,
    bannedMembersCount: Int,
    onAddParticipant: () -> Unit,
    onToggleBannedMembers: () -> Unit,
    onJoinGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (isMember) {
            AddParticipantsAction(onClick = onAddParticipant)
            HorizontalDivider()

            if (currentUserRole == MemberRole.OWNER || currentUserRole == MemberRole.ADMIN) {
                BannedMembersAction(
                    showBannedMembers = showBannedMembers,
                    bannedMembersCount = bannedMembersCount,
                    onClick = onToggleBannedMembers
                )
                HorizontalDivider()
            }
        } else if (groupType == GroupType.PUBLIC_GROUP) {
            JoinGroupAction(onClick = onJoinGroup)
            HorizontalDivider()
        }
    }
}

@Composable
private fun AddParticipantsAction(onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text("Add Participants") },
        leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun BannedMembersAction(
    showBannedMembers: Boolean,
    bannedMembersCount: Int,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(if (showBannedMembers) "Show Participants" else "Banned Members")
        },
        leadingContent = { Icon(Icons.Default.Block, contentDescription = null) },
        trailingContent = {
            if (bannedMembersCount > 0) {
                Badge { Text(bannedMembersCount.toString()) }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun JoinGroupAction(onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text("Join Group", color = MaterialTheme.colorScheme.primary)
        },
        leadingContent = {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
