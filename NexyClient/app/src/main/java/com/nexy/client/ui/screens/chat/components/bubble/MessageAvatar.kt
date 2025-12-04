package com.nexy.client.ui.screens.chat.components.bubble

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nexy.client.ServerConfig
import com.nexy.client.data.models.User

@Composable
fun MessageAvatar(
    sender: User?,
    modifier: Modifier = Modifier
) {
    val avatarUrl = ServerConfig.getFileUrl(sender?.avatarUrl)
    
    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Sender avatar",
            modifier = modifier
                .padding(bottom = 8.dp)
                .size(32.dp)
                .clip(CircleShape)
        )
    } else {
        Surface(
            modifier = modifier
                .padding(bottom = 8.dp)
                .size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = getInitial(sender),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private fun getInitial(sender: User?): String {
    return (sender?.displayName?.firstOrNull()?.uppercaseChar()
        ?: sender?.username?.firstOrNull()?.uppercaseChar()
        ?: '?').toString()
}
