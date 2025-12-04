package com.nexy.client.ui.screens.group.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexy.client.data.models.ContactWithUser

@Composable
fun ContactSelectionItem(
    contact: ContactWithUser,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(contact.contactUser.displayName ?: contact.contactUser.username) 
        },
        supportingContent = { 
            Text("@${contact.contactUser.username}") 
        },
        leadingContent = {
            ContactAvatar(
                avatarUrl = contact.contactUser.avatarUrl,
                displayName = contact.contactUser.displayName ?: contact.contactUser.username
            )
        },
        trailingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
        },
        modifier = Modifier.clickable { onToggle() }
    )
}

@Composable
private fun ContactAvatar(
    avatarUrl: String?,
    displayName: String
) {
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayName.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
