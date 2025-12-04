package com.nexy.client.ui.screens.group.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexy.client.data.models.Chat

@Composable
fun GroupInfoCard(
    chat: Chat,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = chat.name ?: "Group",
                style = MaterialTheme.typography.headlineSmall
            )
            if (!chat.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = chat.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (chat.username != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "@${chat.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MembersHeader(
    memberCount: Int,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Members ($memberCount)",
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
    )
}

@Composable
fun CreateInviteLinkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Link, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Create Invite Link")
    }
}
