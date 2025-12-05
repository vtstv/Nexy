package com.nexy.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexy.client.ui.utils.OnlineStatusFormatter

@Composable
fun OnlineStatusIndicator(
    onlineStatus: String?,
    modifier: Modifier = Modifier,
    showDot: Boolean = true,
    showText: Boolean = true
) {
    val formattedStatus = OnlineStatusFormatter.formatOnlineStatus(onlineStatus)
    val isOnline = OnlineStatusFormatter.isOnline(onlineStatus)
    
    if (formattedStatus.isEmpty()) return
    
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isOnline) Color(0xFF4CAF50) else Color.Gray,
                        shape = CircleShape
                    )
            )
        }
        
        if (showText) {
            if (showDot) {
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = formattedStatus,
                style = MaterialTheme.typography.bodySmall,
                color = if (isOnline) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun OnlineStatusDot(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 10
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(
                color = if (isOnline) Color(0xFF4CAF50) else Color.Gray,
                shape = CircleShape
            )
    )
}
