package com.nexy.client.ui.screens.chat.components.bubble

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexy.client.data.models.MessageStatus
import com.nexy.client.ui.screens.chat.utils.formatTimestamp

@Composable
fun MessageTimestamp(
    timestamp: String?,
    isEdited: Boolean,
    isOwnMessage: Boolean,
    status: MessageStatus?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isEdited) {
            Text(
                text = "edited",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Text(
            text = formatTimestamp(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        if (isOwnMessage) {
            Spacer(modifier = Modifier.width(4.dp))
            val messageStatus = status ?: MessageStatus.SENT
            
            when (messageStatus) {
                MessageStatus.SENDING -> {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Sending",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                MessageStatus.FAILED -> {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = "Failed",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                MessageStatus.READ, MessageStatus.DELIVERED -> {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = messageStatus.name,
                        modifier = Modifier.size(16.dp),
                        tint = if (messageStatus == MessageStatus.READ)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = messageStatus.name,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
