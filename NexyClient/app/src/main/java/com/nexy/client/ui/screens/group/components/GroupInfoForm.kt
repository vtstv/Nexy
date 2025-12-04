package com.nexy.client.ui.screens.group.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun GroupAvatarPicker(
    avatarUri: Uri?,
    onPickAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onPickAvatar)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri != null) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "Group Avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = "Add Photo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun GroupInfoFields(
    groupName: String,
    onNameChange: (String) -> Unit,
    groupDescription: String,
    onDescriptionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = groupName,
            onValueChange = onNameChange,
            label = { Text("Group Name") },
            placeholder = { Text("Enter group name") },
            leadingIcon = { Icon(Icons.Default.Group, null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = groupDescription,
            onValueChange = onDescriptionChange,
            label = { Text("Description (Optional)") },
            placeholder = { Text("Enter group description") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            minLines = 2,
            maxLines = 3
        )
    }
}

@Composable
fun PublicGroupSettings(
    isPublic: Boolean,
    onPublicChange: (Boolean) -> Unit,
    groupUsername: String,
    onUsernameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Public Group",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isPublic) "Anyone can find and join" else "Invite only",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isPublic,
                onCheckedChange = onPublicChange
            )
        }

        if (isPublic) {
            OutlinedTextField(
                value = groupUsername,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                placeholder = { Text("groupname") },
                prefix = { Text("@") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
